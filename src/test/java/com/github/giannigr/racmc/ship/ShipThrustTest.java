package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipThrustTest {
    @Test
    void bothNormalAndTurboUseTheResolvedForwardAxisNotCoastingOrLateralMotion() {
        var forward = new Vec3(0, 0, .3);
        for (boolean boosted : new boolean[]{false, true}) {
            var drive = step(FORWARD, 0, Vec3.ZERO, false, boosted);
            assertTrue(drive.deliveredForward(forward));
            assertFalse(drive.deliveredForward(new Vec3(.3, .1, 0)));
            assertFalse(drive.deliveredForward(forward.reverse()));
            assertFalse(step(FORWARD | BRAKE, 0, forward, false, boosted).deliveredForward(forward));
            assertFalse(step(0, 0, forward, false, boosted).deliveredForward(forward));
            assertFalse(step(FORWARD, 0, forward, true, boosted).deliveredForward(forward));
            assertTrue(step(FORWARD, 90, Vec3.ZERO, false, boosted).deliveredForward(new Vec3(-.3, 0, 0)));
        }
    }
}
