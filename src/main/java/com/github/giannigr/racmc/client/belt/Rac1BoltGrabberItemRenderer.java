package com.github.giannigr.racmc.client.belt;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Draws the original Quartu pickup in inventory, Creative, and as a loose item. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1BoltGrabberItemRenderer extends BlockEntityWithoutLevelRenderer {
    private Rac1BoltGrabberItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private Rac1BoltGrabberItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new Rac1BoltGrabberItemRenderer();
                return renderer;
            }
        }, RacMcItems.BOLT_GRABBER.get());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext display, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        var art = Rac1BoltGrabberResources.getPickup();
        if (art == null) return; // No fabricated or borrowed icon.
        var prepared = art.prepared();
        poses.pushPose();
        try {
            poses.mulPose(prepared.fit());
            Rac1MeshRenderer.render(poses, buffers, prepared.model().mesh(),
                    art.textures(),
                    display == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light, true);
        } finally {
            poses.popPose();
        }
    }
}
