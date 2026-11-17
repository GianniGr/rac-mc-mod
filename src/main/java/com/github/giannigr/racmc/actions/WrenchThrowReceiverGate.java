package com.github.giannigr.racmc.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Original kind0 receiver timing in 60Hz updates, not a once-per-flight/leg blacklist. */
public final class WrenchThrowReceiverGate {
    public static final int COMET_UPDATES = 37;
    private record Key(UUID owner, UUID receiver) {}
    private static final class Entry {
        int identity;
        long ready;
        long sample = Long.MIN_VALUE;
        long seen;
    }
    private final Map<Key, Entry> receivers = new HashMap<>();
    private long lastPrune = Long.MIN_VALUE;

    public boolean message(UUID owner, UUID receiver, int identity, long sourceUpdate) {
        if (identity != 1 && identity != 2) throw new IllegalArgumentException("Not comet identity");
        Entry entry = receivers.computeIfAbsent(new Key(owner, receiver), key -> new Entry());
        if (sourceUpdate <= entry.sample) return false;
        entry.sample = entry.seen = sourceUpdate;
        return entry.identity != identity || sourceUpdate >= entry.ready;
    }

    public void accepted(UUID owner, UUID receiver, int identity, long sourceUpdate) {
        Entry entry = receivers.computeIfAbsent(new Key(owner, receiver), key -> new Entry());
        entry.identity = identity;
        entry.ready = sourceUpdate + (identity == 1 || identity == 2 ? COMET_UPDATES : 15);
        entry.seen = sourceUpdate;
    }

    public void prune(long now) {
        if (lastPrune != Long.MIN_VALUE && now - lastPrune < 60) return;
        lastPrune = now;
        receivers.entrySet().removeIf(e -> now - e.getValue().seen > 300);
    }
    public void forget(UUID player) {
        receivers.keySet().removeIf(key -> key.owner.equals(player) || key.receiver.equals(player));
    }
    public void clear() { receivers.clear(); lastPrune = Long.MIN_VALUE; }
}
