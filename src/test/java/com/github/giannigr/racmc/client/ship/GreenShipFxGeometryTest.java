package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.ship.GreenShipFxBeats;
import org.junit.jupiter.api.Test;

class GreenShipFxGeometryTest {
    @Test
    void onlyActualFlameBeatsHaveGeometryAndTakeoffIgnoresFlickerSeed() {
        assertTrue(GreenShipFxGeometry.flames(GreenShipFxBeats.parked(421), 1).isEmpty());
        var sample = GreenShipFxBeats.takeoff(460);
        var quads = GreenShipFxGeometry.flames(sample, 1);
        assertEquals(8, quads.size());
        assertEquals(quads, GreenShipFxGeometry.flames(sample, 7));
        var first = quads.getFirst();
        var firstCorner = first.corners().getFirst();
        assertEquals(-3.3f, firstCorner.forward());
        assertEquals(1.85f - 48 * .7f / 40, firstCorner.side(), 1e-6);
        assertEquals(0x5fffaf3f, first.argb());
        assertEquals(1, first.corners().get(2).u());
        assertEquals(1, first.corners().get(2).v());
    }

    @Test
    void landingActorFlickerIsDeterministicAndDoesNotLeakIntoDecay() {
        var approach = GreenShipFxBeats.landing(199);
        var a = GreenShipFxGeometry.flames(approach, 1);
        assertEquals(a, GreenShipFxGeometry.flames(approach, 1));
        assertNotEquals(a, GreenShipFxGeometry.flames(approach, 2));
        assertTrue(a.stream().allMatch(q -> (q.argb() >>> 24) >= 99 && (q.argb() >>> 24) <= 117));
        var decay = GreenShipFxBeats.landing(241);
        assertEquals(GreenShipFxGeometry.flames(decay, 1), GreenShipFxGeometry.flames(decay, 2));
    }
}
