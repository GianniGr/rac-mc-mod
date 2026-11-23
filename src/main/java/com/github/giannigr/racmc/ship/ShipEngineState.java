package com.github.giannigr.racmc.ship;

/** Current server-verified physics sample, not a replayable event or an animation timer. */
public enum ShipEngineState {
    UNVERIFIED,
    SUPPORTED,
    AIRBORNE,
    /** Descending on a clear swept path towards verified solid contact in the loaded lookahead. */
    APPROACH;

    public boolean inFlight() {
        return this == AIRBORNE || this == APPROACH;
    }
}
