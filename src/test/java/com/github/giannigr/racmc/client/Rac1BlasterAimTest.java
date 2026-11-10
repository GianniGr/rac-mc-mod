package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterGrip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aim of the Blaster in the first person.
 *
 * <p>The chains of the game of blocks are replayed with the classes of the game, the same way the
 * other tests of the Blaster replay them: the hand of the player, the turn of the model of the
 * item and the cube of the item of the game of blocks.
 *
 * <p>The rule that these checks measure is the rule that brings the mouth of the drawn weapon to
 * the place of the screen where the mouth of the weapon in the world is drawn. The rule is not
 * applied to the picture of the weapon: the mouth of the weapon in the world is at the height of
 * the eyes of the player, so the weapon of the game would float over the crosshair of the screen.
 * The owner of the project approved the weapon of the game in the lower right of the screen. The
 * rule and its measures are kept, so that the rule can be turned on again.
 */
class Rac1BlasterAimTest {

    /** The place of the eye of the player above the place of the player. */
    private static final double EYE = 1.62;

    /** The share of the place of the item of the game of blocks. */
    private static final double FAR = 1024.0;

    /** The width of the screen of the game, in pixels. */
    private static final double WIDTH = 1264.0;

    /** The height of the screen of the game, in pixels. */
    private static final double HEIGHT = 681.0;

    /** The field of view of the hand of the game of blocks, in degrees. */
    private static final double HAND_FOV = 70.0;

    /** The measure of the difference of two places, in blocks. */
    private static final double TOLERANCE = 1.0e-3;

    /**
     * The matrix of the camera of the player.
     *
     * @param yaw the turn of the head of the player around the vertical axis, in degrees
     * @param pitch the turn of the head of the player around the side axis, in degrees
     * @return the matrix, that brings a place of the world into the space of the camera
     */
    private static Matrix4f camera(double yaw, double pitch) {
        double turn = Math.toRadians(yaw);
        double tilt = Math.toRadians(pitch);
        double[] right = { -Math.cos(turn), 0.0, -Math.sin(turn) };
        double[] up = { -Math.sin(turn) * Math.sin(tilt), Math.cos(tilt),
                Math.cos(turn) * Math.sin(tilt) };
        double[] back = { Math.sin(turn) * Math.cos(tilt), Math.sin(tilt),
                -Math.cos(turn) * Math.cos(tilt) };
        Matrix4f matrix = new Matrix4f();
        matrix.m00((float) right[0]);
        matrix.m01((float) right[1]);
        matrix.m02((float) right[2]);
        matrix.m10((float) up[0]);
        matrix.m11((float) up[1]);
        matrix.m12((float) up[2]);
        matrix.m20((float) back[0]);
        matrix.m21((float) back[1]);
        matrix.m22((float) back[2]);
        return matrix;
    }

    /**
     * The matrix of the projection of the game of blocks.
     *
     * @param fov the field of view, in degrees
     * @return the matrix
     */
    private static Matrix4f projection(double fov) {
        return new Matrix4f().perspective((float) Math.toRadians(fov),
                (float) (WIDTH / HEIGHT), 0.05f, (float) FAR);
    }

