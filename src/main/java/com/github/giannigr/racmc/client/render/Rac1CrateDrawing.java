package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.block.Rac1CrateFit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing of a crate of the game.
 *
 * <p>The crate of the game is drawn in two places: in the world, where it stands on the ground of
 * a block, and in the lists of the creative mode, where it stands in the cube of an item. The
 * two places use the same geometry and the same textures, so the drawing of the crate is written
 * down once, here.
 *
 * <p>The crate keeps the size and the proportions it has in the game. It is scaled by one factor
 * for all three axes, so that it fits into one block of the game of blocks.
 */
public final class Rac1CrateDrawing {

    private Rac1CrateDrawing() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Draws a crate of the game in the world.
     *
     * <p>The renderer of the block entities of the game of blocks stands at the corner of the
     * block, so the crate is moved to the middle of the block and stands on the ground of the
     * block.
     *
     * @param pose the place of the crate
     * @param buffers the buffers the triangles are written into
     * @param crate the crate of the game
     * @param textures the place of the texture of every material
     * @param light the light the crate is drawn with
     */
    public static void drawInBlock(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    Rac1CrateAsset crate, ResourceLocation[] textures, int light) {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate.mesh());
        pose.pushPose();
        try {
            pose.translate(0.5f, 0.0f, 0.5f);
            pose.scale(place.scale(), place.scale(), place.scale());
            pose.translate(place.offsetX(), place.offsetY(), place.offsetZ());
            Rac1MeshRenderer.render(pose, buffers, crate.mesh(), textures, light);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Draws a crate of the game as an item of the game of blocks.
     *
     * <p>The cube of an item of the game of blocks stands in the middle of the place of the
     * item, so the crate is centred in that cube and keeps its own proportions.
     *
     * @param pose the place of the crate
     * @param buffers the buffers the triangles are written into
     * @param crate the crate of the game
     * @param textures the place of the texture of every material
     * @param light the light the crate is drawn with
     */
    public static void drawAsItem(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                  Rac1CrateAsset crate, ResourceLocation[] textures, int light) {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate.mesh());
        pose.pushPose();
        try {
            pose.scale(place.scale(), place.scale(), place.scale());
            pose.translate(place.offsetX(), place.offsetY() - place.height() * place.scale() / 2.0f,
                    place.offsetZ());
            Rac1MeshRenderer.render(pose, buffers, crate.mesh(), textures, light);
        } finally {
            pose.popPose();
        }
    }
}
