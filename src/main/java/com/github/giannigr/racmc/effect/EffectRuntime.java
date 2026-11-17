package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import java.util.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Pure bounded fixed-update visual pool. No world/entity references or gameplay callbacks.
 * Caller owns authoritative anchors and calls clear on world/catalog replacement. */
public final class EffectRuntime {
    public enum Program { DUST13, SPARK27, SMOKE23_END, FLASH112, OWNER_SPRITE, TRAIL62, OWNER_MESH }
    public enum Geometry { BILLBOARD, MESH }
    public record Spawn(UUID owner, EffectAssetRef asset, Geometry geometry, Program program,
                        Point position, Point velocity, float size, float growth, Point rotation,
                        float spin, int originalColour, int lifetime, int fadeIn,
                        EffectMaterial material) {
        public Spawn {
            if (owner == null || asset == null || geometry == null || program == null || position == null
                    || velocity == null || material == null || !Float.isFinite(size) || size <= 0
                    || size > 4_000_000 || !Float.isFinite(growth) || growth < 0.9f || growth > 1.1f
                    || rotation == null || !Float.isFinite(spin) || Math.abs(spin) > 10
                    || lifetime < 1 || lifetime > 600 || fadeIn < 0
                    || (program == Program.TRAIL62 ? fadeIn < 1 || fadeIn > 127 : fadeIn >= lifetime)
                    || (geometry == Geometry.MESH) != (asset.bank() == EffectAssetRef.Bank.MOBY)
                    || Math.abs(velocity.x()) > 16 || Math.abs(velocity.y()) > 16
                    || Math.abs(velocity.z()) > 16)
                throw new IllegalArgumentException("Invalid effect spawn");
        }
    }
    public record Draw(long handle, EffectAssetRef asset, Geometry geometry, Point position,
                       float size, Point rotation, int rgba, EffectMaterial material, double near, double far) {
        public Draw(long handle, EffectAssetRef asset, Geometry geometry, Point position,
                    float size, Point rotation, int rgba, EffectMaterial material) {
            this(handle, asset, geometry, position, size, rotation, rgba, material, 1, 128);
        }
    }
    private static final class Active {
        final long handle;
        final Spawn spec;
        Point previous, position;
        float previousSize, size;
        Point previousRotation, rotation;
        int age, alpha;
        int colour;
        double near = 1, far = 128;
        Active(long handle, Spawn spec) {
            this.handle = handle;
            this.spec = spec;
            position = previous = spec.position();
            size = previousSize = spec.program() == Program.FLASH112
                    ? spec.size() * spec.fadeIn() / spec.lifetime() : spec.size();
            rotation = previousRotation = spec.rotation();
            alpha = spec.originalColour() >>> 24;
            colour = spec.originalColour();
        }
    }
    private final int capacity;
    private final EffectClock clock;
    private final Map<Long, Active> active = new LinkedHashMap<>();
    private long nextHandle = 1;

    public EffectRuntime(EffectClock.Profile profile, int capacity) {
        if (capacity < 1 || capacity > 4096) throw new IllegalArgumentException("Invalid effect capacity");
        this.capacity = capacity;
        clock = new EffectClock(profile);
    }
    public EffectClock.Profile profile() { return clock.profile(); }
    public int size() { return active.size(); }
    public int remaining() { return capacity - active.size(); }
    public boolean contains(long handle) { return active.containsKey(handle); }
    public long spawn(Spawn spec) {
        Objects.requireNonNull(spec);
        if (active.size() == capacity) return 0;
        long handle = nextHandle++;
        active.put(handle, new Active(handle, spec));
        return handle;
    }
    public void stop(long handle) { active.remove(handle); }
    public void bounds(long handle, double near, double far) {
        if (!Double.isFinite(near) || !Double.isFinite(far) || near < 0 || far <= near || far > 500)
            throw new IllegalArgumentException("Invalid effect distance bounds");
        Active value = active.get(handle);
        if (value != null) { value.near = near; value.far = far; }
    }
    public void stopOwner(UUID owner) { active.values().removeIf(value -> value.spec.owner().equals(owner)); }
    public void clear() { active.clear(); clock.reset(); }
    /** Typed procedural controllers submit draw state only, not game/entity physics. */
    public boolean pose(long handle, Point point, float size, Point rotation, int originalColour) {
        Active value = active.get(handle);
        if (value == null || (value.spec.program() != Program.OWNER_SPRITE
                && value.spec.program() != Program.OWNER_MESH)) return false;
        if (!Float.isFinite(size) || size < 0 || size > 4_000_000) { stop(handle); return false; }
        value.previous = value.position;
        value.position = Objects.requireNonNull(point);
        value.previousSize = value.size; value.size = size;
        value.previousRotation = value.rotation; value.rotation = Objects.requireNonNull(rotation);
        value.colour = originalColour;
        value.alpha = originalColour >>> 24;
        return true;
    }

