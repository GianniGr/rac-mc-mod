package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.disc.Rac1TextureEntry;

/**
 * The reader of the textures of the game.
 *
 * <p>A texture of the game is an 8 bit image with a palette. The pixels are stored in the core data
 * of the level, one byte per pixel, and the palette is stored in the image of the memory of the
 * graphics chip of the console, 256 entries of four bytes each.
 *
 * <p>The palette is stored in the order of the console, in which the bits three and four of an
 * index are swapped. The reader puts the entries into the order in which the pixels name them.
 */
public final class Rac1TextureReader {

    private Rac1TextureReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads a texture.
     *
     * @param coreData the core data of the level
     * @param texturesBase the offset of the pixels of the textures in the core data
     * @param gsRam the image of the memory of the graphics chip
     * @param entry the entry of the texture table
     * @return the texture
     * @throws AssetFormatException when the texture does not follow the expected layout
     */
    public static Rac1Texture read(byte[] coreData, int texturesBase, byte[] gsRam,
                                   Rac1TextureEntry entry) throws AssetFormatException {
        int width = entry.width();
        int height = entry.height();
        if (width <= 0 || height <= 0) {
            throw new AssetFormatException("A texture of the game has no size.");
        }
        int pixels = width * height;
        int pixelOffset = texturesBase + entry.pixelOffset();
        if (pixelOffset < 0 || pixelOffset > coreData.length - pixels) {
            throw new AssetFormatException("The pixels of a texture of the game lie outside of "
                    + "the core data of the level.");
        }
        int paletteOffset = entry.paletteOffset();
        if (paletteOffset < 0 || paletteOffset > gsRam.length - 1024) {
            throw new AssetFormatException("The palette of a texture of the game lies outside of "
                    + "the image of the graphics memory.");
        }

        byte[] palette = new byte[1024];
        System.arraycopy(gsRam, paletteOffset, palette, 0, 1024);
        int[] order = new int[256];
        for (int index = 0; index < 256; index++) {
            boolean different = ((index >> 3) & 1) != ((index >> 4) & 1);
            order[index] = different ? index ^ 0x18 : index;
        }

        byte[] result = new byte[pixels * 4];
        for (int index = 0; index < pixels; index++) {
            int source = order[coreData[pixelOffset + index] & 0xFF] * 4;
            int target = index * 4;
            result[target] = (byte) alpha(palette[source + 3] & 0xFF);
            result[target + 1] = palette[source];
            result[target + 2] = palette[source + 1];
            result[target + 3] = palette[source + 2];
        }
        return new Rac1Texture(width, height, result);
    }

    /**
     * The alpha of a colour of the game.
     *
     * <p>The alpha of the game runs from zero to 0x80, where 0x80 is opaque. A smaller value is
     * twice as large in the game of blocks.
     *
     * @param value the alpha of the game
     * @return the alpha of the game of blocks
     */
    public static int alpha(int value) {
        return value < 0x80 ? value * 2 : 255;
    }
}
