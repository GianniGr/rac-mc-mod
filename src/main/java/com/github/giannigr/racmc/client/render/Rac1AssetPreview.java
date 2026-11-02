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
     * Draws the hero with the Blaster in his hand and the piece of scenery next to them.
     *
     * @param graphics the graphics of the screen
     * @param content the content of the cache of the assets
     * @param demo the pose of the demo
     * @param angle the turn of the view in degrees
     */
    public void draw(GuiGraphics graphics, Rac1CacheContent content, Rac1BlasterDemo demo,
                     float angle) {
        if (this.failure != null || content == null || demo == null) {
            return;
        }
        try {
            if (this.heroTextures == null) {
                this.heroTextures = register(content.animatedAsset());
                this.blasterTextures = register(content.blaster());
                this.staticTextures = registerStatic(content.staticAsset());
            }
            MultiBufferSource.BufferSource buffers =
                    Minecraft.getInstance().renderBuffers().bufferSource();
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-8.0f));
                Rac1MeshRenderer.render(pose, buffers, content.animatedAsset().mesh(),
                        demo.ratchetPositions(), demo.ratchetNormals(), this.heroTextures);
                Rac1MeshRenderer.render(pose, buffers, content.blaster().mesh(),
                        demo.blasterPositions(), demo.blasterNormals(), this.blasterTextures);
            } finally {
                pose.popPose();
            }

            // The piece of scenery of the level stands next to them.
            pose.pushPose();
            try {
                pose.translate(1.6f, 0.0f, 0.0f);
                pose.scale(0.35f, 0.35f, 0.35f);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
                Rac1MeshRenderer.render(pose, buffers, content.staticAsset().mesh(),
                        this.staticTextures);
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
