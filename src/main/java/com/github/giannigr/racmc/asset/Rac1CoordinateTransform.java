package com.github.giannigr.racmc.asset;

/**
 * The change of the coordinate systems between the game and the game of blocks.
 *
 * <p>The game of the disc works in a world whose up axis is the third axis: the game calls it
 * Z-up. The game of blocks works in a world whose up axis is the second axis: it calls it Y-up.
 * The conversion therefore turns the axes around:
 *
 * <pre>
 * game      x  y  z        blocks     x  z  -y
 * </pre>
 *
 * <p>The conversion is applied once, after the geometry was decoded, so that the scale of a class
 * is applied exactly once as well.
 */
public final class Rac1CoordinateTransform {

    /** The number of the units of the game in a block of the game of blocks. */
    private final float unitScale;

    /**
     * Creates a transformation.
     *
     * @param unitScale the number of the units of the game in a block of the game of blocks
     */
    public Rac1CoordinateTransform(float unitScale) {
        if (!(unitScale > 0.0f) || !Float.isFinite(unitScale)) {
            throw new IllegalArgumentException("The size of a unit of the game has to be a positive "
                    + "number");
        }
        this.unitScale = unitScale;
    }

    /**
     * The number of the units of the game in a block of the game of blocks.
     *
     * @return the size of a unit
     */
    public float unitScale() {
        return unitScale;
    }

    /**
     * Turns a point of the game into a point of the game of blocks.
     *
     * @param x the first coordinate of the game
     * @param y the second coordinate of the game
     * @param z the third coordinate of the game
     * @return the three coordinates of the game of blocks
     */
    public float[] convert(float x, float y, float z) {
        return new float[] { x * unitScale, z * unitScale, -y * unitScale };
    }

    /**
     * Turns a direction of the game into a direction of the game of blocks.
     *
     * @param x the first component of the direction
     * @param y the second component of the direction
     * @param z the third component of the direction
     * @return the three components of the game of blocks
     */
    public float[] convertDirection(float x, float y, float z) {
        return new float[] { x, z, -y };
    }
}
