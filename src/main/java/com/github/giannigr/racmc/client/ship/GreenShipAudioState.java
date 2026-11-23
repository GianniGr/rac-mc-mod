package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.ship.ShipEngineBeat;
import com.github.giannigr.racmc.ship.ShipEngineState;
import com.github.giannigr.racmc.ship.ShipThrust;

/** Policy for the Minecraft-only engine audio. Each instance belongs to one tracked entity. */
final class GreenShipAudioState {
    record Frame(ShipEngineBeat.Kind cue, boolean loop, ShipThrust thrust) {}

    private long lastSerial;
    private boolean contactPending;

    Frame sample(ShipEngineState state, ShipThrust thrust, ShipEngineBeat beat, boolean piloting, long now) {
        if (!state.inFlight() || !piloting) contactPending = false;
        ShipEngineBeat.Kind cue = null;
        if (beat != null && beat.serial() > lastSerial) {
            lastSerial = beat.serial();
            long age = now - beat.serverTick();
            if (piloting && state != ShipEngineState.UNVERIFIED && age >= -2 && age <= 6) {
                cue = beat.kind();
                contactPending = cue == ShipEngineBeat.Kind.TOUCHDOWN && state.inFlight();
            }
        }
        boolean loop = piloting && state.inFlight() && !contactPending;
        return new Frame(cue, loop, loop ? thrust : ShipThrust.OFF);
    }
}
