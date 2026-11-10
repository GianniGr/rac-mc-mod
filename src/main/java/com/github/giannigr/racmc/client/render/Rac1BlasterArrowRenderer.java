package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.item.Rac1ArrowVisibility;
import com.github.giannigr.racmc.item.Rac1BlasterArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing of an arrow that the Blaster fired.
 *
 * <p>The arrow is not drawn while it is still at the mouth of the weapon: it is drawn from the
 * second tick of its flight, when it has left the weapon. The rule is a rule of the drawing only:
 * the arrow flies, it collides and it does its damage from the moment it is fired.
 *
 * <p>The arrows of the bow, of the crossbow and of the other mods keep the renderer of the game
 * of blocks and are drawn as they always were.
 */
public class Rac1BlasterArrowRenderer extends ArrowRenderer<Rac1BlasterArrow> {

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
        // The arrow of the Blaster is an ordinary arrow of the game of blocks.
        return TippableArrowRenderer.NORMAL_ARROW_LOCATION;
    }

    @Override
    public void render(Rac1BlasterArrow arrow, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
        if (Rac1ArrowVisibility.hidden(arrow.tickCount, partialTick)) {
            // The arrow is still at the mouth of the weapon: it is not drawn.
            return;
        }
        super.render(arrow, yaw, partialTick, poseStack, buffer, light);
    }
}
