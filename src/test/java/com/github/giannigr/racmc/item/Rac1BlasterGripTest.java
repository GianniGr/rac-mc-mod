package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the place the weapon of the game is held at, for the place its mouth is in
 * the world and for the way the weapon stands in the hand of the player.
 *
 * <p>The checks replay the chain of the transforms of the game of blocks with the classes of the
 * game itself - the stack of the poses of the game, the joint of the arm of the model of the
 * player and the turn of the model of an item - and compare the weapon of the game with the
 * contract of {@link Rac1BlasterGrip}. The chain follows the source of the game of blocks, of the
 * version 1.21.1 with NeoForge 21.1.265, and the contract follows the frame of the weapon of the
 * game, so the two are built apart from each other.
 *
 * <p>The frame of the mesh of the weapon was measured out of the real asset of the game: the first
 * axis of the mesh runs up and the third axis of the mesh runs along the bore.
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

    /** The first axis of the mesh of the weapon, the axis that runs up. */
    private static final Vector3f MESH_UP = new Vector3f(1.0f, 0.0f, 0.0f);

    /** The third axis of the mesh of the weapon, the axis that runs along the bore. */
    private static final Vector3f MESH_BORE = new Vector3f(0.0f, 0.0f, 1.0f);

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
                    PoseStack chain = thirdPersonChain(bodyYaw, arm);
                    Vector3f drawn = place(chain, Rac1BlasterGrip.BORE_X, Rac1BlasterGrip.BORE_Y,
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
                    Vector3f drawnBore = direction(chain, MESH_BORE);
                    Rac1BlasterGrip.Direction barrel = Rac1BlasterGrip.barrel(look);
                    assertEquals(drawnBore.x, barrel.x(), TOLERANCE,
                            "The bore of the weapon points the wrong way along the first axis");
                    assertEquals(drawnBore.y, barrel.y(), TOLERANCE,
                            "The bore of the weapon points the wrong way along the second axis");
                    assertEquals(drawnBore.z, barrel.z(), TOLERANCE,
                            "The bore of the weapon points the wrong way along the third axis");
                }
            }
        }
    }

    @Test
    @DisplayName("The weapon stands upright in the third person")
    void weaponStandsUprightInTheThirdPerson() {
        // The turn of the head and the turn of the head around the side axis are both away from
        // the zero, because the weapon stands upright only if the two turns are held together.
        double[][] looks = { { 0.0, 40.0, 25.0 }, { 37.0, 40.0, 25.0 }, { 37.0, 90.0, -30.0 },
                { -45.0, -40.0, 60.0 }, { 90.0, 0.0, 25.0 } };
        for (double[] look : looks) {
            double bodyYaw = look[0];
            double headYaw = look[1];
            double pitch = look[2];
            Rac1BlasterGrip.Look eyes = new Rac1BlasterGrip.Look(
                    wrapDegrees(headYaw - bodyYaw), pitch, bodyYaw);
            PoseStack chain = thirdPersonChain(bodyYaw, Rac1BlasterGrip.aim(eyes));
            // The up axis of the weapon has to stand up in the world, the way the up axis of the
            // frame of the look of the player stands up.
            Vector3f up = direction(chain, MESH_UP);
            double yaw = Math.toRadians(headYaw);
            double tilt = Math.toRadians(pitch);
            assertEquals((float) (-Math.sin(tilt) * Math.sin(yaw)), up.x(), TOLERANCE,
                    "The weapon of the game does not stand up the right way");
            assertEquals((float) Math.cos(tilt), up.y(), TOLERANCE,
                    "The weapon of the game does not stand up the right way");
            assertEquals((float) (Math.sin(tilt) * Math.cos(yaw)), up.z(), TOLERANCE,
                    "The weapon of the game does not stand up the right way");
            // The bore of the weapon has to point at the place the player looks at.
            Vector3f bore = direction(chain, MESH_BORE);
            assertEquals((float) (-Math.sin(yaw) * Math.cos(tilt)), bore.x(), TOLERANCE,
                    "The bore of the weapon does not point at the place the player looks at");
            assertEquals((float) (-Math.sin(tilt)), bore.y(), TOLERANCE,
                    "The bore of the weapon does not point at the place the player looks at");
            assertEquals((float) (Math.cos(yaw) * Math.cos(tilt)), bore.z(), TOLERANCE,
                    "The bore of the weapon does not point at the place the player looks at");
            // The up axis of the weapon and the bore of the weapon stand across each other.
            assertEquals(0.0, up.dot(bore), TOLERANCE,
                    "The weapon of the game stands twisted around its bore");
        }
    }

    @Test
    @DisplayName("The weapon stands upright in the first person")
    void weaponStandsUprightInTheFirstPerson() {
        PoseStack chain = firstPersonChain();
        // The bore of the weapon has to point away from the eye of the player, into the screen.
        Vector3f bore = direction(chain, MESH_BORE);
        assertEquals(0.0, bore.x(), TOLERANCE, "The bore of the weapon does not point into the screen");
        assertEquals(0.0, bore.y(), TOLERANCE, "The bore of the weapon does not point into the screen");
        assertEquals(-1.0, bore.z(), TOLERANCE, "The bore of the weapon does not point into the screen");
        // The up axis of the weapon has to point up, into the screen.
        Vector3f up = direction(chain, MESH_UP);
        assertEquals(0.0, up.x(), TOLERANCE, "The weapon of the game does not stand up in the hand");
        assertEquals(1.0, up.y(), TOLERANCE, "The weapon of the game does not stand up in the hand");
        assertEquals(0.0, up.z(), TOLERANCE, "The weapon of the game does not stand up in the hand");
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
        // The mouth of the weapon stands at the height of the eyes of the player, because the arm
        // of the player is raised to aim while the weapon is fired.
        assertTrue(mouth.y() > 1.4 && mouth.y() < 1.9,
                "The mouth of the weapon has to stand at the height of the eyes of the player");
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
        // The arm of the player is raised to aim while the weapon is fired, and the item of the
        // game of blocks is held in the hand at the end of the arm.
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(0.0, 0.0, 0.0);
        Rac1BlasterGrip.Point socket = Rac1BlasterGrip.grip(0.0, 0.0, 0.0, look);
        assertTrue(socket.y() > 1.4 && socket.y() < 1.9,
                "The socket of the weapon has to stand at the height of the eyes of the player");
        assertTrue(socket.z() > 0.0, "The socket of the weapon has to stand in front of the player");
        assertTrue(socket.x() < 0.0, "The socket of the weapon has to stand at the right hand");
    }

    /**
     * The chain of the transforms of the game of blocks in the third person, replayed with the
     * classes of the game.
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
    private static PoseStack thirdPersonChain(double bodyYaw, Rac1BlasterGrip.ArmTurn arm) {
        // The joint of the arm of the model of the player.
        ModelPart joint = new ModelPart(List.of(), Map.of());
        joint.setPos((float) (Rac1BlasterGrip.ARM_JOINT_X * 16.0),
                (float) (Rac1BlasterGrip.ARM_JOINT_Y * 16.0),
                (float) (Rac1BlasterGrip.ARM_JOINT_Z * 16.0));
        joint.setRotation((float) Math.toRadians(arm.x()), (float) Math.toRadians(arm.y()), 0.0f);

        PoseStack chain = new PoseStack();
        chain.setIdentity();
        chain.translate(PLAYER_X, PLAYER_Y, PLAYER_Z);
        // The renderer of the living entities.
        chain.mulPose(Axis.YP.rotationDegrees((float) (180.0 - bodyYaw)));
        chain.scale(-1.0f, -1.0f, 1.0f);
        chain.translate(0.0f, -1.501f, 0.0f);
        // The layer of the item in the hand: the joint of the arm of the player.
        joint.translateAndRotate(chain);
        chain.mulPose(Axis.XP.rotationDegrees(-90.0f));
        chain.mulPose(Axis.YP.rotationDegrees(180.0f));
        chain.translate((float) Rac1BlasterGrip.ITEM_IN_JOINT_X,
                (float) Rac1BlasterGrip.ITEM_IN_JOINT_Y,
                (float) Rac1BlasterGrip.ITEM_IN_JOINT_Z);
        // The model of the item, and the move of the cube of the item that the game of blocks
        // makes and that the renderer of the Blaster takes back.
        ItemTransform transform = new ItemTransform(
                new Vector3f((float) Rac1BlasterGrip.THIRD_PERSON_ROTATION[0],
                        (float) Rac1BlasterGrip.THIRD_PERSON_ROTATION[1],
                        (float) Rac1BlasterGrip.THIRD_PERSON_ROTATION[2]),
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(1.0f, 1.0f, 1.0f));
        transform.apply(false, chain);
        chain.translate(-0.5f, -0.5f, -0.5f);
        chain.translate(0.5f, 0.5f, 0.5f);
        return chain;
    }

    /**
     * The chain of the transforms of the game of blocks in the first person, replayed with the
     * classes of the game.
     *
     * @return the stack of the poses of the game
     */
    private static PoseStack firstPersonChain() {
        PoseStack chain = new PoseStack();
        chain.setIdentity();
        // The renderer of the item in the hand of the first person.
        chain.translate(0.56, -0.52, -0.72);
        ItemTransform transform = new ItemTransform(
                new Vector3f((float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[0],
                        (float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[1],
                        (float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[2]),
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.55f, 0.55f, 0.55f));
        transform.apply(false, chain);
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
    private static Vector3f place(PoseStack chain, double x, double y, double z) {
        Vector4f place = new Vector4f((float) x, (float) y, (float) z, 1.0f).mul(chain.last().pose());
        return new Vector3f(place.x, place.y, place.z);
    }

    /**
     * Moves a direction of the mesh of the weapon with the stack of the poses of the game.
     *
     * @param chain the stack of the poses of the game
     * @param axis the axis of the mesh of the weapon
     * @return the direction in the world
     */
    private static Vector3f direction(PoseStack chain, Vector3f axis) {
        Vector4f direction = new Vector4f(axis, 0.0f).mul(chain.last().pose());
        Vector3f result = new Vector3f(direction.x, direction.y, direction.z);
        return result.normalize();
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
