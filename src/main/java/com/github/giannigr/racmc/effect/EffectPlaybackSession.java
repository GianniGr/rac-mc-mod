package com.github.giannigr.racmc.effect;

import java.util.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Pure client presentation lifecycle. Entity spawn is authoritative shot admission too;
 * packet+spawn deduplicate by UUID. Terminal-before-spawn never resurrects a trail.
 * Sources are value snapshots, never retained Minecraft entity references. */
public final class EffectPlaybackSession {
    public record Source(UUID lifecycle, Point position, Point tangent, int originalAge) {}
    private final String sha1;
    private final EffectRuntime runtime;
    private final EffectEventGate gate = new EffectEventGate();
    private final Map<UUID, Rac1BlasterVisuals> shots = new HashMap<>();
    private final Map<UUID, Long> terminal = new HashMap<>();
    private final Map<UUID, Long> births = new HashMap<>();
    private long generation;
    private Rac1ProceduralEffects procedural;
    private long lastBlast = Long.MIN_VALUE / 2;
    public EffectPlaybackSession(String sha1, EffectClock.Profile profile, int capacity) {
        this.sha1 = sha1;
        runtime = new EffectRuntime(profile, capacity);
    }
    public EffectRuntime runtime() { return runtime; }
    public void blastTables(com.github.giannigr.racmc.asset.effect.Rac1BlastTables tables) {
        if (procedural != null) procedural.clear();
        procedural = new Rac1ProceduralEffects(runtime, sha1, tables);
    }
    public void collisionContext(Point camera, Rac1ProceduralEffects.Collision collision) {
        if (procedural != null) procedural.context(camera, collision);
    }
    public void beginWorld(String dimension, long generation) {
        clear();
        this.generation = generation;
        gate.beginWorld(dimension, generation);
    }
    public void clear() {
        shots.values().forEach(Rac1BlasterVisuals::close);
        shots.clear();
        births.clear();
        terminal.clear();
        runtime.clear();
        if (procedural != null) procedural.clear();
        lastBlast = Long.MIN_VALUE / 2;
    }
    public boolean ownsProjectile(UUID id) { return shots.containsKey(id) || terminal.containsKey(id); }
    public boolean receive(EffectSignal signal, long tick, Point camera) {
        if (!gate.accept(signal, tick, generation)) return false;
        // Same 250ms presentation catch-up bound as the clock. Keep long dedup tombstones,
        // but never replay a historical one-shot after a stalled connection/UI.
        boolean late = tick - signal.serverTick() > 5;
        if (signal.op() == EffectSignal.Op.STOP) {
            var adapter = shots.remove(signal.lifecycle());
            if (adapter != null) adapter.close();
            runtime.stopOwner(signal.lifecycle());
            markTerminal(signal.lifecycle(), tick);
            return true;
        }
        switch (signal.semantic()) {
            case CRATE_TNT_BLAST -> {
                if (late) return false;
                long originalUpdate = tick * runtime.profile().hz() / 20;
                boolean suppressed = originalUpdate - lastBlast < 2;
                lastBlast = originalUpdate;
                Rac1BlastVisuals.emit(runtime, sha1, signal.lifecycle(),
                        signal.anchor(), camera, signal.seed(), !suppressed && runtime.remaining() > 256);
                if (procedural != null)
                    procedural.blast(signal.lifecycle(), signal.anchor(), camera, signal.seed(), suppressed);
            }
            case CRATE_BREAK_WOOD, CRATE_BREAK_HEALTH -> {
                if (late) return false;
                Rac1BlastVisuals.dust(runtime, sha1, signal.lifecycle(), signal.anchor(), new Random(signal.seed()));
                if (procedural != null)
                    procedural.debris(signal.lifecycle(), signal.anchor(), camera, signal.seed(),
                            signal.semantic() == EffectSignal.Semantic.CRATE_BREAK_HEALTH ? 348 : 357);
            }
            case BLASTER_SHOT -> {
                if (!terminal.containsKey(signal.lifecycle()) && births.size() < 4096)
                    births.putIfAbsent(signal.lifecycle(), signal.serverTick());
            }
            case BLASTER_IMPACT, BLASTER_END -> {
                if (terminal.containsKey(signal.lifecycle())) return false;
                var adapter = shots.remove(signal.lifecycle());
                if (late) {
                    if (adapter != null) adapter.close();
                    markTerminal(signal.lifecycle(), tick);
                    return true; // stop lifecycle, don't restart historical impact smoke
                }
                if (adapter == null) adapter = new Rac1BlasterVisuals(runtime, sha1, signal.lifecycle(),
                        signal.anchor(), signal.seed());
                if (signal.semantic() == EffectSignal.Semantic.BLASTER_IMPACT)
                    adapter.impact(signal.anchor(), signal.impact().tangent(), signal.impact().normal(), signal.seed());
                else adapter.end(signal.anchor(), signal.seed());
                markTerminal(signal.lifecycle(), tick);
            }
        }
        return true;
    }
    private void markTerminal(UUID id, long tick) {
        if (terminal.size() < 4096) terminal.put(id, tick);
        births.remove(id);
    }
    public EffectClock.Advance advance(double seconds, boolean paused, long tick, List<Source> sources) {
        return advance(seconds, paused, tick, sources, () -> {});
    }
    public EffectClock.Advance advance(double seconds, boolean paused, long tick, List<Source> sources,
                                       Runnable additionalFixedUpdate) {
        terminal.entrySet().removeIf(entry -> entry.getValue() < tick - 200);
        births.entrySet().removeIf(entry -> entry.getValue() < tick - 200);
        Set<UUID> tracked = new HashSet<>();
        for (Source source : sources) {
            if (tracked.size() >= 512) break;
            tracked.add(source.lifecycle());
            if (terminal.size() < 4096 && !terminal.containsKey(source.lifecycle()) && !shots.containsKey(source.lifecycle())
                    && runtime.remaining() >= 18) {
                var adapter = new Rac1BlasterVisuals(runtime, sha1, source.lifecycle(), source.position(),
                        source.lifecycle().getLeastSignificantBits());
                shots.put(source.lifecycle(), adapter);
            }
        }
        shots.entrySet().removeIf(entry -> {
            if (tracked.contains(entry.getKey())) return false;
            entry.getValue().close(); // Untracking is not an impact.
            return true;
        });
        var advance = runtime.advance(seconds, paused, () -> {
            if (procedural != null) procedural.step();
            for (Source source : sources) {
                var adapter = shots.get(source.lifecycle());
                if (adapter != null) {
                    long birth = births.getOrDefault(source.lifecycle(), tick);
                    int age = births.containsKey(source.lifecycle())
                            ? (int) Math.min(600, Math.max(0, tick - birth) * runtime.profile().hz() / 20)
                            : Math.min(600, source.originalAge());
                    adapter.update(source.position(), source.tangent(), age);
                }
            }
            additionalFixedUpdate.run();
        });
        if (advance.discontinuity()) {
            if (procedural != null) procedural.clear();
            shots.values().forEach(Rac1BlasterVisuals::close);
            shots.clear();
        }
        return advance;
    }
}