    /**
     * The chain of the poses of the game of blocks in the first person.
     *
     * <p>The chain is the chain of the game of blocks without the share of the equip of the hand:
     * the share of the equip is left out on purpose, so that the weapon of the game does not move
     * while it is fired.
     *
     * @param modelView the matrix of the camera of the player
     * @return the chain, up to the mesh of the weapon
     */
    private static Matrix4f chain(Matrix4f modelView) {
        PoseStack stack = new PoseStack();
        stack.setIdentity();
        // The place of the hand of the player.
        stack.translate((float) Rac1BlasterGrip.FIRST_PERSON_HAND[0],
                (float) Rac1BlasterGrip.FIRST_PERSON_HAND[1],
                (float) Rac1BlasterGrip.FIRST_PERSON_HAND[2]);
        // The turn of the model of the item.
        ItemTransform transform = new ItemTransform(
                new Vector3f((float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[0],
                        (float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[1],
                        (float) Rac1BlasterGrip.FIRST_PERSON_ROTATION[2]),
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f((float) Rac1BlasterGrip.FIRST_PERSON_SCALE,
                        (float) Rac1BlasterGrip.FIRST_PERSON_SCALE,
                        (float) Rac1BlasterGrip.FIRST_PERSON_SCALE));
        transform.apply(false, stack);
        // The cube of the item of the game of blocks, and the move back of the renderer.
        stack.translate(-0.5f, -0.5f, -0.5f);
        stack.translate(0.5f, 0.5f, 0.5f);
        // The pose of the game of blocks holds the turn of the camera of the player.
        return new Matrix4f(modelView).invert().mul(stack.last().pose());
    }

    /**
     * The place of the mouth of the weapon in the world, in the space of the camera.
     *
     * @param modelView the matrix of the camera of the player
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @param headYaw the turn of the head of the player around the vertical axis, in degrees
     * @param pitch the turn of the head of the player around the side axis, in degrees
     * @return the place
     */
    private static Vector3f worldMuzzle(Matrix4f modelView, double bodyYaw, double headYaw,
            double pitch) {
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(headYaw - bodyYaw, pitch, bodyYaw);
        Rac1BlasterGrip.Point mouth = Rac1BlasterGrip.muzzle(0.0, 0.0, 0.0, look);
        Vector4f place = new Vector4f((float) mouth.x(), (float) (mouth.y() - EYE),
                (float) mouth.z(), 1.0f).mul(modelView);
        return new Vector3f(place.x, place.y, place.z);
    }

    /**
     * The place of the mouth of the drawn weapon in the space of the camera.
     *
     * @param chain the chain of the poses of the game of blocks
     * @param modelView the matrix of the camera of the player
     * @return the place
     */
    private static Vector3f drawnMuzzle(Matrix4f chain, Matrix4f modelView) {
        Vector4f place = new Vector4f(Rac1BlasterAim.MOUTH.x, Rac1BlasterAim.MOUTH.y,
                Rac1BlasterAim.MOUTH.z, 1.0f).mul(new Matrix4f(modelView).mul(chain));
        return new Vector3f(place.x, place.y, place.z);
    }

    @Test
    @DisplayName("The mouth of the drawn weapon stands in the column of the mouth in the world")
    void theDrawnMuzzleIsAtTheWorldMuzzle() {
        for (double worldFov : new double[] { 70.0, 77.0, 110.0 }) {
            for (double yaw : new double[] { 0.0, 30.0, -45.0 }) {
                for (double pitch : new double[] { 0.0, -20.0, 15.0 }) {
                    Matrix4f modelView = camera(yaw, pitch);
                    Matrix4f worldProjection = projection(worldFov);
                    Matrix4f handProjection = projection(HAND_FOV);
                    Matrix4f chain = chain(modelView);
                    Vector3f world = worldMuzzle(modelView, 0.0, yaw, pitch);
                    Vector2f wanted = Rac1BlasterAim.screen(worldProjection, world);
                    Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection,
                            handProjection, world);
                    Matrix4f moved = new Matrix4f(chain).translate(move);
                    Vector2f drawn = Rac1BlasterAim.screen(handProjection,
                            drawnMuzzle(moved, modelView));
                    assertEquals(wanted.x, drawn.x, 1.0e-4,
                            "The mouth of the drawn weapon is not in the column of the mouth of "
                                    + "the weapon in the world");
                }
            }
        }
    }

    @Test
    @DisplayName("The mouth of the drawn weapon stays below the crosshair of the screen")
    void theDrawnMuzzleStaysBelowTheCrosshair() {
        double room = (Rac1BlasterGrip.MESH_MAX_X - Rac1BlasterGrip.BORE_X)
                * Rac1BlasterGrip.FIRST_PERSON_SCALE + Rac1BlasterAim.CLEARANCE;
        for (double worldFov : new double[] { 70.0, 77.0, 110.0 }) {
            for (double yaw : new double[] { 0.0, 30.0, -45.0 }) {
                for (double pitch : new double[] { 0.0, -20.0, 15.0 }) {
                    Matrix4f modelView = camera(yaw, pitch);
                    Matrix4f worldProjection = projection(worldFov);
                    Matrix4f handProjection = projection(HAND_FOV);
                    Matrix4f chain = chain(modelView);
                    Vector3f world = worldMuzzle(modelView, 0.0, yaw, pitch);
                    Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection,
                            handProjection, world);
                    Matrix4f moved = new Matrix4f(chain).translate(move);
                    Vector3f drawn = drawnMuzzle(moved, modelView);
                    assertEquals(-room, drawn.y, 1.0e-3,
                            "The mouth of the drawn weapon stands over the crosshair of the screen");
                }
            }
        }
    }

