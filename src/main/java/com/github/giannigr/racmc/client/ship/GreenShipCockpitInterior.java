package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;

/** Mod-authored two-piece seat and pedal board. Only for the measured refit, not the baseline hull. */
public final class GreenShipCockpitInterior {
    static final float PIXEL = .9375f / 16;
    static final float BODY_DATUM = .9375f * 1.501f;
    static final GreenShipPilotPose.Angles POSE = new GreenShipPilotPose.Angles(8, -46, -46, -82, -82);

    // In each posed leg's pixel basis. The inflated pants end at y=12.25 and z=2.25.
    static final AABB CUSHION = new AABB(-1.65, 1, 2.25, 1.65, 5.3, 3.6);
    static final AABB PEDAL = new AABB(-1.70, 12.25, -2.05, 1.70, 13.35, 2.05);

    private GreenShipCockpitInterior() {}

    static void legToShip(PoseStack pose, int side) {
        var seat = ShipCockpitRefit.CANDIDATE.seatLocal();
        pose.translate(seat.x + side * 1.9f * PIXEL, seat.y + BODY_DATUM - 12 * PIXEL, seat.z);
        pose.scale(PIXEL, -PIXEL, -PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees((float) POSE.rightLeg()));
    }

    /** Draw with the same block-space transform as the hull. The caller must gate the refit first. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light) {
        GreenShipCockpitFinish.draw(GreenShipCockpitFinish.FLOOR_MESH, pose, buffers, light);
        for (int side : new int[] {-1, 1}) {
            pose.pushPose();
            try {
                legToShip(pose, side);
                GreenShipCockpitFinish.draw(GreenShipCockpitFinish.CUSHION_MESH, pose, buffers, light);
                GreenShipCockpitFinish.draw(GreenShipCockpitFinish.PEDAL_MESH, pose, buffers, light);
            } finally {
                pose.popPose();
            }
        }
    }

}
