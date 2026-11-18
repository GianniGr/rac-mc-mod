package com.github.giannigr.racmc.actions;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Rigid-Steve retarget math, shared by renderers and server contact samplers.
 * Caller supplies measured rest/channel calibration; no guessed Ratchet elbow/left-arm IDs.
 * This projects rotation, not an impossible exact elbow+hand trajectory onto one cuboid arm.
 */
public final class SteveWrenchPose {
    private SteveWrenchPose() {}

    /** Torso-relative source local rotation delta, conjugated into calibrated target coordinates. */
    public static Quaternionf rotation(Quaternionfc sourcePose, Quaternionfc sourceRest,
            Quaternionfc sourceToTarget, Quaternionfc targetRest) {
        Quaternionf delta = new Quaternionf(sourcePose).normalize()
                .mul(new Quaternionf(sourceRest).normalize().conjugate());
        Quaternionf basis = new Quaternionf(sourceToTarget).normalize();
        return basis.mul(delta)
                .mul(new Quaternionf(sourceToTarget).normalize().conjugate())
                .mul(targetRest).normalize();
    }

    /**
     * Native ModelPart pivot then Rz*Ry*Rx. The torso frame must be composed for independent
     * vanilla arm parts explicitly; vanilla arms are not model children of the body.
     */
    public static Matrix4f part(Matrix4fc torso, ActionVector pivotPixels,
            double xRot, double yRot, double zRot) {
        return new Matrix4f(torso)
                .translate((float) (pivotPixels.x() / 16), (float) (pivotPixels.y() / 16),
                        (float) (pivotPixels.z() / 16))
                .rotateZ((float) zRot).rotateY((float) yRot).rotateX((float) xRot);
    }

    /**
     * Actual ItemInHandLayer socket. Display transform/cube-origin correction follows this once
     * in the item renderer. For slim skins the caller must add side*0.5 pixels to the arm pivot
     * BEFORE part rotation (PlayerModel.translateToHand), never translate in the rotated frame.
     */
    public static Matrix4f socket(Matrix4fc arm, boolean left) {
        float side = left ? -1 : 1;
        return new Matrix4f(arm)
                .rotateX((float) (-Math.PI / 2)).rotateY((float) Math.PI)
                .translate(side / 16, 0.125F, -0.625F);
    }
}
