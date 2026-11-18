package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

public final class Rac1BoltItemRenderer extends BlockEntityWithoutLevelRenderer {
    public Rac1BoltItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        RacMcItems.BOLTS.forEach((key, item) -> {
            if (!stack.is(item.get())) return;
            pose.pushPose();
            pose.translate(.5, .5, .5);
            Rac1BoltDrawing.draw(key, pose, buffers,
                    context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light);
            pose.popPose();
        });
    }
}
