package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the weapon of the game in the third person.
 *
 * <p>The game of blocks holds an item in the hand with the arm of the player hanging at the side
 * of his body, and the weapon of the game points down along the arm of the player while he only
 * holds it. The arm of the player is raised to aim while the weapon is fired, and the bore of
 * the weapon points at the place the player looks at.
 */
class Rac1BlasterIdleTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-3;

    /**
     * The turn of the arm of the player while the game of blocks holds an item and the player
     * stands still, in degrees.
     */
    private static final double RESTING_ARM = -18.0;

    /**
     * The turn of the arm of the player that is raised to aim, in degrees.
     */
    private static final double AIMING_ARM = -90.0;

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
                Rac1BlasterChains.thirdPerson(0.0, 40.0, 0.0), Rac1BlasterChains.BORE);
        double between = Math.toDegrees(Math.acos(Math.max(-1.0,
                Math.min(1.0, resting.dot(walking)))));
        assertTrue(between > 10.0,
                "The bore of the weapon does not follow the arm of the player");
    }

    @Test
    @DisplayName("The bore of the weapon points at the place the player looks at while he fires")
    void theBorePointsAtThePlaceThePlayerLooksAt() {
        Vector3f bore = Rac1BlasterChains.direction(aimChain(), Rac1BlasterChains.BORE);
        assertEquals(0.0, bore.x, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
        assertEquals(0.0, bore.y, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
        assertEquals(1.0, bore.z, TOLERANCE,
                "The bore of the weapon does not point at the place the player looks at");
    }

    @Test
    @DisplayName("The arm of the player is raised to aim")
    void theArmIsRaisedToAim() {
        // The arm of the player is raised by a quarter of a turn while the weapon is fired, so
        // that the arm of the player points forward and the weapon of the player is in the hand
        // of the arm.
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(0.0, 0.0, 0.0);
        Rac1BlasterGrip.ArmTurn turn = Rac1BlasterGrip.aim(look);
        assertEquals(AIMING_ARM, turn.x(), TOLERANCE,
                "The arm of the player is not raised to aim");
        // The head of the player is followed by the arm of the player.
        Rac1BlasterGrip.Look tilted = new Rac1BlasterGrip.Look(0.0, 20.0, 0.0);
        assertEquals(AIMING_ARM + 20.0, Rac1BlasterGrip.aim(tilted).x(), TOLERANCE,
                "The arm of the player does not follow the head of the player");
    }

    @Test
    @DisplayName("The mouth of the weapon is at the place the shot leaves from")
    void theMouthIsAtThePlaceTheShotLeavesFrom() {
        // The mouth of the weapon that the server shoots from is the mouth of the weapon of the
        // arm of the player that is raised to aim, and it must be the mouth of the weapon that is
        // drawn.
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(0.0, 0.0, 0.0);
        Rac1BlasterGrip.ArmTurn turn = Rac1BlasterGrip.aim(look);
        Vector3f drawn = Rac1BlasterChains.place(
                Rac1BlasterChains.thirdPerson(0.0, turn.x(), turn.y()), Rac1BlasterChains.MOUTH);
        Rac1BlasterGrip.Point mouth = Rac1BlasterGrip.muzzle(0.0, 0.0, 0.0, look);
        assertEquals(drawn.x, mouth.x(), TOLERANCE,
                "The mouth of the weapon is not at the place the shot leaves from");
        assertEquals(drawn.y, mouth.y(), TOLERANCE,
                "The mouth of the weapon is not at the place the shot leaves from");
        assertEquals(drawn.z, mouth.z(), TOLERANCE,
                "The mouth of the weapon is not at the place the shot leaves from");
    }

    /**
     * The chain of the third person of the game of blocks, with the arm of the player hanging at
     * the side of his body.
     *
     * @return the stack of the poses of the game
     */
    private static com.mojang.blaze3d.vertex.PoseStack idleChain() {
        return Rac1BlasterChains.thirdPerson(0.0, RESTING_ARM, 0.0);
    }

    /**
     * The chain of the third person of the game of blocks, with the arm of the player raised to
     * aim.
     *
     * @return the stack of the poses of the game
     */
    private static com.mojang.blaze3d.vertex.PoseStack aimChain() {
        return Rac1BlasterChains.thirdPerson(0.0, AIMING_ARM, 0.0);
    }
}
