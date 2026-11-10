package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drawing of a falling block of the game of blocks.
 *
 * <p>The crates of the game fall like the sand of the game of blocks, so they are carried by the
 * falling block of the game of blocks. The renderer of the game of blocks draws the model of the
 * block that falls, and the crate of the game has no model: it is drawn by the renderer of its
 * block entity. The renderer of the falling block is therefore replaced by this one, which draws
 * the crate of the game with the mesh of the crate and hands every other falling block to the
 * renderer of the game of blocks.
 *
 * <p>The crate is drawn with the mesh and the textures of the crate, so that a crate that falls
 * looks like a crate that stands.
 */
public final class Rac1FallingBlockRenderer extends EntityRenderer<FallingBlockEntity> {

    /** The renderer of the game of blocks, which draws every block that is not a crate. */
    private final FallingBlockRenderer vanilla;

    /**
     * Creates the renderer.
     *
     * @param context the context of the renderer
     */
    public Rac1FallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanilla = new FallingBlockRenderer(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof Rac1CrateBlock block)) {
            // Every block that is not a crate is drawn by the game of blocks.
            this.vanilla.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
        try {
            Optional<Rac1CacheContent> content = RacMcClient.assets().content();
            if (content.isEmpty()) {
                // The assets of the game are not in the cache: there is nothing to draw.
                return;
            }
            Rac1CrateAsset crate = content.get().crate(block.definition().key());
            if (crate == null || crate.mesh().triangleCount() == 0) {
                return;
            }
            ResourceLocation[] locations = Rac1CrateTextures.of(content.get(), crate);
            Rac1CrateDrawing.drawAtOrigin(poseStack, buffers, crate, locations, packedLight);
            super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        } catch (RuntimeException | LinkageError exception) {
            RacMcMod.LOGGER.error("A falling crate of the game could not be drawn. The crate is "
                    + "not drawn again.", exception);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FallingBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
