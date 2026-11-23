package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

/** Body-only Clank from the same verified local class-601 asset generation as wearable Clank. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1RescuedClankItemRenderer extends BlockEntityWithoutLevelRenderer {
    private Rac1AnimatedAsset current;
    private Rac1ImportedItemPose.Sample idle;
    private Rac1ClankItemPose.Fit fit;

    private Rac1RescuedClankItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private Rac1RescuedClankItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new Rac1RescuedClankItemRenderer();
                return renderer;
            }
        }, RacMcItems.CLANK.get());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        Rac1ClankResources.update(true);
        var body = Rac1ClankResources.model(Role.CLANK);
        if (current != body) {
            current = body;
            idle = null;
            if (body != null) {
                try {
                    var sample = Rac1ImportedItemPose.sample(body, 1, 0);
                    fit = Rac1ClankItemPose.fit(sample.positions(), sample.positions());
                    idle = sample;
                } catch (RuntimeException invalid) {
                    RacMcMod.LOGGER.warn("Local Clank reward model is unavailable: {}", invalid.toString());
                }
            }
        }
        if (idle == null) {
            // A visible vanilla token while the verified local sidecar is unavailable.
            var minecraft = Minecraft.getInstance();
            pose.pushPose();
            try {
                pose.translate(.5, .5, .5);
                minecraft.getItemRenderer().renderStatic(new ItemStack(Items.IRON_NUGGET),
                        ItemDisplayContext.NONE, light, overlay, pose, buffers, minecraft.level, 0);
            } finally {
                pose.popPose();
            }
            return;
        }
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.scale(fit.scale(), fit.scale(), fit.scale());
            pose.translate(-fit.x(), -fit.y(), -fit.z());
            Rac1MeshRenderer.render(pose, buffers, body.mesh(), idle.positions(), idle.normals(),
                    Rac1ClankResources.textures(Role.CLANK),
                    context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light, true);
        } finally {
            pose.popPose();
        }
    }
}
