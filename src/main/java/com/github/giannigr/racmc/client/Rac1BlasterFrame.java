package com.github.giannigr.racmc.client;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The frame of the world of the game of blocks.
 *
 * <p>The game of blocks draws the world with a matrix of the camera and a matrix of the
 * projection, and both of them are matrices of the frame that is drawn: the matrix of the camera
 * is the turn of the camera of the player, and the matrix of the projection is the projection of
 * the player with the share of the sprint of the player and the share of the view of the player
 * in it.
 *
 * <p>The matrices are taken from the event of the game of blocks that is fired after the world is
 * drawn, which is fired right before the hand of the player is drawn. The matrices of the frame
 * are therefore the matrices that are used for the arrow of the shot of the Blaster and for the
 * weapon in the hand of the player at the same time.
 */
public final class Rac1BlasterFrame {

    /** The matrix of the projection of the world. */
    private static final Matrix4f PROJECTION = new Matrix4f();

    /** The matrix of the camera of the player. */
    private static final Matrix4f MODEL_VIEW = new Matrix4f();

    /** The place of the camera of the player in the world. */
    private static final Vector3f CAMERA = new Vector3f();

    /** Whether the matrices of a frame of the world are known. */
    private static boolean ready;

    private Rac1BlasterFrame() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Remembers the matrices of the frame of the world that is drawn.
     *
     * @param event the event that is fired after the world is drawn
     */
    public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        PROJECTION.set(event.getProjectionMatrix());
        MODEL_VIEW.set(event.getModelViewMatrix());
        if (event.getCamera() != null) {
            CAMERA.set(event.getCamera().getPosition().x, event.getCamera().getPosition().y,
                    event.getCamera().getPosition().z);
        }
        ready = true;
    }

    /**
     * Whether the matrices of the frame of the world are known.
     *
     * @return {@code true} when the world was drawn
     */
    public static boolean ready() {
        return ready;
    }

    /**
     * The matrix of the projection of the world.
     *
     * @return the matrix
     */
    public static Matrix4f projection() {
        return PROJECTION;
    }

    /**
     * The matrix of the camera of the player.
     *
     * @return the matrix
     */
    public static Matrix4f modelView() {
        return MODEL_VIEW;
    }

    /**
     * The place of the camera of the player in the world.
     *
     * @return the place
     */
    public static Vector3f camera() {
        return CAMERA;
    }
}
