package com.github.giannigr.racmc.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The chains of the transforms of the game of blocks, replayed with the classes of the game.
 *
 * <p>The chains are the chains of the version 1.21.1 with NeoForge 21.1.265, the same chains
 * that the renderer of the living entities and the renderer of the item in the hand build.
 */
final class Rac1BlasterChains {

    /** The place of the mouth of the barrel in the mesh of the weapon. */
    static final double[] MOUTH = { Rac1BlasterGrip.BORE_X, Rac1BlasterGrip.BORE_Y,
            Rac1BlasterGrip.MUZZLE_Z };

    /** The bore of the mesh of the weapon. */
    static final Vector3f BORE = new Vector3f(0.0f, 0.0f, 1.0f);

    /** The up axis of the mesh of the weapon. */
    static final Vector3f UP = new Vector3f(1.0f, 0.0f, 0.0f);

    /** The place of the hand of the player in the first person. */
    private static final float[] FIRST_HAND = { 0.56f, -0.52f, -0.72f };

    private Rac1BlasterChains() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The chain of the transforms of the game of blocks in the third person.
     *
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @param armX the turn of the arm of the player around the first axis, in degrees
     * @param armY the turn of the arm of the player around the second axis, in degrees
     * @return the stack of the poses of the game
     */
    static PoseStack thirdPerson(double bodyYaw, double armX, double armY) {
        ModelPart joint = new ModelPart(List.of(), Map.of());
        joint.setPos((float) (Rac1BlasterGrip.ARM_JOINT_X * 16.0),
                (float) (Rac1BlasterGrip.ARM_JOINT_Y * 16.0),
                (float) (Rac1BlasterGrip.ARM_JOINT_Z * 16.0));
        joint.setRotation((float) Math.toRadians(armX), (float) Math.toRadians(armY), 0.0f);
        PoseStack chain = new PoseStack();
        chain.setIdentity();
        chain.mulPose(Axis.YP.rotationDegrees((float) (180.0 - bodyYaw)));
        chain.scale(-1.0f, -1.0f, 1.0f);
        chain.translate(0.0f, -1.501f, 0.0f);
        joint.translateAndRotate(chain);
        chain.mulPose(Axis.XP.rotationDegrees(-90.0f));
        chain.mulPose(Axis.YP.rotationDegrees(180.0f));
        chain.translate((float) Rac1BlasterGrip.ITEM_IN_JOINT_X,
                (float) Rac1BlasterGrip.ITEM_IN_JOINT_Y, (float) Rac1BlasterGrip.ITEM_IN_JOINT_Z);
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
     * The chain of the transforms of the game of blocks in the third person, with the turn of the
     * weapon that is only held.
     *
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @param armX the turn of the arm of the player around the first axis, in degrees
     * @param armY the turn of the arm of the player around the second axis, in degrees
     * @param turn the turn of the weapon around its own side, in degrees
     * @return the stack of the poses of the game
     */
    static PoseStack thirdPersonIdle(double bodyYaw, double armX, double armY, double turn) {
        PoseStack chain = thirdPerson(bodyYaw, armX, armY);
        chain.mulPose(Axis.YP.rotationDegrees((float) turn));
        return chain;
    }

    /**
     * The chain of the transforms of the game of blocks in the first person.
     *
     * @param rotation the turn of the model of the item, in degrees
     * @param translation the move of the model of the item, in blocks
     * @param scale the size of the model of the item
     * @return the stack of the poses of the game
     */
    static PoseStack firstPerson(double[] rotation, double[] translation, double[] scale) {
        PoseStack chain = new PoseStack();
        chain.setIdentity();
        chain.translate(FIRST_HAND[0], FIRST_HAND[1], FIRST_HAND[2]);
        ItemTransform transform = new ItemTransform(
                new Vector3f((float) rotation[0], (float) rotation[1], (float) rotation[2]),
                new Vector3f((float) translation[0], (float) translation[1], (float) translation[2]),
                new Vector3f((float) scale[0], (float) scale[1], (float) scale[2]));
        transform.apply(false, chain);
        chain.translate(-0.5f, -0.5f, -0.5f);
        chain.translate(0.5f, 0.5f, 0.5f);
        return chain;
    }

    /**
     * Moves a place of the mesh of the weapon with the stack of the poses of the game.
     *
     * @param chain the stack of the poses of the game
     * @param mesh the place in the frame of the mesh of the weapon
     * @return the place
     */
    static Vector3f place(PoseStack chain, double[] mesh) {
        Vector4f place = new Vector4f((float) mesh[0], (float) mesh[1], (float) mesh[2], 1.0f)
                .mul(chain.last().pose());
        return new Vector3f(place.x, place.y, place.z);
    }

    /**
     * Moves a direction of the mesh of the weapon with the stack of the poses of the game.
     *
     * @param chain the stack of the poses of the game
     * @param axis the axis of the mesh of the weapon
     * @return the direction
     */
    static Vector3f direction(PoseStack chain, Vector3f axis) {
        Vector4f direction = new Vector4f(axis, 0.0f).mul(chain.last().pose());
        return new Vector3f(direction.x, direction.y, direction.z).normalize();
    }

    /**
     * The place of a point of the world in the space of the camera of the player.
     *
     * <p>The camera of the player looks into the direction of the third axis of the world, its
     * right hand is the negative first axis of the world and its up is the second axis of the
     * world. The place is the place of the point of the world, less the place of the eye of the
     * player.
     *
     * @param world the place in the world
     * @param eyeHeight the height of the eyes of the player above the place of his feet
     * @return the place in the space of the camera
     */
    static double[] toCamera(Vector3f world, double eyeHeight) {
        double[] look = { 0.0, 0.0, 1.0 };
        double[] relative = { world.x, world.y - eyeHeight, world.z };
        double[] right = normalize(cross(look, new double[] { 0.0, 1.0, 0.0 }));
        double[] up = cross(right, look);
        return new double[] { dot(relative, right), dot(relative, up), -dot(relative, look) };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0] };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] normalize(double[] a) {
        double length = Math.sqrt(dot(a, a));
        return new double[] { a[0] / length, a[1] / length, a[2] / length };
    }
}
