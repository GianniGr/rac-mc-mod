package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.actions.WrenchActionPresentation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cosmetic starts are ordered and one-time per emitter. Presentation updates never start sounds. */
public final class WrenchAudioGate {
    private static final int FRESH_TICKS = 6;
    private static final int MAX_EMITTERS = 128;
    private record Edge(long action, long tick) {}
    private final Map<UUID, Edge> consumed = new HashMap<>();
    private long minimumTick = Long.MIN_VALUE;

    public boolean claim(UUID emitter, long action, long sourceTick, long now) {
        if (action <= 0 || sourceTick <= minimumTick || !fresh(sourceTick, now)) return false;
        consumed.entrySet().removeIf(e -> now - e.getValue().tick() > FRESH_TICKS);
        Edge last = consumed.get(emitter);
        if (last != null ? sourceTick <= last.tick() || action == last.action()
                : consumed.size() >= MAX_EMITTERS) return false;
        consumed.put(emitter, new Edge(action, sourceTick));
        return true;
    }

    public void fence(long tick) { minimumTick = tick; }

    public void worldChanged() {
        consumed.clear();
        minimumTick = Long.MIN_VALUE;
    }

    public static boolean flightMatches(long action, long viewAction,
            WrenchActionPresentation.Phase phase, long viewTick, long now) {
        return action > 0 && action == viewAction && WrenchActionPresentation.inFlight(phase)
                && fresh(viewTick, now);
    }

    private static boolean fresh(long tick, long now) {
        return tick >= 0 && now >= 0 && Math.abs(now - tick) <= FRESH_TICKS;
    }
}
