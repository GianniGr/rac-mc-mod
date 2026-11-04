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
 * <p>Two models are drawn: the hero with his Blaster in his hand, and the piece of the scenery of
 * the game. They stand next to each other, in two places of their own, and every model is framed
 * on its own: the hero keeps his size, and the scenery is drawn as large as its place allows.
 * The places and the boxes around the models do not change from frame to frame, so neither model
 * moves on the screen while the demo runs.
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

    /** The box around the scenery, when it was measured. */
    private float[] staticFraming;

    /** The scenery the box around it was measured for. */
    private Rac1StaticAsset staticSource;

    /** The failure of the drawing, when there was one. */
    private String failure;

    /**
     * Draws the hero with the Blaster in his hand and the scenery of the game next to him.
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
            float[] framing = demo.framing();
            if (framing == null) {
                return;
            }
            Rac1StaticAsset scenery = content.staticAsset();
            boolean withScenery = scenery != null && scenery.mesh().triangleCount() > 0;
            Rac1PreviewLayout.View heroView = Rac1PreviewLayout.heroView(areaX, areaY, areaWidth,
                    areaHeight, withScenery);
            Rac1PreviewLayout.View sceneryView = withScenery
                    ? Rac1PreviewLayout.sceneryView(areaX, areaY, areaWidth, areaHeight)
                    : null;

            MultiBufferSource.BufferSource buffers =
                    Minecraft.getInstance().renderBuffers().bufferSource();
            PoseStack pose = graphics.pose();

            // The model is measured and turned so that it stands in the middle of its place and
            // fills it as far as the room allows. The box around the model is the same for every
            // moment of the demo, so that the model does not move on the screen while it runs.
            float scale = Rac1PreviewLayout.scale(framing, heroView.width(), heroView.height());
            // The hero is never drawn larger than he was drawn before the scenery was added to
            // the preview, so that the scenery does not change the way the hero looks.
            scale = Math.min(scale, Rac1PreviewLayout.scale(framing, areaWidth, areaHeight));
            float centreX = (framing[0] + framing[3]) * 0.5f;
            float centreY = (framing[1] + framing[4]) * 0.5f;
            float centreZ = (framing[2] + framing[5]) * 0.5f;

            pose.pushPose();
            try {
                pose.translate(heroView.centreX(), heroView.y() + heroView.height() * 0.5f, 0.0f);
                pose.scale(scale, scale, scale);
                // The second axis of the screen of the game points down, and the second axis of
                // the models of the game points up. The model is therefore turned around the
                // first axis, so that the hero stands on his feet on the screen and faces the
                // player. The turn is a proper turn, so it does not change the winding of the
                // triangles.
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0f));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
                // The screens of the game of blocks draw their pictures in a frame whose second
                // axis points down and whose third axis points towards the player, so a model of
                // the game would come out mirrored on the screen: the right hand of the hero
                // would be on the left of the screen when he is seen from behind. The game of
                // blocks turns the models of its own screens around their vertical axis for the
                // same reason, so the third axis of the model is turned around here.
                pose.scale(1.0f, 1.0f, -1.0f);
                pose.translate(-centreX, -centreY, -centreZ);
                Rac1MeshRenderer.render(pose, buffers, content.animatedAsset().mesh(),
                        demo.ratchetPositions(), demo.ratchetNormals(), this.heroTextures);
                Rac1MeshRenderer.render(pose, buffers, content.blaster().mesh(),
                        demo.blasterPositions(), demo.blasterNormals(), this.blasterTextures);
            } finally {
                pose.popPose();
            }

            if (withScenery) {
                drawScenery(pose, buffers, scenery, angle, sceneryView);
                // A thin line between the two places, so that the player sees that the preview
                // holds two models and not one.
                graphics.fill(sceneryView.x(), sceneryView.y(), sceneryView.x() + 1,
                        sceneryView.bottomY(), 0x40FFFFFF);
            }
        } catch (RuntimeException | LinkageError exception) {
            this.failure = exception.toString();
            RacMcMod.LOGGER.error("The assets of the game could not be drawn. The preview is "
                    + "not drawn again.", exception);
        }
    }

    /**
     * Draws the piece of the scenery of the game.
     *
     * <p>The scenery has no skeleton and no animation: its geometry is already the geometry of
     * the game, so it is drawn with the same renderer as the hero and with the same turn of the
     * view. It stands on the lower edge of its place, the way a piece of the scenery of the game
     * stands on the ground, and the box around it is measured once, so that it does not move on
     * the screen.
     *
     * @param pose the place of the models
     * @param buffers the buffers the triangles are written into
     * @param scenery the scenery
     * @param angle the turn of the view in degrees
     * @param view the place the scenery is drawn in
     */
    private void drawScenery(PoseStack pose, MultiBufferSource.BufferSource buffers,
                              Rac1StaticAsset scenery, float angle,
                              Rac1PreviewLayout.View view) {
        float[] framing = framingOf(scenery);
        if (framing == null) {
            return;
        }
        float scale = Rac1PreviewLayout.scale(framing, view.width(), view.height());
        float centreX = (framing[0] + framing[3]) * 0.5f;
        float centreZ = (framing[2] + framing[5]) * 0.5f;
        pose.pushPose();
        try {
            pose.translate(view.centreX(), view.bottomY(), 0.0f);
            pose.scale(scale, scale, scale);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0f));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
            pose.scale(1.0f, 1.0f, -1.0f);
            // The scenery stands on the lower edge of its place, so the box is turned around the
            // middle of its width and around its lowest point.
            pose.translate(-centreX, -framing[1], -centreZ);
            Rac1MeshRenderer.render(pose, buffers, scenery.mesh(), this.staticTextures);
        } finally {
            pose.popPose();
        }
    }

    /**
     * The box around the scenery.
     *
     * <p>The box is measured once for every scenery, so that the scenery does not move on the
     * screen from frame to frame.
     *
     * @param scenery the scenery
     * @return the smallest and the greatest place of every axis, or {@code null} when the scenery
     *         has no triangle
     */
    private float[] framingOf(Rac1StaticAsset scenery) {
        if (this.staticFraming == null || this.staticSource != scenery) {
            this.staticSource = scenery;
            this.staticFraming = Rac1PreviewLayout.box(scenery.mesh());
        }
        return this.staticFraming;
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
