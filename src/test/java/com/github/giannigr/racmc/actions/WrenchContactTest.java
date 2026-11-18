package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WrenchContactTest {
    private static final WrenchContact.Box BOX = new WrenchContact.Box(
            new ActionVector(1, 1, 1), new ActionVector(2, 2, 2));

    @Test void sphereHitsBoxEdgeEvenWhenOriginIsOutside() {
        assertTrue(WrenchContact.sphereIntersects(new ActionVector(0.53, 1.5, 1.5), 0.47, BOX));
        assertFalse(WrenchContact.sphereIntersects(new ActionVector(0.529, 1.5, 1.5), 0.47, BOX));
    }

    @Test void broadphaseCornerIsNotASphereHit() {
        assertFalse(WrenchContact.sphereIntersects(new ActionVector(0.6, 0.6, 1.5), 0.47, BOX));
        assertTrue(WrenchContact.sphereIntersects(new ActionVector(1.5, 1.5, 1.5), 0, BOX));
    }

    @Test void slabIntersectionIsBoundedAndHandlesParallelAxes() {
        assertEquals(0.25, WrenchContact.intersection(new WrenchContact.Segment(
                new ActionVector(0, 1.5, 1.5), new ActionVector(4, 1.5, 1.5)), BOX), 1.0e-9);
        assertEquals(Double.POSITIVE_INFINITY, WrenchContact.intersection(new WrenchContact.Segment(
                new ActionVector(0, 0, 0), new ActionVector(4, 0, 0)), BOX));
        assertEquals(0, WrenchContact.intersection(new WrenchContact.Segment(
                new ActionVector(1.5, 1.5, 1.5), new ActionVector(1.5, 1.5, 1.5)), BOX));
    }

    @Test void sweepUsesFiveIntermediatePosesAndShortTipExtension() {
        var rays = WrenchContact.sweep(ActionVector.ZERO, new ActionVector(1, 0, 0),
                new ActionVector(0, 1, 0), new ActionVector(1, 1, 0));
        assertEquals(5, rays.size());
        assertEquals(0.2, rays.getFirst().from().y(), 1.0e-9);
        assertEquals(1.17, rays.getLast().to().x(), 1.0e-9);
        assertEquals(1, rays.getLast().to().y(), 1.0e-9);
    }

    @Test void invalidVolumesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> WrenchContact.sphereIntersects(ActionVector.ZERO, -1, BOX));
        assertThrows(IllegalArgumentException.class, () -> new WrenchContact.Box(
                new ActionVector(2, 0, 0), new ActionVector(1, 0, 0)));
    }
}
