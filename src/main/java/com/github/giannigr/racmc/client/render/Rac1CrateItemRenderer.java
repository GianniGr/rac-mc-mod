package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The drawing of a crate of the game as an item of the game of blocks.
 *
 * <p>The crate of the game is an arbitrary mesh, so the item that places it is drawn by this
 * renderer instead of by a model. The renderer is given to the game of blocks for the item of the
 * crate, so that the tab of the creative mode shows the crate of the game and not a picture of
 * it.
 *
 * <p>The crate keeps the size and the proportions it has in the game and is scaled by one factor
 * for all three axes, so that it fits into the cube of an item.
 *
 * <p>The renderer draws nothing when the assets of the game are not in the cache.
 */
public final class Rac1CrateItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** The failure of the drawing, when there was one. */
    private String failure;

    /**
     * Creates the renderer.
     *
     * @param dispatcher the dispatcher of the blocks of the world
     * @param models the models of the entities
     */
    public Rac1CrateItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        if (this.failure != null) {
            return;
        }
        try {
            Optional<Rac1CacheContent> content = RacMcClient.assets().content();
            if (content.isEmpty()) {
                // The assets of the game are not in the cache: there is nothing to draw.
                return;
            }
            Rac1CrateAsset crate = content.get().crate(keyOf(stack));
            if (crate == null || crate.mesh().triangleCount() == 0) {
                return;
            }
            ResourceLocation[] locations = texturesOf(content.get(), crate);
            // The picture of the item in the lists of the game is always drawn with the full
            // light, so that the crate can be seen in the lists.
            int usedLight = context == ItemDisplayContext.GUI ? Rac1MeshRenderer.FULL_LIGHT : light;
            Rac1CrateDrawing.drawAsItem(poseStack, buffers, crate, locations, usedLight);
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("A crate of the game could not be drawn as an item. The crate is "
                    + "not drawn again.", exception);
        }
    }

    /**
     * The key of the crate the item places.
     *
     * @param stack the stack of the item
     * @return the key of the crate, or an empty text when the item places no crate
     */
    private static String keyOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof Rac1CrateBlock block) {
            return block.definition().key();
        }
        return "";
    }

    /**
     * The places of the textures of a crate.
     *
     * @param content the content of the cache of the assets
     * @param crate the crate of the game
     * @return the places of the textures
     */
    private static ResourceLocation[] texturesOf(Rac1CacheContent content, Rac1CrateAsset crate) {
        return Rac1CrateTextures.of(content, crate);
    }
}
