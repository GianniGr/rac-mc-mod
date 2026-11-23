package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The original textured world pickup, fitted to Minecraft item contexts. */
public final class BlasterAmmoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final Rac1ItemTextureLease<ResourceLocation> textures =
            Rac1ImportedItemDrawing.textures("blaster_ammo_item");

    public BlasterAmmoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        var pickup = Rac1EffectClient.ammoPickup();
        if (pickup == null) {
            textures.clear();
            return; // No stand-in pixels while media is absent/loading.
        }
        var locations = textures.get(pickup, Rac1ImportedItemDrawing.epoch(),
                pickup.textures().toArray(Rac1Texture[]::new)).toArray(ResourceLocation[]::new);
        var mesh = pickup.mesh();
        pose.pushPose();
        try {
            Rac1ImportedItemDrawing.restoreSocketOrigin(pose);
            Rac1ImportedItemDrawing.fitGui(pose, mesh.positions()); // bounded item presentation, not source-world scale
            Rac1MeshRenderer.render(pose, buffers, mesh, locations,
                    context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light, false);
        } finally {
            pose.popPose();
        }
    }
}
