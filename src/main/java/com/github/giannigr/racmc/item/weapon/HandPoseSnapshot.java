package com.github.giannigr.racmc.item.weapon;

import java.util.Objects;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/**
 * A resolved physical hand, not a request to guess a Minecraft pose. ModelPart pivots are in model
 * units (sixteenths); rotations are degrees, ZYX. Position remains double precision.
 * Callers supplying crouch/swim/riding poses must supply their final renderer root and arm data.
 */
public record HandPoseSnapshot(Vector position, double bodyYaw, double entityScale,
                              boolean left, Vector pivot, Vector armRotation, Vector armScale,
                              double playerScale, double modelHeight, HandRoot root) {
    /** Compatibility constructor for an ordinary yaw-only root; resolved poses supply root explicitly. */
    public HandPoseSnapshot(Vector position, double bodyYaw, double entityScale, boolean left,
                            Vector pivot, Vector armRotation, Vector armScale, double playerScale,
                            double modelHeight) {
        this(position, bodyYaw, entityScale, left, pivot, armRotation, armScale, playerScale,
                modelHeight, HandRoot.yaw(bodyYaw));
    }
    public HandPoseSnapshot {
        Objects.requireNonNull(position);
        Objects.requireNonNull(pivot);
        Objects.requireNonNull(armRotation);
        Objects.requireNonNull(armScale);
        Objects.requireNonNull(root);
        if (!Double.isFinite(bodyYaw) || !Double.isFinite(modelHeight)
                || !Double.isFinite(entityScale) || entityScale <= 0
                || !Double.isFinite(playerScale) || playerScale <= 0
                || armScale.x() <= 0 || armScale.y() <= 0 || armScale.z() <= 0)
            throw new IllegalArgumentException("Invalid physical hand pose");
    }

    /** Source-pinned ordinary standing adult aim, not swimming/riding/attack animation. */
    public static HandPoseSnapshot standing(Vector position, double bodyYaw, double headYaw,
                                            double pitch, boolean left, boolean slim, double scale) {
        double pivotX = left ? 5 : -5;
        if (slim) pivotX += left ? -.5 : .5;
        return new HandPoseSnapshot(position, bodyYaw, scale, left,
                new Vector(pivotX, 2, 0), new Vector(pitch - 90, headYaw, 0),
                new Vector(1, 1, 1), .9375, 1.501);
    }
}
