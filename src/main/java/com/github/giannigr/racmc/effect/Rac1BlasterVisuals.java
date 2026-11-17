package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import java.util.Random;
import java.util.UUID;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Render-only class305 adapter. The existing arrow supplies position/direction/lifecycle.
 * No original speed, aiming, collision, damage, homing or water rules are installed.
 * Muzzle/socket and periodic camera-relative side sparks remain separate work. */
public final class Rac1BlasterVisuals implements AutoCloseable {
    private final EffectRuntime runtime;
    private final String sha1;
    private final UUID owner;
    private final Random random;
    private final long[] handles = new long[18];
    private boolean closed;

    public Rac1BlasterVisuals(EffectRuntime runtime, String sha1, UUID owner, Point anchor, long seed) {
        this.runtime = runtime;
        this.sha1 = sha1;
        this.owner = owner;
        random = new Random(seed);
        // All-or-none allocation: never replace an original projectile with a partial empty trail.
        if (runtime.remaining() < handles.length) {
            closed = true;
            return;
        }
        handles[0] = sprite(anchor, 32, 200000, 0x2f4f7f7f, 0, 0);
        for (int slot = 0; slot < 17; slot++) {
            float t = slot / 16.0f;
            float curve = .5f * t * t * t - 2 * t * t + 2.5f * t;
            float start = 30000 + random.nextFloat() * 20000;
            float size = start + (16000 - start) * curve;
            int alpha = (int) (128 - 64 * curve);
            handles[slot + 1] = sprite(anchor, 28, size, alpha << 24 | 0xffffff,
                    (28 + random.nextInt(8)) * (float) (Math.PI * 2 / 256),
                    (random.nextInt(12) - 6) * (float) (Math.PI * 2 / 256));
        }
    }

    private long sprite(Point anchor, int texture, float size, int colour, float rotation, float spin) {
        return runtime.spawn(new Spawn(owner, ref(texture), Geometry.BILLBOARD, Program.OWNER_SPRITE,
                anchor, new Point(0, 0, 0), size, 1, new Point(0, 0, rotation), spin, colour,
                600, 0, EffectMaterial.ADDITIVE));
    }
    private EffectAssetRef ref(int id) { return new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.PARTICLE, id); }
    public boolean active() { return !closed && runtime.contains(handles[0]); }

    /** Once per original fixed update. Direction is the existing arrow tangent in MC axes.
     * This never extrapolates or mutates the projectile's actual position. */
    public void update(Point anchor, Point direction, int originalAge) {
        if (closed) return;
        if (!active()) { close(); return; }
        if (originalAge < 0 || originalAge > 600) throw new IllegalArgumentException("Invalid projectile age");
        double length = Math.sqrt(direction.distanceSquared(new Point(0, 0, 0)));
        if (length < 1.0e-8) return;
        runtime.anchor(handles[0], anchor);
        runtime.anchor(handles[1], anchor); // Trail slot zero is at the projectile without jitter.
        Point position = anchor;
        double step = .15 * Math.min(1, originalAge / (double) runtime.profile().ticks(13));
        for (int slot = 1; slot < 17; slot++) {
            position = position.add(-direction.x() / length * step, -direction.y() / length * step,
                    -direction.z() / length * step);
            runtime.anchor(handles[slot + 1], position.add(jitter(.075), jitter(.075), jitter(.075)));
            step *= .85;
        }
    }

    /** Accepted server impact only. Caller deduplicates impact/end and never infers impact
     * from untracking. Reflected tangent is only used for cosmetic sparks. */
    public void impact(Point point, Point incoming, Point normal) {
        if (closed) return;
        if (!active()) { close(); return; }
        double length = Math.sqrt(normal.distanceSquared(new Point(0, 0, 0)));
        if (length < 1.0e-8) throw new IllegalArgumentException("Missing impact normal");
        double nx = normal.x() / length, ny = normal.y() / length, nz = normal.z() / length;
        double dot = incoming.x() * nx + incoming.y() * ny + incoming.z() * nz;
        Point reflected = new Point(incoming.x() - 2 * dot * nx, incoming.y() - 2 * dot * ny,
                incoming.z() - 2 * dot * nz);
        double reflectedLength = Math.sqrt(reflected.distanceSquared(new Point(0, 0, 0)));
        close();
        for (int spark = 0; spark < 5; spark++) {
            Point noise = new Point(jitter(1), jitter(1), jitter(1));
            double noiseLength = Math.sqrt(noise.distanceSquared(new Point(0, 0, 0)));
            double noiseScale = noiseLength < 1.0e-8 ? 0 : .5 / noiseLength;
            double reflectScale = reflectedLength < 1.0e-8 ? 0 : 1 / reflectedLength;
            Point direction = new Point(reflected.x() * reflectScale + noise.x() * noiseScale,
                    reflected.y() * reflectScale + noise.y() * noiseScale,
                    reflected.z() * reflectScale + noise.z() * noiseScale);
            double magnitude = Math.sqrt(direction.distanceSquared(new Point(0, 0, 0)));
            double speed = (3 + random.nextDouble() * 3) / runtime.profile().hz();
            Point velocity = magnitude < 1.0e-8 ? new Point(0, speed, 0)
                    : new Point(direction.x() / magnitude * speed, direction.y() / magnitude * speed,
                    direction.z() / magnitude * speed);
            runtime.spawn(new Spawn(owner, ref(32), Geometry.BILLBOARD, Program.SPARK27, point,
                    velocity, 30000, 1, new Point(0, 0, random.nextDouble() * Math.PI * 2), 0,
                    0x7f2f4f6f, runtime.profile().ticks(10 + random.nextInt(6)), 0, EffectMaterial.ADDITIVE));
        }
        smoke(point);
    }

    public void end(Point point) {
        if (closed) return;
        if (!active()) { close(); return; }
        close();
        smoke(point);
    }

    private void smoke(Point point) {
        for (int puff = 0; puff < 3; puff++) {
            int alpha = 32 + random.nextInt(97);
            runtime.spawn(new Spawn(owner, ref(0), Geometry.BILLBOARD, Program.SMOKE23_END,
                    point.add(jitter(.2), jitter(.2), jitter(.2)),
                    new Point(0, .01 + random.nextDouble() * .015, 0), 140000,
                    1 + random.nextFloat() * .02f, new Point(0, 0, 0),
                    (random.nextBoolean() ? 1 : -1) * (float) (Math.PI * 2 / 256),
                    alpha << 24 | 0x606060, 50 + random.nextInt(31), 0,
                    random.nextBoolean() ? EffectMaterial.NORMAL : EffectMaterial.ADDITIVE));
        }
    }
    private double jitter(double limit) { return (random.nextDouble() * 2 - 1) * limit; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        for (long handle : handles) runtime.stop(handle);
    }
}
