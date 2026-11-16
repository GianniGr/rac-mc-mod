package com.github.giannigr.racmc.loot;

import java.util.List;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import com.github.giannigr.racmc.loot.Rac1BoltCollision.Box;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltCollisionTest {
    private static Rac1BoltCollision world(Box... boxes) {
        return new Rac1BoltCollision((a, b, radius) -> List.of(boxes));
    }
    @Test void thinWallSweepDoesNotTunnelAndReflects() {
        var c = world(new Box(1, -5, -5, 1.01, 5, 5));
        Movement m = c.move(V.ZERO, new V(3, 0, 0), .15);
        assertFalse(m.contacts().isEmpty());
        assertTrue(m.position().x() < 1);
        assertTrue(m.contacts().stream().allMatch(h -> h.separation().x() < 0));
    }
    @Test void slabFloorSettlesAndCeilingDoesNot() {
        var floor = world(new Box(-5, 0, -5, 5, .5, 5));
        Movement landed = floor.move(new V(0, .8, 0), new V(0, -.3, 0), .23);
        assertTrue(landed.position().y() >= .73);
        assertTrue(landed.contacts().stream().anyMatch(h -> Rac1BoltMotion.ground(h.separation(), h.normal())));
        var ceiling = world(new Box(-5, 1, -5, 5, 2, 5));
        Movement hit = ceiling.move(new V(0, .8, 0), new V(0, .15, 0), .15);
        assertFalse(hit.contacts().isEmpty());
        assertFalse(hit.contacts().stream().anyMatch(h -> Rac1BoltMotion.ground(h.separation(), h.normal())));
    }
    @Test void embeddedSpawnAndTwoFaceCornerRemainFiniteAndOutsideSolids() {
        var c = world(new Box(0, 0, 0, 1, 1, 1));
        Movement m = c.move(new V(.9, .5, .5), V.ZERO, .15);
        assertTrue(m.position().x() >= 1.15);
        var corner = world(new Box(1, -2, -2, 2, 2, 2), new Box(-2, -2, 1, 2, 2, 2));
        Movement hit = corner.move(new V(.8, 0, .8), new V(.2, 0, .2), .15);
        assertTrue(hit.position().x() <= .85);
        assertTrue(hit.position().z() <= .85);
        assertTrue(hit.contacts().size() >= 2);
    }
    @Test void ledgeHasNoInventedFloor() {
        var air = world();
        var p = air.move(new V(4, 50, 6), new V(.05, -.2, 0), .25).position();
        assertEquals(4.05, p.x(), 1e-12);
        assertEquals(49.8, p.y(), 1e-12);
        assertEquals(6, p.z(), 1e-12);
    }
}
