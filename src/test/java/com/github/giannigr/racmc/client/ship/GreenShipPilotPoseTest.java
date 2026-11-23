package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipPilotPoseTest {
    private static final Vec3 BOARD = new Vec3(10, 65, -20);
    private static final Vec3 SILL = new Vec3(11, 66, -20);
    private static final Vec3 SEAT = new Vec3(12, 65.75, -20);
    private static final Vec3 EXIT = new Vec3(11, 65, -22);

    @Test
    void boardTracksRealAnchorsAndFinishesAtTheSeatedPose() {
        assertEquals(BOARD, GreenShipPilotPose.board(BOARD, SILL, SEAT, -1).base());
        assertEquals(SILL, GreenShipPilotPose.board(BOARD, SILL, SEAT, .46).base());
        assertEquals(1, GreenShipPilotPose.board(BOARD, SILL, SEAT, .46).canopyAccess());
        assertEquals(GreenShipPilotPose.seated(SEAT), GreenShipPilotPose.board(BOARD, SILL, SEAT, 2));
    }

    @Test
    void exitIsASeparatePoseAndLandsAtTheServerChosenDestination() {
        assertEquals(GreenShipPilotPose.seated(SEAT), GreenShipPilotPose.exit(SEAT, SILL, EXIT, 0));
        var step = GreenShipPilotPose.exit(SEAT, SILL, EXIT, .58);
        assertEquals(SILL, step.base());
        assertNotEquals(GreenShipPilotPose.board(EXIT, SILL, SEAT, .46).angles(), step.angles());
        assertEquals(EXIT, GreenShipPilotPose.exit(SEAT, SILL, EXIT, 1).base());
        assertEquals(0, GreenShipPilotPose.exit(SEAT, SILL, EXIT, 1).canopyAccess());
    }

    @Test
    void anchorPathIsContinuousAcrossSegmentBoundaries() {
        double epsilon = 1e-5;
        assertTrue(GreenShipPilotPose.board(BOARD, SILL, SEAT, .46 - epsilon).base()
                .distanceTo(GreenShipPilotPose.board(BOARD, SILL, SEAT, .46 + epsilon).base()) < epsilon);
        assertTrue(GreenShipPilotPose.exit(SEAT, SILL, EXIT, .58 - epsilon).base()
                .distanceTo(GreenShipPilotPose.exit(SEAT, SILL, EXIT, .58 + epsilon).base()) < epsilon);
    }
}
