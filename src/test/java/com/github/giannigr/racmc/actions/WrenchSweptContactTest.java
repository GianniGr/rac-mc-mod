package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WrenchSweptContactTest {
    @Test void sweptCrateContactCatchesThinBoxBetweenHighVelocityEndpoints() {
        var path = new WrenchContact.Segment(new ActionVector(0, 0.5, 0.5),
                new ActionVector(5, 0.5, 0.5));
        var thin = new WrenchContact.Box(new ActionVector(2, 0, 0), new ActionVector(2.05, 1, 1));
        assertFalse(WrenchContact.sphereIntersects(path.from(), 0.4, thin));
        assertFalse(WrenchContact.sphereIntersects(path.to(), 0.4, thin));
        assertEquals(0.32, WrenchContact.sweptSphere(path, 0.4, thin), 1.0e-8);
    }

    @Test void inflatedAabbCornerIsNotAnOriginalSmallSphereContact() {
        var path = new WrenchContact.Segment(new ActionVector(-1, 1.35, 1.35),
                new ActionVector(2, 1.35, 1.35));
        var box = new WrenchContact.Box(ActionVector.ZERO, new ActionVector(1, 1, 1));
        assertEquals(Double.POSITIVE_INFINITY, WrenchContact.sweptSphere(path, 0.4, box));
    }
}
