package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Mesh;

/**
 * The layout of the preview of the assets of the game.
 *
 * <p>The preview draws two models: the hero with his Blaster in his hand, and the piece of the
 * scenery of the game. The two models are drawn side by side, in two places of their own, and
 * every model is measured so that it fills its place as far as the room allows.
 *
 * <p>The places of the preview are the same for every frame: the box around the hero is measured
 * over the whole cycle of his sequence, and the box around the scenery is measured once, because
 * the scenery does not move. A model therefore stands still on the screen while the demo runs.
 *
 * <p>The class holds no state and no dependency on the game of blocks, so the layout can be
 * checked on a plain JVM.
 */
public final class Rac1PreviewLayout {

    /**
     * The share of the width of the area the hero is drawn in, in percent.
     *
     * <p>The hero is wider than the scenery is deep, so it gets the bigger share of the width.
     */
    public static final int HERO_SHARE = 55;

    /**
     * The share of the place of a model that is filled by the model, in percent.
     *
     * <p>The rest of the place is left empty around the model, so that the model does not touch
     * the edge of its place.
     */
    public static final float MARGIN = 0.85f;

    private Rac1PreviewLayout() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A place of the screen that a model is drawn in.
     *
     * @param x the left edge of the place
     * @param y the upper edge of the place
     * @param width the width of the place
     * @param height the height of the place
     */
    public record View(int x, int y, int width, int height) {

        /**
         * The middle of the place on its first axis.
         *
         * @return the place
         */
        public int centreX() {
            return this.x + this.width / 2;
        }

        /**
         * The lower edge of the place on its second axis.
         *
         * @return the place
         */
        public int bottomY() {
            return this.y + this.height;
        }
    }

    /**
     * The place the hero with his Blaster is drawn in.
     *
     * <p>The hero keeps the whole area when there is no scenery to draw, so that the preview of
     * the assets of the game does not change when the scenery is missing.
     *
     * @param areaX the left edge of the area
     * @param areaY the upper edge of the area
     * @param areaWidth the width of the area
     * @param areaHeight the height of the area
     * @param withScenery whether the scenery is drawn next to the hero
     * @return the place of the hero
     */
    public static View heroView(int areaX, int areaY, int areaWidth, int areaHeight,
                                boolean withScenery) {
        if (!withScenery) {
            return new View(areaX, areaY, areaWidth, areaHeight);
        }
        int width = areaWidth * HERO_SHARE / 100;
        return new View(areaX, areaY, Math.max(1, width), Math.max(1, areaHeight));
    }

    /**
     * The place the scenery is drawn in.
     *
     * <p>The scenery stands next to the hero: it is drawn in the right half of the area.
     *
     * @param areaX the left edge of the area
     * @param areaY the upper edge of the area
     * @param areaWidth the width of the area
     * @param areaHeight the height of the area
     * @return the place of the scenery
     */
    public static View sceneryView(int areaX, int areaY, int areaWidth, int areaHeight) {
        int width = areaWidth * HERO_SHARE / 100;
        int x = areaX + width;
        return new View(x, areaY, Math.max(1, areaWidth - width), Math.max(1, areaHeight));
    }

    /**
     * The box around the triangles of a mesh.
     *
     * <p>Only the vertices that a triangle uses are measured, so that a vertex that no triangle
     * uses does not make the model smaller than it is.
     *
     * @param mesh the mesh
     * @return the smallest and the greatest place of every axis, or {@code null} when the mesh has
     *         no triangle
     */
    public static float[] box(Rac1Mesh mesh) {
        if (mesh == null) {
            return null;
        }
        float[] positions = mesh.positions();
        int[] indices = mesh.indices();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int index : indices) {
            if (index < 0 || index * 3 + 2 >= positions.length) {
                continue;
            }
            minX = Math.min(minX, positions[index * 3]);
            maxX = Math.max(maxX, positions[index * 3]);
            minY = Math.min(minY, positions[index * 3 + 1]);
            maxY = Math.max(maxY, positions[index * 3 + 1]);
            minZ = Math.min(minZ, positions[index * 3 + 2]);
            maxZ = Math.max(maxZ, positions[index * 3 + 2]);
        }
        if (minX > maxX) {
            return null;
        }
        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * The size a model is drawn with, so that it fills a place as far as the room allows.
     *
     * <p>The size is the smaller of the size that fits the box of the model on its width and the
     * size that fits it on its height, so that the whole model is always visible. The deepest
     * axis of the model is measured against the width as well, because the model turns around its
     * vertical axis while it is drawn.
     *
     * @param box the box around the model
     * @param width the width of the place
     * @param height the height of the place
     * @return the size
     */
    public static float scale(float[] box, int width, int height) {
        if (box == null) {
            return 0.0f;
        }
        float modelWidth = Math.max(0.001f, box[3] - box[0]);
        float modelHeight = Math.max(0.001f, box[4] - box[1]);
        float modelDepth = Math.max(0.001f, box[5] - box[2]);
        return Math.min(width / Math.max(modelWidth, modelDepth), height / modelHeight) * MARGIN;
    }
}
