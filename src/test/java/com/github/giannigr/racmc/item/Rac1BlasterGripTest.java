package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the place the weapon of the game is held at and for the place its mouth is
 * in the world.
 *
 * <p>The checks replay the chain of the transforms of the game of blocks with the classes of the
 * game itself - the stack of the poses of the game, the turns of Mojang and the matrices of
 * JOML - and compare the place of the mouth of the weapon with the contract of
 * {@link Rac1BlasterGrip}. The two are built apart from each other: the chain follows the source
 * of the game of blocks, of the version 1.21.1 with NeoForge 21.1.265, and the contract follows
 * the frame of the weapon of the game.
 */
class Rac1BlasterGripTest {

    /** The size of the tolerance of the checks. */
    private static final double TOLERANCE = 1.0e-3;

    /** The place of the player in the checks. */
    private static final double PLAYER_X = 0.5;

    /** The place of the player in the checks. */
    private static final double PLAYER_Y = 100.0;

    /** The place of the player in the checks. */
    private static final double PLAYER_Z = 0.5;

    @Test
    @DisplayName("The mouth of the weapon is where the game of blocks draws it")
    void mouthIsWhereTheGameOfBlocksDrawsIt() {
        double[] bodyYaws = { 0.0, 37.0, 90.0, 180.0, -45.0 };
        double[] headYaws = { 0.0, 37.0, 90.0, 180.0, -45.0 };
        double[] pitches = { 0.0, 30.0, -30.0, 60.0 };
        for (double bodyYaw : bodyYaws) {
            for (double headYaw : headYaws) {
                for (double pitch : pitches) {
                    Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(
                            wrapDegrees(headYaw - bodyYaw), pitch, bodyYaw);
                    Rac1BlasterGrip.ArmTurn arm = Rac1BlasterGrip.aim(look);
                    PoseStack chain = chainOfTheGame(bodyYaw, arm);
                    Vector3f drawn = transform(chain, Rac1BlasterGrip.BORE_X, Rac1BlasterGrip.BORE_Y,
                            Rac1BlasterGrip.MUZZLE_Z);
                    Rac1BlasterGrip.Point mouth = Rac1BlasterGrip.muzzle(PLAYER_X, PLAYER_Y, PLAYER_Z,
                            look);
                    assertEquals(drawn.x, mouth.x(), TOLERANCE,
                            "The mouth of the weapon is at the wrong place along the first axis");
                    assertEquals(drawn.y, mouth.y(), TOLERANCE,
                            "The mouth of the weapon is at the wrong place along the second axis");
                    assertEquals(drawn.z, mouth.z(), TOLERANCE,
                            "The mouth of the weapon is at the wrong place along the third axis");
                    // The bore of the weapon points the way the game of blocks draws it.
                    Vector3f drawnTip = transform(chain, Rac1BlasterGrip.BORE_X,
                            Rac1BlasterGrip.BORE_Y, Rac1BlasterGrip.MUZZLE_Z + 0.01);
                    float length = (float) Math.sqrt(
                            (drawnTip.x - drawn.x) * (drawnTip.x - drawn.x)
                                    + (drawnTip.y - drawn.y) * (drawnTip.y - drawn.y)
                                    + (drawnTip.z - drawn.z) * (drawnTip.z - drawn.z));
                    Rac1BlasterGrip.Direction barrel = Rac1BlasterGrip.barrel(look);
                    assertEquals((drawnTip.x - drawn.x) / length, barrel.x(), 1.0e-3,
                            "The bore of the weapon points the wrong way along the first axis");
                    assertEquals((drawnTip.y - drawn.y) / length, barrel.y(), 1.0e-3,
                            "The bore of the weapon points the wrong way along the second axis");
                    assertEquals((drawnTip.z - drawn.z) / length, barrel.z(), 1.0e-3,
                            "The bore of the weapon points the wrong way along the third axis");
                }
            }
        }
    }

    @Test
    @DisplayName("The mouth of the weapon is at the right hand of the player")
    void mouthIsAtTheRightHandOfThePlayer() {
        // The player stands at the origin of the world and looks into the direction of the third
        // axis, which is the direction of the south.
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(0.0, 0.0, 0.0);
        Rac1BlasterGrip.Point mouth = Rac1BlasterGrip.muzzle(0.0, 0.0, 0.0, look);
        // The right hand of the player is at the negative first axis when the player looks into
        // the direction of the third axis.
        assertTrue(mouth.x() < 0.0,
                "The mouth of the weapon has to stand at the right hand of the player");
        // The mouth of the weapon stands in front of the player.
        assertTrue(mouth.z() > 0.0, "The mouth of the weapon has to stand in front of the player");
        // The mouth of the weapon stands at the height of the hand of the player, which is a little
        // below the hips of the player and not at the height of his eyes.
        assertTrue(mouth.y() > 0.5 && mouth.y() < 1.0,
                "The mouth of the weapon has to stand at the height of the hand of the player");
    }

