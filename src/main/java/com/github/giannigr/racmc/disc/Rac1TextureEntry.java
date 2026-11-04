package com.github.giannigr.racmc.disc;

/**
 * An entry of a texture table of a level.
 *
 * <p>The textures of a level are 8 bit images with a palette. An entry names where the pixels are
 * in the core data of the level, how large the image is and where its palette is in the image of
 * the memory of the graphics chip.
 */
public final class Rac1TextureEntry {

    /** The size of an entry in bytes. */
    public static final int SIZE = 0x10;

    private final int pixelOffset;
    private final int width;
    private final int height;
    private final int type;
    private final int palette;
    private final int mipmap;

    /**
     * Creates an entry.
     *
     * @param pixelOffset the offset of the pixels in the core data
     * @param width the width of the image
     * @param height the height of the image
     * @param type the type of the texture
     * @param palette the offset of the palette in the image of the graphics memory, in blocks of
     *        0x100 bytes
     * @param mipmap the offset of the reduced image in the image of the graphics memory, in blocks
     *        of 0x100 bytes
     */
    Rac1TextureEntry(int pixelOffset, int width, int height, int type, int palette, int mipmap) {
        this.pixelOffset = pixelOffset;
        this.width = width;
        this.height = height;
        this.type = type;
        this.palette = palette;
        this.mipmap = mipmap;
    }

    /**
     * The offset of the pixels in the core data of the level, relative to the base of the pixels.
     *
     * @return the offset in bytes
     */
    public int pixelOffset() {
        return pixelOffset;
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
     * The type of the texture.
     *
     * @return the type
     */
    public int type() {
        return type;
    }

    /**
     * The offset of the palette in the image of the graphics memory.
     *
     * @return the offset in bytes
     */
    public int paletteOffset() {
        return palette * 0x100;
    }

    /**
     * The offset of the reduced image in the image of the graphics memory.
     *
     * @return the offset in bytes, or {@code -1} when the texture has no reduced image
     */
    public int mipmapOffset() {
        return mipmap * 0x100;
    }
}
