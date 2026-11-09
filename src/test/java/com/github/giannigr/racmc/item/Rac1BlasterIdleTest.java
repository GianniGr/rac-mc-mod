package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the weapon of the game while it is only held.
 *
 * <p>The game of blocks holds an item in the hand with the arm of the player hanging at the side
 * of his body. The weapon of the game is turned around its own side while it is only held, so
 * that the bore of the weapon points down along the arm of the player and the weapon follows the
 * arm of the player while he walks. The pose of the aim is not touched by this turn.
 */
class Rac1BlasterIdleTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-3;

    /**
     * The turn of the arm of the player while the game of blocks holds an item and the player
     * stands still, in degrees.
     */
    private static final double RESTING_ARM = -18.0;

    @Test
    @DisplayName("The bore of the weapon points down along the arm of the player")
    void theBorePointsDownAlongTheArm() {
        Vector3f bore = Rac1BlasterChains.direction(idleChain(), Rac1BlasterChains.BORE);
        // The arm of the player hangs down and a little forward, so the bore of the weapon points
        // down and a little forward.
        assertEquals(0.0, bore.x, TOLERANCE,
                "The bore of the weapon does not point along the arm of the player");
        assertEquals(-Math.cos(Math.toRadians(-RESTING_ARM)), bore.y, TOLERANCE,
                "The bore of the weapon does not point down along the arm of the player");
        assertEquals(Math.sin(Math.toRadians(-RESTING_ARM)), bore.z, TOLERANCE,
                "The bore of the weapon does not point along the arm of the player");
        assertTrue(bore.y < 0.0, "The bore of the weapon does not point down");
    }

    @Test
    @DisplayName("The bore of the weapon follows the arm of the player while he walks")
    void theBoreFollowsTheArm() {
        // The arm of the player swings forward while he walks, and the bore of the weapon turns
        // with the arm of the player.
        Vector3f resting = Rac1BlasterChains.direction(idleChain(), Rac1BlasterChains.BORE);
        Vector3f walking = Rac1BlasterChains.direction(
                Rac1BlasterChains.thirdPersonIdle(0.0, 0.0, 0.0, Rac1BlasterGrip.IDLE_ROTATION),
                Rac1BlasterChains.BORE);
        double between = Math.toDegrees(Math.acos(Math.max(-1.0,
                Math.min(1.0, resting.dot(walking)))));
        assertTrue(between > 10.0,
                "The bore of the weapon does not follow the arm of the player");
    }

    @Test
    @DisplayName("The pose of the aim is not touched by the turn of the weapon that is held")
    void theAimIsNotTouched() {
        // The turn of the weapon that is only held is not part of the chain of the pose of the
        // aim, so the bore of the weapon still points at the place the player looks at.
        Vector3f bore = Rac1BlasterChains.direction(
                Rac1BlasterChains.thirdPerson(0.0, 0.0, 0.0), Rac1BlasterChains.BORE);
        assertEquals(0.0, bore.x, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
        assertEquals(0.0, bore.y, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
        assertEquals(1.0, bore.z, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
    }

    @Test
    @DisplayName("The turn of the weapon that is held is bounded")
    void theTurnIsBounded() {
        assertTrue(Math.abs(Rac1BlasterGrip.IDLE_ROTATION) <= 90.0,
                "The turn of the weapon that is only held is too great");
    }

    /**
     * The chain of the third person of the game of blocks, with the turn of the weapon that is
     * only held.
     *
     * @return the stack of the poses of the game
     */
    private static com.mojang.blaze3d.vertex.PoseStack idleChain() {
        return Rac1BlasterChains.thirdPersonIdle(0.0, RESTING_ARM, 0.0,
                Rac1BlasterGrip.IDLE_ROTATION);
    }
}
