package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ship.Rac1TravelShip;
import com.github.giannigr.racmc.ship.Rac1TravelShip.AccessPhase;
import com.github.giannigr.racmc.ship.ShipAccessPath;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipAccessVisualTest {
    private static final Vec3 BOARD = new Vec3(1, 0, .9);
    private static final Vec3 SILL = new Vec3(0, .65, .2);
    private static final Vec3 SEAT = new Vec3(0, .54, 0);
    private static final Vec3 EXIT = new Vec3(-1, 0, .7);

    @Test
    void lateTrackingSamplesCurrentEightTickPhaseWithoutReplayingStart() {
        var snapshot = new Rac1TravelShip.AccessSnapshot(AccessPhase.BOARDING, 100, 2, BOARD, SEAT, null);
        assertEquals(BOARD, GreenShipAccessVisual.sample(snapshot, 99, 0, SILL).base());
        assertTrue(GreenShipAccessVisual.sample(snapshot, 104, 0, SILL).base().distanceTo(SILL) < .05);
        assertEquals(GreenShipPilotPose.seated(SEAT), GreenShipAccessVisual.sample(snapshot, 108, 0, SILL));
    }

    @Test
    void exitStaysOnChosenAnchorAndParkedHasNoRiderPose() {
        var exit = new Rac1TravelShip.AccessSnapshot(AccessPhase.EXITING, 150, 4, null, SEAT, EXIT);
        assertEquals(EXIT, GreenShipAccessVisual.sample(exit, 158, 0, SILL).base());
        var parked = new Rac1TravelShip.AccessSnapshot(AccessPhase.PARKED, 158, 5, null, SEAT, null);
        assertNull(GreenShipAccessVisual.sample(parked, 158, 0, SILL));
    }

    @Test
    void routedPoseUsesConfirmedWaypointsAndNewRollbackSerialWithoutReturningAPosition() {
        var seat = ShipCockpitRefit.CANDIDATE.seatLocal();
        var sill = ShipCockpitRefit.CANDIDATE.sillLocal();
        var outside = new Vec3(5.7, 0, 0);
        var boarding = new Rac1TravelShip.AccessSnapshot(AccessPhase.BOARDING, 100, 2, outside, seat, null);
        var route = new ShipAccessPath(ShipCockpitRefit.waypoints(outside), 100, 2, false);
        assertEquals(24, route.durationTicks());
        assertEquals(0, GreenShipAccessVisual.routed(boarding, route, 8).rightLeg());
        assertEquals(GreenShipCockpitInterior.POSE, GreenShipAccessVisual.routed(boarding, route, 16));
        assertEquals(GreenShipCockpitInterior.POSE, GreenShipAccessVisual.routed(boarding, route, 24));
        assertNull(GreenShipAccessVisual.routed(boarding, route, 25));
        var rollback = new ShipAccessPath(List.of(route.sample(20), sill,
                new Vec3(outside.x, sill.y, outside.z), outside), 120, 3, true);
        assertNull(GreenShipAccessVisual.routed(boarding, rollback, 0));
        var returning = new Rac1TravelShip.AccessSnapshot(AccessPhase.BOARDING, 120, 3, outside, seat, null);
        assertEquals(GreenShipCockpitInterior.POSE, GreenShipAccessVisual.routed(returning, rollback, 4));
        assertEquals(0, GreenShipAccessVisual.routed(returning, rollback, 16).rightLeg());
        assertEquals(0, GreenShipAccessVisual.routed(returning, rollback, 24).rightLeg());
    }
}
