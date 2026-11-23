package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class Rac1VendorRenderer implements BlockEntityRenderer<Rac1VendorBlockEntity> {
    /** Source unfold/fold dips below the closed-fit floor; culling only, never model placement/scale. */
    public static final float MIN_ANIMATED_Y=-.5f;
    private Object failedSource;

    public Rac1VendorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(Rac1VendorBlockEntity entity) {
        var pos = entity.getBlockPos();
        // The 2x world model includes antennas and a proportionally enlarged floating logo.
        return new AABB(pos.getX() - 2, pos.getY()+MIN_ANIMATED_Y, pos.getZ() - 2,
                pos.getX() + 3, pos.getY() + 6, pos.getZ() + 3);
    }

    @Override
    public void render(Rac1VendorBlockEntity entity, float partialTick, PoseStack pose,
                        MultiBufferSource buffers, int light, int overlay) {
        if (com.github.giannigr.racmc.client.shop.Rac1ShopPreviewScreen.suppressesWorld(entity)) return;
        var content = RacMcClient.assets().content().orElse(null);
        if (content == null || content == failedSource || entity.getLevel() == null) {
            return;
        }
        try {
            // Keep precision for the spin even in long-lived worlds.
            double elapsed = (entity.getLevel().getGameTime() + (double) partialTick) / 20.0;
            float seconds = (float) (elapsed % (Math.PI * 2 / 0.6));
            var physical=com.github.giannigr.racmc.client.shop.Rac1ShopObserverClient.pose(entity,content.vendor(),partialTick);
            Rac1VendorDrawing.drawInBlock(content, pose, buffers, light,
                    physical==null ? entity.visual().growth(partialTick):0,seconds,physical);
        } catch (RuntimeException exception) {
            failedSource = content;
            RacMcMod.LOGGER.error("The Gadgetron vendor could not be drawn.", exception);
        }
    }
}