    @Test
    @DisplayName("The move of the mesh of the weapon does not turn the weapon")
    void theMoveDoesNotTurnTheWeapon() {
        Matrix4f modelView = camera(0.0, 0.0);
        Matrix4f worldProjection = projection(70.0);
        Matrix4f handProjection = projection(HAND_FOV);
        Matrix4f chain = chain(modelView);
        Vector3f world = worldMuzzle(modelView, 0.0, 0.0, 0.0);
        Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection, handProjection,
                world);
        Vector3f bore = Rac1BlasterAim.direction(chain, modelView, Rac1BlasterAim.BORE);
        Vector3f moved = Rac1BlasterAim.direction(new Matrix4f(chain).translate(move), modelView,
                Rac1BlasterAim.BORE);
        assertEquals(bore.x, moved.x, TOLERANCE, "The weapon of the game was turned");
        assertEquals(bore.y, moved.y, TOLERANCE, "The weapon of the game was turned");
        assertEquals(bore.z, moved.z, TOLERANCE, "The weapon of the game was turned");
    }

    @Test
    @DisplayName("The depth of the mouth of the drawn weapon is the depth of the game of blocks")
    void theDepthIsKept() {
        Matrix4f modelView = camera(0.0, 0.0);
        Matrix4f worldProjection = projection(110.0);
        Matrix4f handProjection = projection(HAND_FOV);
        Matrix4f chain = chain(modelView);
        Vector3f world = worldMuzzle(modelView, 0.0, 0.0, 0.0);
        Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection, handProjection,
                world);
        Vector3f before = drawnMuzzle(chain, modelView);
        Vector3f after = drawnMuzzle(new Matrix4f(chain).translate(move), modelView);
        assertEquals(before.z, after.z, TOLERANCE,
                "The weapon of the game was moved in the depth of the screen");
    }

    @Test
    @DisplayName("The picture of the weapon is on the screen while it is only held")
    void thePictureIsOnTheScreen() {
        Matrix4f modelView = camera(0.0, 0.0);
        Matrix4f worldProjection = projection(70.0);
        Matrix4f handProjection = projection(HAND_FOV);
        Matrix4f chain = chain(modelView);
        Vector3f world = worldMuzzle(modelView, 0.0, 0.0, 0.0);
        Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection, handProjection,
                world);
        Matrix4f moved = new Matrix4f(chain).translate(move);
        double[] min = { 2.0, 2.0 };
        double[] max = { -2.0, -2.0 };
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    double[] mesh = { x == 0 ? Rac1BlasterGrip.MESH_MIN_X : Rac1BlasterGrip.MESH_MAX_X,
                            y == 0 ? Rac1BlasterGrip.MESH_MIN_Y : Rac1BlasterGrip.MESH_MAX_Y,
                            z == 0 ? Rac1BlasterGrip.MESH_MIN_Z : Rac1BlasterGrip.MESH_MAX_Z };
                    Vector4f place = new Vector4f((float) mesh[0], (float) mesh[1], (float) mesh[2],
                            1.0f).mul(new Matrix4f(modelView).mul(moved));
                    Vector2f screen = Rac1BlasterAim.screen(handProjection,
                            new Vector3f(place.x, place.y, place.z));
                    min[0] = Math.min(min[0], screen.x);
                    max[0] = Math.max(max[0], screen.x);
                    min[1] = Math.min(min[1], screen.y);
                    max[1] = Math.max(max[1], screen.y);
                }
            }
        }
        System.out.printf("the picture of the weapon is on the screen from (%6.3f,%6.3f) to "
                + "(%6.3f,%6.3f)%n", min[0], min[1], max[0], max[1]);
        assertTrue(max[0] < 1.0 && min[0] > -1.0 && max[1] < 1.0 && min[1] > -1.0,
                "The picture of the weapon runs out of the screen");
        assertTrue(min[0] > 0.05 || max[1] < -0.05,
                "The picture of the weapon covers the crosshair of the screen");
    }

    @Test
    @DisplayName("The bore of the drawn weapon points at the place the player looks at")
    void theBorePointsAtTheLook() {
        Matrix4f modelView = camera(0.0, 0.0);
        Matrix4f worldProjection = projection(70.0);
        Matrix4f handProjection = projection(HAND_FOV);
        Matrix4f chain = chain(modelView);
        Vector3f world = worldMuzzle(modelView, 0.0, 0.0, 0.0);
        Vector3f move = Rac1BlasterAim.align(chain, modelView, worldProjection, handProjection,
                world);
        Vector3f bore = Rac1BlasterAim.direction(new Matrix4f(chain).translate(move), modelView,
                Rac1BlasterAim.BORE);
        System.out.printf("the bore of the drawn weapon points at (%6.3f,%6.3f,%6.3f)%n", bore.x,
                bore.y, bore.z);
        assertTrue(bore.z < -0.9,
                "The bore of the drawn weapon does not point at the place the player looks at");
    }
}
