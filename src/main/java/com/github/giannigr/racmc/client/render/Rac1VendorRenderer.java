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
    private Object failedSource;

    public Rac1VendorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(Rac1VendorBlockEntity entity) {
        var pos = entity.getBlockPos();
        // Include the floating logo above the grounded half-height collision box.
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
    }

    @Override
    public void render(Rac1VendorBlockEntity entity, float partialTick, PoseStack pose,
                        MultiBufferSource buffers, int light, int overlay) {
        var content = RacMcClient.assets().content().orElse(null);
        if (content == null || content == failedSource || entity.getLevel() == null) {
            return;
        }
        try {
            // Keep precision for the spin even in long-lived worlds.
            double elapsed = (entity.getLevel().getGameTime() + (double) partialTick) / 20.0;
            float seconds = (float) (elapsed % (Math.PI * 2 / 0.6));
            Rac1VendorDrawing.draw(content, pose, buffers, light,
                    entity.visual().growth(partialTick), seconds);
        } catch (RuntimeException exception) {
            failedSource = content;
            RacMcMod.LOGGER.error("The Gadgetron vendor could not be drawn.", exception);
        }
    }
}
