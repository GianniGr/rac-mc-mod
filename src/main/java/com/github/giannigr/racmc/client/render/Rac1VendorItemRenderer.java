package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Items always show the closed body, with no proximity effect or player/world dependency. */
public final class Rac1VendorItemRenderer extends BlockEntityWithoutLevelRenderer {
    private Object failedSource;

    public Rac1VendorItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        var content = RacMcClient.assets().content().orElse(null);
        if (content == null || content == failedSource) {
            return;
        }
        try {
            Rac1VendorDrawing.drawAsItem(content, pose, buffers,
                    context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light);
        } catch (RuntimeException exception) {
            failedSource = content;
            RacMcMod.LOGGER.error("The Gadgetron vendor item could not be drawn.", exception);
        }
    }
}
