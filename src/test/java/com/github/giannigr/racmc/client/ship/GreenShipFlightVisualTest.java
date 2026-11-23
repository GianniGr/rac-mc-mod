package com.github.giannigr.racmc.client.ship;

import static com.github.giannigr.racmc.ship.ShipEngineState.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ship.ShipEngineBeat;
import com.github.giannigr.racmc.ship.ShipEngineBeat.Kind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GreenShipFlightVisualTest {
    private static final UUID SHIP = new UUID(0, 6);

    @Test
    void accessCanOnlyVetoNeverCreateAnEffectAndConsumesStaleEdges() {
        var visual = new GreenShipFlightVisual();
        assertNull(visual.observe(UNVERIFIED, null, false, true, 48, 0).sample());
        var implausiblyPaired = edge(Kind.LIFTOFF, 1, 49);
        assertNull(visual.observe(APPROACH, implausiblyPaired, false, true, 49, 0).sample());
        assertNull(visual.observe(SUPPORTED, implausiblyPaired, true, true, 50, 0).sample());
        assertNull(visual.observe(AIRBORNE, null, true, true, 51, 0).sample());
        assertNull(visual.observe(APPROACH, null, false, true, 52, 0).sample());
    }

    @Test
    void edgeStartsOnceAndDoesNotRequireFreshStatePacketInTheSameFrame() {
        var visual = new GreenShipFlightVisual();
        visual.observe(SUPPORTED, null, true, true, 50, 0);
        var lift = edge(Kind.LIFTOFF, 1, 51);
        assertEquals(8, visual.observe(SUPPORTED, lift, true, true, 51, 0).sample().flameQuads());
        var continued = visual.observe(AIRBORNE, lift, true, true, 52, 0);
        assertEquals(0, continued.newExhaustBursts());
        assertTrue(continued.sample().flameCommand() > 1);
        assertNull(visual.observe(AIRBORNE, lift, true, true, 80, 0).sample());
        var earlyContact = edge(Kind.TOUCHDOWN, 2, 81);
        assertNull(visual.observe(AIRBORNE, earlyContact, true, true, 80, 0).sample());
        assertNotNull(visual.observe(SUPPORTED, null, true, true, 81, 0).sample());
    }

    @Test
    void approachIsCurrentOnlyAndUnsafeDescentImmediatelyStopsPrecontactExhaust() {
        var visual = new GreenShipFlightVisual();
        var late = visual.observe(APPROACH, null, true, true, 120, 0);
        assertTrue(late.safeExhaust()); // Current replicated proof, not a historical edge or emit.
        assertEquals(0, late.newExhaustBursts());
        assertEquals(2, visual.observe(APPROACH, null, true, true, 121, 0).newExhaustBursts());
        assertEquals(0, visual.observe(APPROACH, null, true, true, 121, .5f).newExhaustBursts());
        assertEquals(0, visual.observe(APPROACH, null, true, true, 130, 0).newExhaustBursts());
        assertEquals(2, visual.observe(APPROACH, null, true, true, 131, 0).newExhaustBursts());
        assertFalse(visual.observe(AIRBORNE, null, true, true, 132, 0).safeExhaust());
        assertFalse(visual.observe(UNVERIFIED, null, true, true, 133, 0).safeExhaust());
    }

    @Test
    void touchdownVetoesOldApproachEvenWithoutArtAndDoesNotReplayOnImportCompletion() {
        var visual = new GreenShipFlightVisual();
        visual.observe(APPROACH, null, true, true, 93, 0);
        var contact = edge(Kind.TOUCHDOWN, 1, 94);
        assertFalse(visual.observe(APPROACH, contact, true, false, 94, 0).safeExhaust());
        var frame = visual.observe(APPROACH, contact, true, true, 95, 0);
        assertFalse(frame.safeExhaust());
        assertNull(frame.sample());
        assertNull(visual.observe(SUPPORTED, contact, true, true, 96, 0).sample());
    }

    @Test
    void delayedStaleEdgesDoNotPlayAndUnverifiedOrAssetLossCancelsActiveEdges() {
        var visual = new GreenShipFlightVisual();
        assertNull(visual.observe(AIRBORNE, edge(Kind.LIFTOFF, 1, 50), true, true, 80, 0).sample());
        var lift = edge(Kind.LIFTOFF, 2, 81);
        assertNotNull(visual.observe(AIRBORNE, lift, true, true, 81, 0).sample());
        assertNull(visual.observe(UNVERIFIED, null, true, true, 82, 0).sample());
        assertNull(visual.observe(AIRBORNE, lift, true, true, 83, 0).sample());
        var fresh = edge(Kind.TOUCHDOWN, 3, 84);
        assertNotNull(visual.observe(SUPPORTED, fresh, true, true, 84, 0).sample());
        assertNull(visual.observe(SUPPORTED, fresh, true, false, 85, 0).sample());
        assertNull(visual.observe(SUPPORTED, fresh, true, true, 86, 0).sample());
    }

    private static ShipEngineBeat edge(Kind kind, long serial, long tick) {
        return new ShipEngineBeat(42, SHIP, kind, serial, tick);
    }
}