    @Test
    @DisplayName("The bore of the weapon points at the place the player looks at")
    void borePointsWhereThePlayerLooks() {
        double[] headYaws = { 0.0, 37.0, 90.0, 180.0, -45.0 };
        double[] pitches = { 0.0, 30.0, -30.0, 60.0 };
        for (double bodyYaw : headYaws) {
            for (double headYaw : headYaws) {
                for (double pitch : pitches) {
                    Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(
                            wrapDegrees(headYaw - bodyYaw), pitch, bodyYaw);
                    Rac1BlasterGrip.Direction barrel = Rac1BlasterGrip.barrel(look);
                    // The place the player looks at, in the world.
                    double yaw = Math.toRadians(headYaw);
                    double tilt = Math.toRadians(pitch);
                    double x = -Math.sin(yaw) * Math.cos(tilt);
                    double y = -Math.sin(tilt);
                    double z = Math.cos(yaw) * Math.cos(tilt);
                    assertEquals(x, barrel.x(), 1.0e-3,
                            "The bore of the weapon does not follow the turn of the head");
                    assertEquals(y, barrel.y(), 1.0e-3,
                            "The bore of the weapon does not follow the turn of the head");
                    assertEquals(z, barrel.z(), 1.0e-3,
                            "The bore of the weapon does not follow the turn of the head");
                }
            }
        }
    }

    @Test
    @DisplayName("The bore of the weapon is the one of the game")
    void boreIsTheOneOfTheGame() {
        // The axis of the bore of the weapon is the line of the joints 13 to 16 of the skeleton of
        // the class 168 of the game, and the mouth of the barrel is the ring of the mesh of the
        // game at the same place.
        assertEquals(0.2305, Rac1BlasterGrip.BORE_X, 1.0e-6);
        assertEquals(-0.0300, Rac1BlasterGrip.BORE_Y, 1.0e-6);
        assertEquals(0.6335, Rac1BlasterGrip.MUZZLE_Z, 1.0e-6);
    }

    @Test
    @DisplayName("The socket of the weapon is at the hand of the player")
    void socketIsAtTheHandOfThePlayer() {
        // The arm of the player hangs at the side of his body and the item of the game of blocks
        // is held in the hand at the end of the arm.
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(0.0, 0.0, 0.0);
        Rac1BlasterGrip.Point socket = Rac1BlasterGrip.grip(0.0, 0.0, 0.0, look);
        assertTrue(socket.y() > 0.5 && socket.y() < 1.0,
                "The socket of the weapon has to stand at the height of the hand of the player");
        assertTrue(socket.z() > 0.0, "The socket of the weapon has to stand in front of the player");
        assertTrue(socket.x() < 0.0, "The socket of the weapon has to stand at the right hand");
    }

    /**
     * The chain of the transforms of the game of blocks, replayed with the classes of the game.
     *
     * <p>The chain is the chain of the version 1.21.1 with NeoForge 21.1.265: the renderer of the
     * living entities turns the model of the player around the vertical axis by the turn of the
     * body of the player, turns it around the vertical axis by a half a turn and turns it upside
     * down, the layer of the item in the hand moves the joint of the arm of the player and turns
     * it, and the model of the item turns the mesh of the weapon.
     *
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @param arm the turns of the arm of the player
     * @return the stack of the poses of the game
     */
    private static PoseStack chainOfTheGame(double bodyYaw, Rac1BlasterGrip.ArmTurn arm) {
        PoseStack chain = new PoseStack();
        chain.setIdentity();
        chain.translate(PLAYER_X, PLAYER_Y, PLAYER_Z);
        // The renderer of the living entities.
        chain.mulPose(Axis.YP.rotationDegrees((float) (180.0 - bodyYaw)));
        chain.scale(-1.0f, -1.0f, 1.0f);
        chain.translate(0.0f, -1.501f, 0.0f);
        // The layer of the item in the hand: the joint of the arm of the player.
        chain.translate((float) Rac1BlasterGrip.ARM_JOINT_X, (float) Rac1BlasterGrip.ARM_JOINT_Y,
                (float) Rac1BlasterGrip.ARM_JOINT_Z);
        chain.mulPose(Axis.XP.rotationDegrees((float) arm.x()));
        chain.mulPose(Axis.YP.rotationDegrees((float) arm.y()));
        chain.mulPose(Axis.ZP.rotationDegrees(0.0f));
        chain.mulPose(Axis.XP.rotationDegrees(-90.0f));
        chain.mulPose(Axis.YP.rotationDegrees(180.0f));
        chain.translate((float) Rac1BlasterGrip.ITEM_IN_JOINT_X,
                (float) Rac1BlasterGrip.ITEM_IN_JOINT_Y,
                (float) Rac1BlasterGrip.ITEM_IN_JOINT_Z);
        // The model of the item, and the move of the cube of the item that the game of blocks
        // makes and that the renderer of the Blaster takes back.
        chain.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(Rac1BlasterGrip.THIRD_PERSON_ROTATION[0]), 0.0f, 0.0f));
        chain.translate(-0.5f, -0.5f, -0.5f);
        chain.translate(0.5f, 0.5f, 0.5f);
        return chain;
    }

    /**
     * Moves a place of the mesh of the weapon with the stack of the poses of the game.
     *
     * @param chain the stack of the poses of the game
     * @param x the place along the first axis
     * @param y the place along the second axis
     * @param z the place along the third axis
     * @return the place in the world
     */
    private static Vector3f transform(PoseStack chain, double x, double y, double z) {
        Vector4f place = new Vector4f((float) x, (float) y, (float) z, 1.0f).mul(chain.last().pose());
        return new Vector3f(place.x, place.y, place.z);
    }

    /**
     * Brings a turn into the turn of the game of blocks.
     *
     * @param degrees the turn in degrees
     * @return the turn in degrees
     */
    private static double wrapDegrees(double degrees) {
        double turned = degrees % 360.0;
        if (turned >= 180.0) {
            turned -= 360.0;
        }
        if (turned < -180.0) {
            turned += 360.0;
        }
        return turned;
    }
}
