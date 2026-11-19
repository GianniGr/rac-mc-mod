package com.github.giannigr.racmc.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded current-world revision gate; old accepted shots cannot restart ended loops.
 * Refresh every accepted two-tick shot, expire after three ticks without one.
 */
public final class FireLoopState {
    private record Entry(UUID session, long revision, long tick, boolean active) {}
    private final Map<UUID, Entry> entries = new HashMap<>();
    public boolean accept(UUID emitter, UUID session, long revision, long serverTick,
                          boolean active, long now) {
        entries.entrySet().removeIf(e -> now - e.getValue().tick() > 200);
        Entry old = entries.get(emitter);
        if (revision < 0 || (old != null && revision <= old.revision())
                || (active && (now - serverTick > 5 || serverTick - now > 5))
                || (old == null && entries.size() >= 128)) return false;
        entries.put(emitter, new Entry(session, revision, serverTick, active));
        return true;
    }
    public boolean active(UUID emitter, UUID session, long now) {
        Entry entry = entries.get(emitter);
        return entry != null && entry.active() && entry.session().equals(session)
                && now - entry.tick() <= 3 && now >= entry.tick() - 5;
    }
    public void clear() { entries.clear(); }
}
