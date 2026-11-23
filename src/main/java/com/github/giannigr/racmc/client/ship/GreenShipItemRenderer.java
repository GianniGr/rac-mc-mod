package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared local hull in the item slot; authored miniature when media isn't ready. */
public final class GreenShipItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GreenShipItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            pose.scale(.78f, .78f, .78f);
            pose.translate(0, -.30, 0);
            pose.pushPose();
            try {
                // Inventory framing is independent of the shared in-world sizing.
                float miniature = .20f / GreenShipHullPose.BLOCKS_PER_UNIT;
                pose.scale(miniature, miniature, miniature);
                if (GreenShipHullRender.drawItem(pose, buffers, light)) return;
            } finally {
                pose.popPose();
            }
            box(pose, buffers, light, overlay, Blocks.LIME_CONCRETE, -.25, .12, -.55, .5, .24, 1.15);
            box(pose, buffers, light, overlay, Blocks.GREEN_CONCRETE, -.13, .12, .60, .26, .16, .15);
            box(pose, buffers, light, overlay, Blocks.CYAN_TERRACOTTA, -.18, .36, -.07, .36, .18, .37);
            for (double x : new double[] {-.59, .22}) {
                box(pose, buffers, light, overlay, Blocks.GREEN_CONCRETE, x, .11, -.60, .37, .13, .54);
                box(pose, buffers, light, overlay, Blocks.GRAY_CONCRETE, x + .07, .12, -.73, .23, .19, .38);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void box(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                            Block block, double x, double y, double z, double dx, double dy, double dz) {
        pose.pushPose();
        try {
            pose.translate(x, y, z);
            pose.scale((float) dx, (float) dy, (float) dz);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    block.defaultBlockState(), pose, buffers, light, overlay);
        } finally {
            pose.popPose();
        }
    }
}
