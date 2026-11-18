package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.asset.Rac1Mesh;

/**
 * The place of a crate of the game inside one block of the game of blocks.
 *
 * <p>A crate of the game is a piece of geometry that was imported as it was: it keeps the size
 * and the proportions it has in the game. The place tells how the crate has to be scaled and
 * moved to fit into one block of the game of blocks.
 *
 * <p>The crate is scaled by one factor for all three axes, so that its proportions are kept. The
 * factor is the smallest one that makes the crate fit into the block, so a crate that is smaller
 * than a block keeps its size and a crate that is bigger than a block is made smaller.
 */
public final class Rac1CrateFit {

    /**
     * The place of a crate inside a block.
     *
     * @param scale the factor every axis of the crate is scaled by
     * @param offsetX the move of the crate along the first axis
     * @param offsetY the move of the crate along the second axis
     * @param offsetZ the move of the crate along the third axis
     * @param width the width of the crate before it is scaled
     * @param height the height of the crate before it is scaled
     * @param depth the depth of the crate before it is scaled
     */
    public record Place(float scale, float offsetX, float offsetY, float offsetZ, float width,
                         float height, float depth) {

        /**
         * The move of the crate along the first axis in the cube of an item.
         *
         * <p>The cube of an item of the game of blocks holds the block that is drawn in it, and
         * the block of the game of blocks has its origin in the corner of the cube. The crate of
         * the game has its origin in the middle of its own width and at its feet, so it is
         * moved into the cube the way a block of the game of blocks is placed in it.
         *
         * @return the move of the crate
         */
        public float itemOffsetX() {
            return 0.5f + offsetX;
        }

        /**
         * The move of the crate along the second axis in the cube of an item.
         *
         * <p>The crate of the game stands on the ground of its own origin, and the block of the
         * game of blocks stands on the ground of its origin as well, so the crate is not moved
         * along the second axis.
         *
         * @return the move of the crate
         */
        public float itemOffsetY() {
            return offsetY;
        }

        /**
         * The move of the crate along the third axis in the cube of an item.
         *
         * @return the move of the crate
         */
        public float itemOffsetZ() {
            return 0.5f + offsetZ;
        }
    }

    private Rac1CrateFit() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The place of the crate of the given mesh inside one block.
     *
     * <p>The crate is centred along the first and the third axis and stands on the ground along
     * the second axis, so that it stands on the bottom of the block the way it stands on the
     * ground in the game.
     *
     * @param mesh the geometry of the crate
     * @return the place of the crate
     */
    public static Place place(Rac1Mesh mesh) {
        float[] box = box(mesh.positions());
        float width = box[1] - box[0];
        float height = box[3] - box[2];
        float depth = box[5] - box[4];
        float scale = 1.0f;
        if (width > 0.0f) {
            scale = Math.min(scale, 1.0f / width);
        }
        if (height > 0.0f) {
            scale = Math.min(scale, 1.0f / height);
        }
        if (depth > 0.0f) {
            scale = Math.min(scale, 1.0f / depth);
        }
        float centreX = (box[0] + box[1]) / 2.0f;
        float centreZ = (box[4] + box[5]) / 2.0f;
        float bottom = box[2];
        return new Place(scale, -centreX * scale, -bottom * scale, -centreZ * scale, width,
                height, depth);
    }

    /**
     * The box around the vertices of a mesh.
     *
     * @param positions the places of the vertices
     * @return the smallest and the largest place of every axis
     */
    private static float[] box(float[] positions) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int vertex = 0; vertex + 2 < positions.length; vertex += 3) {
            minX = Math.min(minX, positions[vertex]);
            maxX = Math.max(maxX, positions[vertex]);
            minY = Math.min(minY, positions[vertex + 1]);
            maxY = Math.max(maxY, positions[vertex + 1]);
            minZ = Math.min(minZ, positions[vertex + 2]);
            maxZ = Math.max(maxZ, positions[vertex + 2]);
        }
        if (minX > maxX) {
            // The mesh has no vertices: the crate is empty and stays where it is.
            return new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        }
        return new float[] {minX, maxX, minY, maxY, minZ, maxZ};
    }
}