    /** Owner-controlled sprites have no autonomous motion. A stale/missing handle is harmless. */
    public boolean anchor(long handle, Point point) {
        Active value = active.get(handle);
        if (value == null || value.spec.program() != Program.OWNER_SPRITE) return false;
        value.previous = value.position;
        value.position = Objects.requireNonNull(point);
        return true;
    }

    public EffectClock.Advance advance(double seconds, boolean paused) {
        return advance(seconds, paused, () -> {});
    }
    public EffectClock.Advance advance(double seconds, boolean paused, Runnable fixedUpdate) {
        EffectClock.Advance advance = clock.advance(seconds, paused);
        if (advance.discontinuity()) active.clear();
        for (int step = 0; step < advance.steps(); step++) {
            update();
            fixedUpdate.run();
        }
        return advance;
    }

    private void update() {
        Iterator<Active> iterator = active.values().iterator();
        while (iterator.hasNext()) {
            Active value = iterator.next();
            Spawn spec = value.spec;
            int lifetime = spec.lifetime() - (spec.program() == Program.FLASH112 ? spec.fadeIn() : 0);
            if (++value.age >= lifetime) {
                iterator.remove();
                continue;
            }
            value.previous = value.position;
            value.previousSize = value.size;
            value.previousRotation = value.rotation;
            if (spec.program() != Program.OWNER_SPRITE && spec.program() != Program.OWNER_MESH) {
                Point velocity = spec.velocity();
                try {
                    value.position = value.position.add(velocity.x(), velocity.y(), velocity.z());
                } catch (IllegalArgumentException outsideWorld) {
                    iterator.remove();
                    continue;
                }
            }
            value.rotation = spec.program() == Program.FLASH112
                    ? value.rotation.add(spec.spin(), spec.spin(), spec.spin())
                    : value.rotation.add(0, 0, spec.spin());
            int startAlpha = spec.originalColour() >>> 24;
            switch (spec.program()) {
                case DUST13 -> {
                    value.size *= spec.growth();
                    value.alpha = value.age < spec.fadeIn()
                            ? 32 + (int) (value.age * (96.0f / spec.fadeIn()))
                            : (int) ((spec.lifetime() - value.age) * (127.0f / (spec.lifetime() - spec.fadeIn())));
                }
                case SMOKE23_END -> {
                    value.size *= spec.growth();
                    value.alpha = startAlpha * (spec.lifetime() - value.age) / spec.lifetime();
                }
                case SPARK27 -> value.alpha = startAlpha * (spec.lifetime() - value.age + 1) / spec.lifetime();
                case FLASH112 -> {
                    value.size = spec.size() * (value.age + spec.fadeIn()) / spec.lifetime();
                    value.alpha = Math.min(startAlpha, startAlpha * 2
                            * (spec.lifetime() - spec.fadeIn() - value.age) / spec.lifetime());
                }
                case OWNER_SPRITE, OWNER_MESH -> { }
                case TRAIL62 -> value.alpha = startAlpha - value.age * spec.fadeIn();
            }
            if (!Float.isFinite(value.size) || value.size > 4_000_000) iterator.remove();
        }
    }

    /** Stable back-to-front normal blend pass, followed by stable additive pass.
     * Size remains source projection units for sprites; backend calibration is explicit. */
    public List<Draw> snapshot(Point camera, double interpolation) {
        if (!Double.isFinite(interpolation) || interpolation < 0 || interpolation > 1)
            throw new IllegalArgumentException("Invalid effect interpolation");
        List<Draw> draws = new ArrayList<>(active.size());
        for (Active value : active.values()) {
            Spawn spec = value.spec;
            double t = interpolation;
            Point point = new Point(value.previous.x() + (value.position.x() - value.previous.x()) * t,
                    value.previous.y() + (value.position.y() - value.previous.y()) * t,
                    value.previous.z() + (value.position.z() - value.previous.z()) * t);
            int colour = (value.colour & 0xffffff) | Math.min(128, Math.max(0, value.alpha)) << 24;
            draws.add(new Draw(value.handle, spec.asset(), spec.geometry(), point,
                    (float) (value.previousSize + (value.size - value.previousSize) * t),
                    new Point(value.previousRotation.x() + (value.rotation.x() - value.previousRotation.x()) * t,
                            value.previousRotation.y() + (value.rotation.y() - value.previousRotation.y()) * t,
                            value.previousRotation.z() + (value.rotation.z() - value.previousRotation.z()) * t),
                    EffectMaterial.rgba(colour), spec.material(), value.near, value.far));
        }
        draws.sort(Comparator.comparing((Draw draw) -> draw.material().blend())
                .thenComparing(Comparator.comparingDouble((Draw draw) ->
                        draw.material().blend() == EffectMaterial.Blend.NORMAL
                                ? draw.position().distanceSquared(camera) : 0).reversed()));
        return List.copyOf(draws);
    }
}
