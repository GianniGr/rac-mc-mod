package com.github.giannigr.racmc.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded deduplication window. Call beginWorld on dimension/disconnect/generation change.
 * Old packets outside a 10-second window are rejected; capacity fails closed, never evicts a
 * still-valid tombstone and accidentally replays an event. */
public final class EffectEventGate {
    private record Key(UUID lifecycle, EffectSignal.Semantic semantic) {}
    private record Seen(long sequence, long tick) {}
    private final Map<Key, Seen> seen = new HashMap<>();
    private String dimension;
    private long generation;
    public void beginWorld(String dimension, long generation) {
        this.dimension = dimension;
        this.generation = generation;
        seen.clear();
    }
    public boolean accept(EffectSignal signal, long currentTick, long generation) {
        if (this.generation != generation || !signal.dimension().equals(dimension)
                || currentTick < 0 || signal.serverTick() - currentTick > 20
                || signal.serverTick() < currentTick - 200) return false;
        seen.entrySet().removeIf(entry -> entry.getValue().tick() < currentTick - 200);
        Key key = new Key(signal.lifecycle(), signal.semantic());
        Seen previous = seen.get(key);
        if (previous != null && signal.sequence() <= previous.sequence()) return false;
        if (previous == null && seen.size() >= 4096) return false;
        seen.put(key, new Seen(signal.sequence(), signal.serverTick()));
        return true;
    }
    public int size() { return seen.size(); }
}
