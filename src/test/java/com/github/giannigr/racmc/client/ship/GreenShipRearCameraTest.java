package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipRearCameraTest {
    @Test
    void measuredAftClearanceIncludesRoomBeyondTheTailWithoutChangingOrbit() {
        var eye = ShipCockpitRefit.CANDIDATE.seatLocal().add(0, 1.62, 0);
        assertEquals(9.749, GreenShipRearCamera.framingDistance(eye, new Vec3(0, 0, -1)), .001);
        assertEquals(6.25, GreenShipRearCamera.framingDistance(eye, new Vec3(1, 0, 0)), .001);
        var diagonal = new Vec3(1, 0, -1).normalize();
        assertTrue(GreenShipRearCamera.framingDistance(eye, diagonal) > 9);
    }

    @Test
    void loadedPrefixStopsBeforeAnUnloadedSliverIncludingNearPlane() {
        var eye = new Vec3(1, 64, 10);
        var away = new Vec3(0, 0, -1);
        assertEquals(3.25, GreenShipRearCamera.loadedDistance(eye, away, 9.749, box -> box.minZ >= 6.5));
        assertEquals(0, GreenShipRearCamera.loadedDistance(eye, away, 9.749, box -> false));
        assertEquals(9.749, GreenShipRearCamera.loadedDistance(eye, away, 9.749, box -> true));
    }
}
