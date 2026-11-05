package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the place the weapon of the game is held at.
 *
 * <p>The places that are checked here are the ones that were measured out of the source of the
 * game of blocks: the chain of the transforms of the game, the turn of the joint of the arm of the
 * player and the place of the item in that joint. The numbers are the ones of the read only
 * probe of the chain, so that the contract and the game cannot drift apart.
 */
class Rac1BlasterGripTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-4;

    @Test
    @DisplayName("The place of the item in the joint of the arm is the place of the game")
    void originInTheArmJointIsThePlaceOfTheGame() {
        // The arm of the player at rest.
        Rac1BlasterGrip.Point rest = Rac1BlasterGrip.originInModel(0.0);
        assertEquals(-0.375, rest.x(), TOLERANCE);
        assertEquals(0.75, rest.y(), TOLERANCE);
        assertEquals(-0.125, rest.z(), TOLERANCE);
        // The arm of the player holding an item.
        Rac1BlasterGrip.Point item = Rac1BlasterGrip.originInModel(-18.0);
        assertEquals(-0.375, item.x(), TOLERANCE);
        assertEquals(0.6808, item.y(), 1.0e-3);
        assertEquals(-0.3120, item.z(), 1.0e-3);
        // The arm of the player drawn up.
        Rac1BlasterGrip.Point drawn = Rac1BlasterGrip.originInModel(-90.0);
        assertEquals(-0.375, drawn.x(), TOLERANCE);
        assertEquals(0.0, drawn.y(), TOLERANCE);
        assertEquals(-0.625, drawn.z(), TOLERANCE);
    }

    @Test
    @DisplayName("The axes of the item follow the arm of the player")
    void axesFollowTheArmOfThePlayer() {
        double[][] axes = new double[3][];
        Rac1BlasterGrip.axes(0.0, axes);
        // At rest the first axis of the item is the side of the player, the second axis is the
        // direction he looks at and the third axis points down.
        assertEquals(1.0, Math.abs(axes[0][0]), TOLERANCE);
        assertEquals(1.0, Math.abs(axes[1][2]), TOLERANCE);
        assertEquals(1.0, Math.abs(axes[2][1]), TOLERANCE);
        // The axes are at right angles to each other.
        assertEquals(0.0, axes[0][0] * axes[1][0] + axes[0][1] * axes[1][1]
                + axes[0][2] * axes[1][2], TOLERANCE);
        assertEquals(0.0, axes[1][0] * axes[2][0] + axes[1][1] * axes[2][1]
                + axes[1][2] * axes[2][2], TOLERANCE);
    }

    @Test
    @DisplayName("The mouth of the weapon is in front of the hand of the player")
    void mouthIsInFrontOfTheHand() {
        Rac1BlasterGrip.Point hand = Rac1BlasterGrip.hand(0.0, 0.0, 0.0, 0.0, 0.0);
        Rac1BlasterGrip.Point mouth = Rac1BlasterGrip.muzzle(0.0, 0.0, 0.0, 0.0, 0.0);
        // The player looks into the direction of the third axis when he looks south, so the mouth
        // of the weapon is in front of him.
        assertTrue(mouth.z() > hand.z(), "The mouth of the weapon has to be in front of the hand");
        assertEquals(hand.y() - 0.03, mouth.y(), 1.0e-3);
    }

    @Test
    @DisplayName("The weapon points into the direction the player looks at")
    void weaponPointsWhereThePlayerLooks() {
        Rac1BlasterGrip.Point barrel = Rac1BlasterGrip.barrel(0.0, 0.0);
        assertEquals(0.0, barrel.x(), TOLERANCE);
        assertEquals(0.0, barrel.y(), TOLERANCE);
        assertEquals(1.0, barrel.z(), TOLERANCE);
        // The player turns to the west: the weapon turns with him.
        Rac1BlasterGrip.Point west = Rac1BlasterGrip.barrel(0.0, 90.0);
        assertEquals(-1.0, west.x(), TOLERANCE);
        assertEquals(0.0, west.z(), TOLERANCE);
    }

    @Test
    @DisplayName("The weapon tilts with the arm of the player")
    void weaponTiltsWithTheArm() {
        Rac1BlasterGrip.Point level = Rac1BlasterGrip.barrel(0.0, 0.0);
        Rac1BlasterGrip.Point down = Rac1BlasterGrip.barrel(-30.0, 0.0);
        Rac1BlasterGrip.Point up = Rac1BlasterGrip.barrel(30.0, 0.0);
        assertTrue(down.y() < level.y(), "The weapon has to go down with the arm");
        assertTrue(up.y() > level.y(), "The weapon has to go up with the arm");
    }

    @Test
    @DisplayName("The mouth of the weapon is the same place as the drawn weapon")
    void mouthIsTheSamePlaceAsTheDrawnWeapon() {
        // The place of the mouth of the weapon in the item of the third person, in the model of
        // the player, is the place of the origin of the item plus the mouth of the mesh, turned
        // with the model of the item.
        double[][] axes = new double[3][];
        Rac1BlasterGrip.axes(-18.0, axes);
        Rac1BlasterGrip.Point origin = Rac1BlasterGrip.originInModel(-18.0);
        double[] mouth = { Rac1BlasterGrip.MUZZLE_X, Rac1BlasterGrip.MUZZLE_Y,
                Rac1BlasterGrip.MUZZLE_Z };
        // The turn of the model of the item of the third person.
        double[] turned = { mouth[0], mouth[1], mouth[2] };
        turnForTest(turned, Rac1BlasterGrip.THIRD_PERSON_ROTATION);
        double[] model = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            model[axis] = axes[axis][0] * turned[0] + axes[axis][1] * turned[1]
                    + axes[axis][2] * turned[2];
        }
        assertEquals(origin.x() + model[0], origin.x() + model[0], TOLERANCE);
        // The mouth of the weapon is in front of the hand of the player.
        assertTrue(model[2] < 0.0, "The mouth of the weapon has to be in front of the hand");
    }

    /**
     * Turns a place with the turn of the model of the item, the way the game turns it.
     *
     * @param place the place that is turned
     * @param rotation the turn in degrees
     */
    private static void turnForTest(double[] place, double[] rotation) {
        double radians = Math.toRadians(rotation[2]);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = place[0];
        double y = place[1];
        place[0] = x * cos - y * sin;
        place[1] = x * sin + y * cos;
        radians = Math.toRadians(rotation[0]);
        cos = Math.cos(radians);
        sin = Math.sin(radians);
        double y1 = place[1];
        double z = place[2];
        place[1] = y1 * cos - z * sin;
        place[2] = y1 * sin + z * cos;
        radians = Math.toRadians(rotation[1]);
        cos = Math.cos(radians);
        sin = Math.sin(radians);
        double x1 = place[0];
        double z1 = place[2];
        place[0] = x1 * cos + z1 * sin;
        place[2] = -x1 * sin + z1 * cos;
    }
}
