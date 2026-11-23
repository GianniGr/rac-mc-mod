package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.RacMcMod;
import java.util.concurrent.atomic.AtomicInteger;

/** Local retest diagnostics only; opt-in, bounded, no extra network traffic. */
final class ShipMotionTrace {
    static final boolean ENABLED = Boolean.getBoolean("racmc.shipMotionTrace");
    private static final int MAX_ROWS = 1800;
    private static final AtomicInteger ROWS = new AtomicInteger();

    static void log(Rac1TravelShip ship, String event, Object... fields) {
        if (!ENABLED || ROWS.getAndIncrement() >= MAX_ROWS) return;
        RacMcMod.LOGGER.info("[ship-motion] side={} tick={} entityTick={} id={} uuid={} phase={} boost={} {} {}",
                ship.level().isClientSide ? "client" : "server", ship.level().getGameTime(), ship.tickCount,
                ship.getId(), ship.getUUID(), ship.accessPhase(), ship.isBoosted(), event,
                java.util.Arrays.toString(fields));
    }

    private ShipMotionTrace() {}
}
