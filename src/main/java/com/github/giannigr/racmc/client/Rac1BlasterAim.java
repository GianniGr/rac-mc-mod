package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterGrip;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The aim of the Blaster in the first person.
 *
 * <p>The game of blocks draws the weapon in the hand of the player with a projection of its own.
 * The projection of the hand is a projection of seventy degrees, and the projection of the world
 * is the projection of the player, with the share of the sprint of the player and the share of
 * the view of the player in it. The shot of the weapon leaves the mouth of the weapon in the
 * world, and the arrow of the shot is drawn with the projection of the world, so the mouth of the
 * drawn weapon and the mouth of the weapon in the world are two places of the world that are not
 * the same place on the screen.
 *
 * <p>The mouth of the drawn weapon is therefore moved, so that it is drawn at the place of the
 * screen where the mouth of the weapon in the world is drawn. The move is made with the matrices
 * of the game of blocks themselves: the matrix of the camera, the matrix of the projection of the
 * world and the matrix of the projection of the hand. The move is therefore right for every
 * field of view of the game, and it is right for every share of the sprint of the player, because
 * the matrices are the matrices of the frame that is drawn.
 *
 * <p>The move is a move of the mesh of the weapon and not a turn of it: the weapon keeps its
 * place in the hand of the player, it keeps its size and it keeps standing up. The depth of the
 * mouth of the weapon is the depth the game of blocks gives it, so that the size of the weapon on
 * the screen and the perspective of the weapon do not change.
 */
public final class Rac1BlasterAim {

    /** The place of the mouth of the weapon in the mesh of the weapon. */
    public static final Vector3f MOUTH = new Vector3f((float) Rac1BlasterGrip.BORE_X,
            (float) Rac1BlasterGrip.BORE_Y, (float) Rac1BlasterGrip.MUZZLE_Z);

    /** The axis of the bore of the weapon in the mesh of the weapon. */
    public static final Vector3f BORE = new Vector3f(0.0f, 0.0f, 1.0f);

    /**
     * The place of the mouth of the drawn weapon below the eye of the player, in blocks.
     *
     * <p>The mouth of the drawn weapon may not stand at the height of the crosshair of the
     * screen: the crosshair of the screen has to stay clear, and the weapon of the game has to
     * stay in the lower right of the screen. The mouth of the weapon is therefore kept below the
     * crosshair, and the weapon is kept as low as the place of the mouth allows it to be.
     */
    public static final double CLEARANCE = 0.05;

    private Rac1BlasterAim() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The place of a point of the space of the camera on the screen.
     *
     * @param projection the matrix of the projection of the game of blocks
     * @param camera the place of the point in the space of the camera
     * @return the place on the screen, from {@code -1} to {@code 1} on both axes
     */
    public static Vector2f screen(Matrix4f projection, Vector3f camera) {
        Vector4f clip = new Vector4f(camera.x, camera.y, camera.z, 1.0f).mul(projection);
        float w = clip.w == 0.0f ? 1.0f : clip.w;
        return new Vector2f(clip.x / w, clip.y / w);
    }

    /**
     * The place of a point of the space of the camera at a given depth.
     *
     * <p>The place is the place of the ray that goes from the eye of the player through the place
     * on the screen, at the depth that is asked for.
     *
     * @param projection the matrix of the projection of the game of blocks
     * @param screen the place on the screen, from {@code -1} to {@code 1} on both axes
     * @param depth the depth of the place, in blocks
     * @return the place in the space of the camera
     */
    public static Vector3f atDepth(Matrix4f projection, Vector2f screen, float depth) {
        Vector4f near = new Vector4f(screen.x, screen.y, -1.0f, 1.0f)
                .mul(new Matrix4f(projection).invert());
        if (near.w == 0.0f) {
            return new Vector3f(0.0f, 0.0f, -depth);
        }
        Vector3f direction = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        if (direction.z == 0.0f) {
            return new Vector3f(0.0f, 0.0f, -depth);
        }
        return direction.mul(-depth / direction.z);
    }

    /**
     * The move of the mesh of the weapon that puts the mouth of the weapon at the place of the
     * mouth of the weapon in the world.
     *
     * <p>The mouth of the weapon in the world is drawn with the projection of the world, and the
     * mouth of the drawn weapon is drawn with the projection of the hand. The place of the mouth
     * of the weapon in the world is brought to the projection of the hand, and the difference of
     * the two places is brought back into the frame of the mesh of the weapon.
     *
     * <p>The move is limited in the height: the mouth of the drawn weapon may not stand at the
     * height of the crosshair of the screen, because the crosshair has to stay clear and the
     * weapon of the game has to stay in the lower right of the screen. The mouth of the weapon in
     * the world is at the height of the eyes of the player, and the weapon of the game cannot be
     * drawn there without standing over the crosshair. The move in the width is not limited: the
     * mouth of the drawn weapon stands in the column of the mouth of the weapon in the world, so
     * that the shot of the weapon stands over the mouth of the drawn weapon.
     *
     * @param chain the chain of the poses of the game of blocks, up to the mesh of the weapon
     * @param modelView the matrix of the camera of the game of blocks
     * @param worldProjection the matrix of the projection of the world of the game of blocks
     * @param handProjection the matrix of the projection of the hand of the game of blocks
     * @param worldMuzzle the place of the mouth of the weapon in the world, in the space of the
     *     camera of the player
     * @return the move, in the frame of the mesh of the weapon
     */
    public static Vector3f align(Matrix4f chain, Matrix4f modelView, Matrix4f worldProjection,
            Matrix4f handProjection, Vector3f worldMuzzle) {
        // The space of the camera the mesh of the weapon is drawn in.
        Matrix4f space = new Matrix4f(modelView).mul(chain);
        // The mouth of the weapon as it is drawn at the moment.
        Vector4f point = new Vector4f(MOUTH.x, MOUTH.y, MOUTH.z, 1.0f).mul(space);
        Vector3f drawn = new Vector3f(point.x, point.y, point.z);
        // The place of the mouth of the weapon in the world on the screen of the world.
        Vector2f target = screen(worldProjection, worldMuzzle);
        // The place of the mouth of the weapon in the space of the camera, at the depth the game
        // of blocks gives to the mouth of the weapon.
        Vector3f wanted = atDepth(handProjection, target, -drawn.z);
        // The move of the mesh of the weapon that brings the mouth to the place that is wanted.
        Vector3f difference = wanted.sub(drawn);
        // The mouth of the drawn weapon stays below the crosshair of the screen: the height of the
        // weapon above its mouth is the measure of the room the weapon needs.
        double room = (Rac1BlasterGrip.MESH_MAX_X - Rac1BlasterGrip.BORE_X)
                * Rac1BlasterGrip.FIRST_PERSON_SCALE + CLEARANCE;
        if (drawn.y + difference.y > -room) {
            difference.y = (float) (-room - drawn.y);
        }
        return difference.mul(new Matrix3f(space).invert());
    }

    /**
     * The direction of an axis of the mesh of the weapon in the space of the camera.
     *
     * @param chain the chain of the poses of the game of blocks, up to the mesh of the weapon
     * @param modelView the matrix of the camera of the game of blocks
     * @param axis the axis of the mesh of the weapon
     * @return the direction in the space of the camera
     */
    public static Vector3f direction(Matrix4f chain, Matrix4f modelView, Vector3f axis) {
        Matrix4f space = new Matrix4f(modelView).mul(chain);
        return new Vector3f(axis).mul(new Matrix3f(space)).normalize();
    }
}
