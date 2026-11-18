package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.Rac1ClankSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-observer semantic state. No grants, local assets or shared PlayerModel mutations. */
public final class Rac1ClankViews {
    private record Observed(Rac1ClankSession.Snapshot snapshot, long receivedLocalTick) {}
    private final Map<UUID, Observed> states = new HashMap<>();
    public boolean accept(Rac1ClankSession.Snapshot next) {
        return accept(next, next.serverTick());
    }
    public boolean accept(Rac1ClankSession.Snapshot next, long localTick) {
        if (localTick < 0 || (!states.containsKey(next.player()) && states.size() >= 4096)) return false;
        var entry = states.get(next.player());
        var old = entry == null ? null : entry.snapshot();
        if (old != null && (next.epoch() < old.epoch() || (next.epoch() == old.epoch()
                && (next.revision() < old.revision() || (next.revision() == old.revision()
                && next.serverTick() <= old.serverTick()) || next.serverTick() < old.serverTick()))))
            return false;
        states.put(next.player(), new Observed(next, localTick));
        return true;
    }
    public Rac1ClankSession.Snapshot get(UUID player) {
        var entry = states.get(player);
        return entry == null ? null : entry.snapshot();
    }
    public double serverTime(UUID player, double localTick) {
        if (!Double.isFinite(localTick)) throw new IllegalArgumentException("Invalid local time");
        var entry = states.get(player);
        return entry == null ? 0 : entry.snapshot().serverTick()
                + Math.max(0, localTick - entry.receivedLocalTick());
    }
    public boolean anyWorn() {
        return states.values().stream().anyMatch(s -> s.snapshot().equipment().worn());
    }
    public void forget(UUID player) { states.remove(player); }
    public void clear() { states.clear(); }
}
