package com.github.giannigr.racmc.client.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import com.github.giannigr.racmc.client.render.Rac1ItemTextureLease;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Local-only inventory/ground display; a worn glove is never drawn through a held item display. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
@SuppressWarnings("removal")
public final class BombGloveItemDrawing extends BlockEntityWithoutLevelRenderer {
    private static final Rac1ItemTextureLease<ResourceLocation> GLOVE_TEXTURES =
            Rac1ImportedItemDrawing.textures("bomb_glove_item");
    private static final Rac1ItemTextureLease<ResourceLocation> AMMO_TEXTURES =
            Rac1ImportedItemDrawing.textures("bomb_glove_ammo_item");
    private final boolean glove;

    private BombGloveItemDrawing(boolean glove) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.glove = glove;
    }

    /** Release stale media immediately, not only if these items happen to be drawn again. */
    public static void clearTextures() {
        GLOVE_TEXTURES.clear();
        AMMO_TEXTURES.clear();
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        register(event, "bomb_glove", true);
        register(event, "bomb_glove_ammo", false);
    }

    private static void register(RegisterClientExtensionsEvent event, String path, boolean glove) {
        // P34 is the sole registrar. The standalone presentation branch adds no replacement item.
        var id = ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> event.registerItem(new IClientItemExtensions() {
            private BombGloveItemDrawing renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new BombGloveItemDrawing(glove);
                return renderer;
            }
        }, item));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext display, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        if (glove && !isUnwornDisplay(display)) return;
        int drawLight = display == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light;
        pose.pushPose();
        try {
            Rac1ImportedItemDrawing.restoreSocketOrigin(pose);
            if (glove) drawGlove(pose, buffers, drawLight);
            else drawAmmo(pose, buffers, drawLight);
        } finally {
            pose.popPose();
        }
    }

    private static boolean isUnwornDisplay(ItemDisplayContext display) {
        return display == ItemDisplayContext.GUI || display == ItemDisplayContext.GROUND
                || display == ItemDisplayContext.FIXED;
    }

    private void drawGlove(PoseStack pose, MultiBufferSource buffers, int light) {
        var art = Rac1EffectClient.bombGlove();
        if (art == null) return;
        var mesh = art.mesh();
        var textures = GLOVE_TEXTURES.get(art, Rac1ImportedItemDrawing.epoch(), art.textures())
                .toArray(ResourceLocation[]::new);
        Rac1ImportedItemDrawing.fitGui(pose, mesh.positions());
        Rac1MeshRenderer.render(pose, buffers, mesh, mesh.positions(), mesh.normals(), textures, light, false);
    }

    private void drawAmmo(PoseStack pose, MultiBufferSource buffers, int light) {
        var art = Rac1EffectClient.bombAmmo();
        if (art == null) return;
        var mesh = art.mesh();
        var textures = AMMO_TEXTURES.get(art, Rac1ImportedItemDrawing.epoch(),
                art.textures().toArray(Rac1Texture[]::new)).toArray(ResourceLocation[]::new);
        Rac1ImportedItemDrawing.fitGui(pose, mesh.positions());
        Rac1MeshRenderer.render(pose, buffers, mesh, textures, light, false);
    }
}
