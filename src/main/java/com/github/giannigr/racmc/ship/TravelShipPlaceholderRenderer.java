package com.github.giannigr.racmc.ship;

import com.github.giannigr.racmc.client.ship.GreenShipHullRender;
import com.github.giannigr.racmc.client.ship.GreenShipFlightFxRender;
import com.github.giannigr.racmc.client.ship.GreenShipCockpitInterior;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Keeps the cuboid layout as a fallback for the optional, media-backed normal hull. */
final class TravelShipPlaceholderRenderer extends EntityRenderer<Rac1TravelShip> {
    private final BlockRenderDispatcher blocks;

    TravelShipPlaceholderRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = .7F;
    }

    @Override
    public void render(Rac1TravelShip ship, float yaw, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light) {
        int hullLight = GreenShipFlightFxRender.draw(ship, yaw, tickDelta, pose, buffers, light);
        var cameraInShip = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(ship.getPosition(tickDelta)).yRot(yaw * Mth.DEG_TO_RAD);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        boolean open = ship.hasRefitCockpit() && openCockpit(ship.accessPhase(), ship.isVehicle(),
                ship.accessPath() != null);
        // The visible switch is an authored portal, not an animation of the verified frame-zero hull.
        if (GreenShipHullRender.draw(false, open, cameraInShip, pose, buffers, hullLight)) {
            if (open) GreenShipCockpitInterior.draw(pose, buffers, light);
            GreenShipFlightFxRender.drawRear(ship, pose, buffers);
            pose.popPose();
            super.render(ship, yaw, tickDelta, pose, buffers, light);
            return;
        }
        if (ship.hasRefitCockpit()) {
            for (var part : ShipRefitFallback.parts()) {
                var b = part.bounds();
                box(pose, buffers, light, part.block(), b.minX, b.minY, b.minZ, b.getXsize(), b.getYsize(), b.getZsize());
            }
            GreenShipCockpitInterior.draw(pose, buffers, light);
            pose.popPose();
            super.render(ship, yaw, tickDelta, pose, buffers, light);
            return;
        }
        box(pose, buffers, light, Blocks.CYAN_CONCRETE, -.51, .12, -.50, 1.02, .35, 1.04);
        box(pose, buffers, light, Blocks.ORANGE_CONCRETE, -.65, .04, -.42, .23, .22, .83);
        box(pose, buffers, light, Blocks.ORANGE_CONCRETE, .42, .04, -.42, .23, .22, .83);
        box(pose, buffers, light, Blocks.YELLOW_CONCRETE, -.28, .25, .52, .56, .20, .18);
        box(pose, buffers, light, Blocks.GRAY_CONCRETE, -.28, .47, -.25, .56, .09, .49);
        box(pose, buffers, light, Blocks.GRAY_CONCRETE, -.28, .49, -.36, .56, .43, .11);
        // Deliberately simple phase indicator; not a canopy or boarding animation adaptation.
        var phase = ship.accessSnapshot().phase();
        boolean access = phase == Rac1TravelShip.AccessPhase.BOARDING || phase == Rac1TravelShip.AccessPhase.EXITING;
        box(pose, buffers, light, access ? Blocks.YELLOW_CONCRETE : Blocks.WHITE_CONCRETE,
                -.33, access ? .97 : .79, .33, .66, .10, .15);
        pose.popPose();
        super.render(ship, yaw, tickDelta, pose, buffers, light);
    }

    private void box(PoseStack pose, MultiBufferSource buffers, int light, Block material,
                     double x, double y, double z, double dx, double dy, double dz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale((float) dx, (float) dy, (float) dz);
        blocks.renderSingleBlock(material.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override protected boolean shouldShowName(Rac1TravelShip ship) {
        return ship.hasCustomName() && super.shouldShowName(ship);
    }

    static boolean openCockpit(Rac1TravelShip.AccessPhase phase, boolean rider, boolean route) {
        // Either replicated fact can arrive first. Never close the original roof across a mounted or routed pilot.
        return phase != Rac1TravelShip.AccessPhase.PARKED || rider || route;
    }
    @Override public ResourceLocation getTextureLocation(Rac1TravelShip ship) { return TextureAtlas.LOCATION_BLOCKS; }
}
