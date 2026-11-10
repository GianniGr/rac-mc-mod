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
 *
 * <p>The buffers of the game are not always a buffer that can be closed: while a block is being
 * destroyed, the game of blocks hands the renderer of the block entity a buffer that draws the
 * crack of the block in front of the buffer of the block. The crate is drawn into that buffer as
 * well, so that the crate stays visible while it is broken and the crack of the game of blocks is
 * drawn over it.
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
    public static void drawInBlock(PoseStack pose, MultiBufferSource buffers, Rac1CrateAsset crate,
                                    ResourceLocation[] textures, int light) {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate.mesh());
        pose.pushPose();
        try {
            pose.translate(0.5f, 0.0f, 0.5f);
            pose.scale(place.scale(), place.scale(), place.scale());
            pose.translate(place.offsetX(), place.offsetY(), place.offsetZ());
            Rac1MeshRenderer.render(pose, buffers, crate.mesh(), textures, light, closes(buffers));
        } finally {
            pose.popPose();
        }
    }

    /**
     * Draws a crate of the game as an item of the game of blocks.
     *
     * <p>The cube of an item of the game of blocks holds the block that is drawn in it: the
     * model of a block of the game of blocks has its origin in the corner of that cube, and the
     * game of blocks moves the cube by half a block in every direction before it hands it to
     * the renderer of the item. The crate of the game has its origin in the middle of its own
     * width and at its feet, so it is moved into the cube the way a block of the game of blocks
     * is placed in it. The crate is then drawn exactly where a block of the game of blocks would
     * be drawn, so it is held in the hand of the player and shown in the lists of the game the
     * way every other block is.
     *
     * <p>The turn of the cube and its size are the turns of the model of the item, so that the
     * crate is drawn like every other block of the game of blocks.
     *
     * @param pose the place of the crate
     * @param buffers the buffers the triangles are written into
     * @param crate the crate of the game
     * @param textures the place of the texture of every material
     * @param light the light the crate is drawn with
     */
    public static void drawAsItem(PoseStack pose, MultiBufferSource buffers, Rac1CrateAsset crate,
                                   ResourceLocation[] textures, int light) {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate.mesh());
        pose.pushPose();
        try {
            pose.scale(place.scale(), place.scale(), place.scale());
            pose.translate(place.itemOffsetX(), place.itemOffsetY(), place.itemOffsetZ());
            Rac1MeshRenderer.render(pose, buffers, crate.mesh(), textures, light, closes(buffers));
        } finally {
            pose.popPose();
        }
    }

    /**
     * Draws a crate of the game at the place of the entity that carries it.
     *
     * <p>The origin of the entity that falls is the middle of the ground of the place the crate
     * was at, and the origin of the crate of the game is the middle of its own width and at its
     * feet as well, so the crate is drawn where it was without being moved.
     *
     * @param pose the place of the crate
     * @param buffers the buffers the triangles are written into
     * @param crate the crate of the game
     * @param textures the place of the texture of every material
     * @param light the light the crate is drawn with
     */
    public static void drawAtOrigin(PoseStack pose, MultiBufferSource buffers,
                                     Rac1CrateAsset crate, ResourceLocation[] textures, int light) {
        Rac1CrateFit.Place place = Rac1CrateFit.place(crate.mesh());
        pose.pushPose();
        try {
            pose.scale(place.scale(), place.scale(), place.scale());
            pose.translate(place.offsetX(), place.offsetY(), place.offsetZ());
            Rac1MeshRenderer.render(pose, buffers, crate.mesh(), textures, light, closes(buffers));
        } finally {
            pose.popPose();
        }
    }

    /**
     * Whether the runs of the triangles can be closed in the given buffers.
     *
     * @param buffers the buffers the triangles are written into
     * @return {@code true} when the buffers can be closed
     */
    private static boolean closes(MultiBufferSource buffers) {
        return buffers instanceof MultiBufferSource.BufferSource;
    }
}
