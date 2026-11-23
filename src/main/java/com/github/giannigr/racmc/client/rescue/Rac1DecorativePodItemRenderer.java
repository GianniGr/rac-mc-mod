package com.github.giannigr.racmc.client.rescue;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.rescue.Rac1PodRegistration;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Empty class-1515 hull using the same ready art and textures as the placed pod. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1DecorativePodItemRenderer extends BlockEntityWithoutLevelRenderer {
    private Rac1PodClientResources.Art failed;

    private Rac1DecorativePodItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private Rac1DecorativePodItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new Rac1DecorativePodItemRenderer();
                return renderer;
            }
        }, Rac1PodRegistration.DECORATIVE.get());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        // No disk work or texture upload on first sight. The existing pod tick publishes the art.
        var art = Rac1PodClientResources.get();
        if (art != failed) failed = null;
        if (art == null || art == failed) {
            drawFallback(pose, buffers, light, overlay);
            return;
        }
        var hull = art.presentation(false); // P40 already fitted this version to 0.84 blocks.
        var textures = art.hullTextures();
        int packedLight = context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light;
        pose.pushPose();
        try {
            // ItemRenderer offsets by -.5; the mounted mesh is already centered at .5 in X/Z.
            pose.translate(0, .5f - hull.hullCenterY(), 0);
            Rac1MeshRenderer.render(pose, buffers, hull.hull(), textures, packedLight,
                    buffers instanceof MultiBufferSource.BufferSource);
            Rac1PodRenderer.drawCanopy(pose, buffers, hull.canopy(), textures[0], packedLight);
        } catch (RuntimeException failure) {
            failed = art;
            RacMcMod.LOGGER.warn("Decorative pod item drawing disabled for these local resources.", failure);
        } finally {
            pose.popPose();
        }
    }

    private static void drawFallback(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // A visible vanilla token until the current selection publishes valid art.
        var minecraft = Minecraft.getInstance();
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            minecraft.getItemRenderer().renderStatic(new ItemStack(Items.IRON_INGOT), ItemDisplayContext.NONE,
                    light, overlay, pose, buffers, minecraft.level, 0);
        } finally {
            pose.popPose();
        }
    }
}
