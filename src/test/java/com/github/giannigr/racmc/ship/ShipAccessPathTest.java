package com.github.giannigr.racmc.ship;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShipAccessPathTest {
    @Test
    void pathPreservesLegCornersAndSerialForPresentationAndRollback() {
        var ground = new Vec3(5.70, 0, 0);
        var route = new ShipAccessPath(ShipCockpitRefit.waypoints(ground), 113, 47, false);
        assertEquals(route, ShipAccessPath.decode(route.encode()));
        assertEquals(new Vec3(5.70, 2.075, 0), route.sample(4));
        assertEquals(4.15, route.sample(12).y);
        assertEquals(ShipCockpitRefit.CANDIDATE.sillLocal(), route.sample(16));
        assertEquals(ShipCockpitRefit.CANDIDATE.seatLocal(), route.sample(24));
        var back = new ShipAccessPath(route.reversePrefix(12), 125, 48, true);
        assertEquals(route.sample(12), back.sample(0));
        assertEquals(route.sample(8), back.sample(8));
        assertEquals(ground, back.sample(100));
        assertEquals(Boolean.getBoolean("racmc.shipLegacyProfile")
                ? ShipCockpitProfile.CURRENT : ShipCockpitRefit.CANDIDATE, ShipCockpitRefit.activeProfile());
    }
}
