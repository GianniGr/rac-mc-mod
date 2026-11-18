package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.item.Rac1BlasterArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing of an arrow that the Blaster fired.
 *
 * <p>The entity never submits a vanilla shaft. Available imported graphics are prepared
 * immediately and submitted by the existing effect pass; actual media/capacity failure is
 * invisible and diagnosed. This renderer never changes collision, damage or flight.
 *
 * <p>The arrows of the bow, of the crossbow and of the other mods keep the renderer of the game
 * of blocks and are drawn as they always were.
 */
public class Rac1BlasterArrowRenderer extends EntityRenderer<Rac1BlasterArrow> {

    /**
     * Creates the renderer of the arrow of the Blaster.
     *
     * @param context the context of the renderers of the entities
     */
    public Rac1BlasterArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Rac1BlasterArrow arrow) {
        // Required by EntityRenderer; this renderer never submits vanilla geometry.
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(Rac1BlasterArrow arrow, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
        com.github.giannigr.racmc.client.effect.Rac1EffectClient.prepareProjectile(arrow, partialTick);
        // Imported graphics are submitted in AFTER_PARTICLES. Missing media/capacity is the
        // explicitly accepted invisible fallback, never a vanilla shaft (including stuck arrows).
    }
}
