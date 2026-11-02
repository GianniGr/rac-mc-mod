package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1BlasterDemo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing of the assets of the game.
 *
 * <p>The class puts the textures of the assets into the texture manager of the game and draws the
 * meshes of the assets with the renderer of the game.
 *
 * <p>The drawing is framed: the model is measured and turned so that it stands in the middle of
 * the area it is drawn in, whatever its size is.
 *
 * <p>A failure of the drawing is reported once and remembered: the screen stays usable and the
 * failure is not hidden, but it is not written into the log again and again either.
 */
public final class Rac1AssetPreview {

    /** The textures of the hero, when they were registered. */
    private ResourceLocation[] heroTextures;

    /** The textures of the Blaster, when they were registered. */
    private ResourceLocation[] blasterTextures;

    /** The textures of the scenery, when they were registered. */
    private ResourceLocation[] staticTextures;

    /** The failure of the drawing, when there was one. */
    private String failure;

    /**
     * Draws the hero with the Blaster in his hand.
     *
     * @param graphics the graphics of the screen
     * @param content the content of the cache of the assets
     * @param demo the pose of the demo
     * @param angle the turn of the view in degrees
     * @param areaX the left edge of the area the model is drawn in
     * @param areaY the upper edge of the area the model is drawn in
     * @param areaWidth the width of the area the model is drawn in
     * @param areaHeight the height of the area the model is drawn in
     */
    public void draw(GuiGraphics graphics, Rac1CacheContent content, Rac1BlasterDemo demo,
                     float angle, int areaX, int areaY, int areaWidth, int areaHeight) {
        if (this.failure != null || content == null || demo == null) {
            return;
        }
        try {
            if (this.heroTextures == null) {
                this.heroTextures = register(content.animatedAsset());
                this.blasterTextures = register(content.blaster());
                this.staticTextures = registerStatic(content.staticAsset());
            }
            float[] bounds = demo.bounds();
            if (bounds == null) {
                return;
            }
            // The model is measured and turned so that it stands in the middle of the area and
            // fills it as far as the room allows.
            float width = Math.max(0.001f, bounds[3] - bounds[0]);
            float height = Math.max(0.001f, bounds[4] - bounds[1]);
            float depth = Math.max(0.001f, bounds[5] - bounds[2]);
            float scale = Math.min(areaWidth / Math.max(width, depth), areaHeight / height) * 0.85f;
            float centreX = (bounds[0] + bounds[3]) * 0.5f;
            float centreY = (bounds[1] + bounds[4]) * 0.5f;
            float centreZ = (bounds[2] + bounds[5]) * 0.5f;

            MultiBufferSource.BufferSource buffers =
                    Minecraft.getInstance().renderBuffers().bufferSource();
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, 0.0f);
                pose.scale(scale, scale, scale);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
                pose.translate(-centreX, -centreY, -centreZ);
                Rac1MeshRenderer.render(pose, buffers, content.animatedAsset().mesh(),
                        demo.ratchetPositions(), demo.ratchetNormals(), this.heroTextures);
                Rac1MeshRenderer.render(pose, buffers, content.blaster().mesh(),
                        demo.blasterPositions(), demo.blasterNormals(), this.blasterTextures);
            } finally {
                pose.popPose();
            }
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("The assets of the game could not be drawn. The preview is "
                    + "not drawn again.", exception);
        }
    }

    /**
     * The failure of the drawing, when there was one.
     *
     * @return the failure, or {@code null} when the drawing works
     */
    public String failure() {
        return this.failure;
    }

    /**
     * Releases the textures of the assets.
     *
     * <p>The screen that draws the preview has to release the textures when it is closed, so that
     * the images of the textures do not stay in the texture manager of the game.
     */
    public void release() {
        release(this.heroTextures);
        release(this.blasterTextures);
        release(this.staticTextures);
        this.heroTextures = null;
        this.blasterTextures = null;
        this.staticTextures = null;
    }

    /**
     * Releases the textures of a list of places.
     *
     * @param locations the places of the textures
     */
    private static void release(ResourceLocation[] locations) {
        if (locations == null) {
            return;
        }
        for (ResourceLocation location : locations) {
            Rac1ClientTextures.release(location);
        }
    }

    /**
     * Registers the textures of an asset.
     *
     * @param asset the asset
     * @return the place of the texture of every material
     */
    private static ResourceLocation[] register(Rac1AnimatedAsset asset) {
        if (asset == null) {
            return new ResourceLocation[0];
        }
        ResourceLocation[] locations = new ResourceLocation[asset.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register(asset.name() + "_" + index,
                    asset.textures()[index]);
        }
        return locations;
    }

    /**
     * Registers the textures of the scenery.
     *
     * @param asset the asset
     * @return the place of the texture of every material
     */
    private static ResourceLocation[] registerStatic(Rac1StaticAsset asset) {
        if (asset == null) {
            return new ResourceLocation[0];
        }
        ResourceLocation[] locations = new ResourceLocation[asset.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register(asset.name() + "_" + index,
                    asset.textures()[index]);
        }
        return locations;
    }
}
