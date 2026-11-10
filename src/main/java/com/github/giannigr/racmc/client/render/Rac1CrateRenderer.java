package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drawing of a crate of the game in the world.
 *
 * <p>The crate of the game is an arbitrary mesh with its own textures, so it cannot be drawn with
 * the model of a block. It is drawn by this renderer, which is given to the game of blocks for
 * the block entity of the crates.
 *
 * <p>The crate keeps the size and the proportions it has in the game. It is scaled by one factor
 * for all three axes, so that it fits into one block of the game of blocks, and it is centred
 * above the ground of the block, so that it stands on the ground the way it stands on the
 * ground in the game.
 *
 * <p>The textures of the crate are put into the texture manager of the game once for every
 * content of the cache of the assets, and not once for every frame. The renderer draws nothing
 * when the assets of the game are not in the cache.
 */
public final class Rac1CrateRenderer implements BlockEntityRenderer<Rac1CrateBlockEntity> {

    /** The failure of the drawing, when there was one. */
    private String failure;

    /**
     * Creates the renderer.
     *
     * @param context the context of the renderer
     */
    public Rac1CrateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(Rac1CrateBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (this.failure != null) {
            return;
        }
        try {
            Rac1CrateBlock block = crateOf(blockEntity.getBlockState());
            if (block == null) {
                return;
            }
            Optional<Rac1CacheContent> content = RacMcClient.assets().content();
            if (content.isEmpty()) {
                // The assets of the game are not in the cache: there is nothing to draw.
                return;
            }
            Rac1CrateAsset crate = content.get().crate(block.definition().key());
            if (crate == null || crate.mesh().triangleCount() == 0) {
                return;
            }
            // The buffers of the game are not always a buffer that can be closed: while the
            // block is being destroyed, the game of blocks hands the renderer a buffer that
            // draws the crack of the block in front of the buffer of the block. The crate is
            // drawn into that buffer as well, so that the crate stays visible while it is
            // broken and the crack of the game of blocks is drawn over it.
            ResourceLocation[] locations = texturesOf(content.get(), crate);
            Rac1CrateDrawing.drawInBlock(poseStack, buffers, crate, locations, packedLight);
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("A crate of the game could not be drawn. The crate is not drawn "
                    + "again.", exception);
        }
    }

    /**
     * The block of the crate of the given state.
     *
     * @param state the state of the block
     * @return the block of the crate, or {@code null} when the state is not a crate
     */
    private static Rac1CrateBlock crateOf(BlockState state) {
        return state.getBlock() instanceof Rac1CrateBlock block ? block : null;
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
