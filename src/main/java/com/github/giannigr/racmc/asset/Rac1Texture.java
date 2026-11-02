package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * A texture that was converted from the disc of the game.
 *
 * <p>The textures of the game are 8 bit images with a palette. The converter turns them into
 * 32 bit pixels in the order of the game: alpha, red, green and blue, each in the low byte.
 */
public final class Rac1Texture {

    /** The width of the image in pixels. */
    private final int width;

    /** The height of the image in pixels. */
    private final int height;

    /** The pixels of the image, four bytes each, in the order alpha, red, green, blue. */
    private final byte[] pixels;

    /**
     * Creates a texture.
     *
     * @param width the width of the image in pixels
     * @param height the height of the image in pixels
     * @param pixels the pixels of the image, four bytes each
     */
    public Rac1Texture(int width, int height, byte[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A texture has to have a size");
        }
        if (pixels.length != width * height * 4) {
            throw new IllegalArgumentException("A texture has to hold four bytes per pixel");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /**
     * The width of the image.
     *
     * @return the width in pixels
     */
    public int width() {
        return width;
    }

    /**
     * The height of the image.
     *
     * @return the height in pixels
     */
    public int height() {
        return height;
    }

    /**
     * The pixels of the image.
     *
     * @return the pixels, four bytes each
     */
    public byte[] pixels() {
        return pixels;
    }

    /**
     * The pixel at the given place.
     *
     * @param x the column of the pixel
     * @param y the row of the pixel
     * @return the pixel as a packed colour
     */
    public int pixel(int x, int y) {
        int offset = (y * width + x) * 4;
        return ((pixels[offset] & 0xFF) << 24) | ((pixels[offset + 1] & 0xFF) << 16)
                | ((pixels[offset + 2] & 0xFF) << 8) | (pixels[offset + 3] & 0xFF);
    }

    /**
     * The pixels of the image as packed colours.
     *
     * @return the colours, one per pixel
     */
    public int[] colours() {
        int[] colours = new int[width * height];
        for (int index = 0; index < colours.length; index++) {
            int offset = index * 4;
            colours[index] = ((pixels[offset] & 0xFF) << 24) | ((pixels[offset + 1] & 0xFF) << 16)
                    | ((pixels[offset + 2] & 0xFF) << 8) | (pixels[offset + 3] & 0xFF);
        }
        return colours;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1Texture texture && width == texture.width && height == texture.height
                && Arrays.equals(pixels, texture.pixels);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(pixels);
    }
}
