package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipFxBeats;
import com.github.giannigr.racmc.ship.ShipEngineBeat;
import com.github.giannigr.racmc.ship.ShipEngineState;

/** Cosmetic clock: transient server edges and current safe-approach state are separate authorities. */
public final class GreenShipFlightVisual {
    public record Frame(GreenShipFxBeats.Sample sample, boolean safeExhaust, int newExhaustBursts) {
        static final Frame INACTIVE = new Frame(null, false, 0);
    }

    private static final int MAX_EDGE_AGE = 6;
    private static final int MAX_EDGE_CLOCK_LEAD = 2;
    private long lastSerial;
    private long lastServerTick = -1;
    private long lastTick = -1;
    private ShipEngineState lastState = ShipEngineState.UNVERIFIED;
    private boolean previousAccessClear;
    private ShipEngineBeat active;
    private boolean touchdownBlocksApproach;

    /**
     * Sample only the current frame. An unavailable edge material consumes the edge rather
     * than replaying it after asset delivery. First tracking and clock gaps never emit exhaust.
     */
    public Frame observe(ShipEngineState state, ShipEngineBeat edge, boolean accessClear,
                         boolean edgeArtReady, long tick, float partial) {
        if (tick < 0 || tick < lastTick) {
            active = null;
            return Frame.INACTIVE;
        }
        boolean nextTick = previousAccessClear && lastTick >= 0 && tick - lastTick == 1;
        boolean invalidated = state == ShipEngineState.UNVERIFIED && state != lastState;
        lastTick = tick;
        lastState = state;
        previousAccessClear = accessClear;
        if (state != ShipEngineState.APPROACH) {
            touchdownBlocksApproach = false;
        }
        if (!accessClear || invalidated) {
            active = null;
        }
        if (edge != null && edge.serial() > lastSerial) {
            lastSerial = edge.serial();
            long age = edge.serverTick() < 0 ? Long.MAX_VALUE : tick - edge.serverTick();
            boolean fresh = edge.serverTick() >= 0 && edge.serverTick() >= lastServerTick
                    && age >= -MAX_EDGE_CLOCK_LEAD && age <= MAX_EDGE_AGE;
            if (fresh) {
                lastServerTick = edge.serverTick();
                active = accessClear && !invalidated && edgeArtReady ? edge : null;
                if (edge.kind() == ShipEngineBeat.Kind.TOUCHDOWN && state == ShipEngineState.APPROACH) {
                    // The transient contact can arrive before the replicated supported state.
                    touchdownBlocksApproach = true;
                }
            }
        }
        if (!accessClear || invalidated || state == ShipEngineState.UNVERIFIED) {
            return Frame.INACTIVE;
        }
        if (!edgeArtReady) {
            // Once lost, this edge never resumes, including across resource reload.
            active = null;
        }
        int subframe = Float.isFinite(partial) ? (int) (Math.clamp(partial, 0, .9999f) * 3) : 0;
        if (state == ShipEngineState.APPROACH && !touchdownBlocksApproach
                && (active == null || active.kind() != ShipEngineBeat.Kind.TOUCHDOWN)) {
            active = null;
            // The approach is a current / at-most-eight-step proof, not a stored sequence.
            int reference = (int) ((tick & 63) * 3 + subframe) & 63;
            var sample = GreenShipFxBeats.landing(reference);
            return new Frame(sample, true, nextTick ? sample.exhaustBursts() : 0);
        }
        if (active == null || tick < active.serverTick()) {
            return Frame.INACTIVE;
        }
        int frame = (int) Math.min(tick - active.serverTick(), 200) * 3 + subframe;
        var sample = switch (active.kind()) {
            case LIFTOFF -> frame < 83 ? GreenShipFxBeats.takeoff(421 + frame) : null;
            case TOUCHDOWN -> frame <= 120 ? GreenShipFxBeats.landing(240 + frame) : null;
        };
        if (sample == null) {
            active = null;
            return Frame.INACTIVE;
        }
        return new Frame(sample, false, 0);
    }
}
