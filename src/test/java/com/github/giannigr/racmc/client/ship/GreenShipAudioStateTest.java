package com.github.giannigr.racmc.client.ship;

import static com.github.giannigr.racmc.ship.ShipEngineState.*;
import static com.github.giannigr.racmc.ship.ShipThrust.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ship.ShipEngineBeat;
import com.github.giannigr.racmc.ship.ShipEngineBeat.Kind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GreenShipAudioStateTest {
    @Test
    void lateTrackingGetsOnlyCurrentLoopAndContactStopsItBeforeStateArrives() {
        var audio = new GreenShipAudioState();
        var late = audio.sample(AIRBORNE, BOOST, null, true, 50);
        assertNull(late.cue());
        assertTrue(late.loop());
        assertEquals(BOOST, late.thrust());
        assertEquals(OFF, audio.sample(AIRBORNE, OFF, null, true, 51).thrust());
        var contact = beat(Kind.TOUCHDOWN, 2, 52);
        var landed = audio.sample(APPROACH, NORMAL, contact, true, 52);
        assertEquals(Kind.TOUCHDOWN, landed.cue());
        assertFalse(landed.loop());
        assertFalse(audio.sample(APPROACH, NORMAL, null, true, 53).loop());
        assertNull(audio.sample(SUPPORTED, OFF, contact, true, 54).cue());
        assertFalse(audio.sample(AIRBORNE, NORMAL, null, false, 55).loop());
    }

    @Test
    void accessAndStaleEdgesCannotCreateOrReplayCues() {
        var audio = new GreenShipAudioState();
        var stale = beat(Kind.LIFTOFF, 1, 30);
        assertNull(audio.sample(SUPPORTED, OFF, stale, true, 40).cue());
        var boarding = beat(Kind.LIFTOFF, 2, 41);
        assertNull(audio.sample(SUPPORTED, OFF, boarding, false, 41).cue());
        assertNull(audio.sample(SUPPORTED, OFF, boarding, true, 42).cue());
        var real = beat(Kind.LIFTOFF, 3, 43);
        assertEquals(Kind.LIFTOFF, audio.sample(SUPPORTED, OFF, real, true, 43).cue());
        assertNull(audio.sample(AIRBORNE, NORMAL, real, true, 44).cue());
        assertFalse(audio.sample(UNVERIFIED, OFF, null, true, 45).loop());
    }

    private static ShipEngineBeat beat(Kind kind, long serial, long tick) {
        return new ShipEngineBeat(1, new UUID(0, 1), kind, serial, tick);
    }
}
