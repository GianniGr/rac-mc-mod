package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1CoreIndex;
import com.github.giannigr.racmc.disc.Rac1TextureEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the textures of the game.
 *
 * <p>The core index, the core data and the image of the graphics memory that are used are built by
 * hand. No data of the game is used anywhere.
 */
class Rac1TextureReaderTest {

    /** The number of the bytes of the image of the graphics memory. */
    private static final int GS_RAM_SIZE = 0x1000;

    /**
     * Builds a core index with one texture.
     *
     * @param pixelOffset the offset of the pixels of the texture in the core data
     * @param width the width of the texture
     * @param height the height of the texture
     * @param paletteOffset the offset of the palette of the texture in the graphics memory
     * @return the bytes of the core index
     */
    private static byte[] index(int pixelOffset, int width, int height, int paletteOffset) {
        byte[] index = new byte[Rac1CoreIndex.HEADER_SIZE + 0x600];
        writeInt(index, 0x8C, 0x10000);
        writeInt(index, 0x60, 0x1000);
        writeInt(index, 0x38, 1);
        writeInt(index, 0x3C, Rac1CoreIndex.HEADER_SIZE);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE, pixelOffset);
        writeShort(index, Rac1CoreIndex.HEADER_SIZE + 4, width);
        writeShort(index, Rac1CoreIndex.HEADER_SIZE + 6, height);
        writeShort(index, Rac1CoreIndex.HEADER_SIZE + 8, 0);
        writeShort(index, Rac1CoreIndex.HEADER_SIZE + 10, paletteOffset);
        writeShort(index, Rac1CoreIndex.HEADER_SIZE + 12, 0);
        return index;
    }

    /**
     * The graphics memory of the checks: every entry of the palette is the colour of its number.
     *
     * @return the bytes of the graphics memory
     */
    private static byte[] gsRam() {
        byte[] memory = new byte[GS_RAM_SIZE];
        for (int index = 0; index < 256; index++) {
            memory[index * 4] = (byte) index;
            memory[index * 4 + 1] = (byte) (255 - index);
            memory[index * 4 + 2] = (byte) (index * 2);
            memory[index * 4 + 3] = (byte) 0x80;
        }
        return memory;
    }

    @Test
    @DisplayName("A texture of the game is read")
    void aTextureIsRead() throws AssetFormatException, DiscFormatException {
        byte[] coreData = new byte[0x2000];
        coreData[0x1000] = 5;
        coreData[0x1001] = 6;
        Rac1CoreIndex index = Rac1CoreIndex.parse(index(0, 2, 2, 0));
        Rac1TextureEntry entry = index.mobyTextures().get(0);

        Rac1Texture texture = Rac1TextureReader.read(coreData, index.texturesBase(), gsRam(), entry);

        assertEquals(2, texture.width());
        assertEquals(2, texture.height());
        assertEquals(4, texture.pixels().length / 4);
        // The pixels of the game of blocks are kept in the order alpha, red, green and blue.
        int[] first = texture.colours();
        assertEquals(255, first[0] >>> 24, "The alpha of the game of blocks is opaque");
        assertEquals(5, (first[0] >>> 16) & 0xFF, "The red of the palette is the red of the game");
        assertEquals(250, (first[0] >>> 8) & 0xFF, "The green of the palette is the green");
        assertEquals(10, first[0] & 0xFF, "The blue of the palette is the blue");
    }

    @Test
    @DisplayName("A texture whose pixels lie outside of the core data is refused")
    void aTextureOutsideOfTheCoreDataIsRefused() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index(0, 64, 64, 0));
        Rac1TextureEntry entry = index.mobyTextures().get(0);

        AssetFormatException failure = assertThrows(AssetFormatException.class,
                () -> Rac1TextureReader.read(new byte[16], index.texturesBase(), gsRam(), entry));
        assertEquals(true, failure.getMessage().contains("outside"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A texture whose palette lies outside of the graphics memory is refused")
    void aTextureOutsideOfTheGraphicsMemoryIsRefused() throws AssetFormatException,
            DiscFormatException {
        byte[] coreData = new byte[0x2000];
        Rac1CoreIndex index = Rac1CoreIndex.parse(index(0, 2, 2, 0xF00));
        Rac1TextureEntry entry = index.mobyTextures().get(0);

        assertThrows(AssetFormatException.class,
                () -> Rac1TextureReader.read(coreData, index.texturesBase(), gsRam(), entry));
    }

    @Test
    @DisplayName("A texture without a size is refused")
    void aTextureWithoutASizeIsRefused() throws AssetFormatException, DiscFormatException {
        byte[] coreData = new byte[0x2000];
        Rac1CoreIndex index = Rac1CoreIndex.parse(index(0, 0, 0, 0));
        Rac1TextureEntry entry = index.mobyTextures().get(0);

        assertThrows(AssetFormatException.class,
                () -> Rac1TextureReader.read(coreData, index.texturesBase(), gsRam(), entry));
    }

    @Test
    @DisplayName("The alpha of the game of blocks is twice the alpha of the game")
    void theAlphaIsTwiceTheAlphaOfTheGame() {
        assertEquals(0, Rac1TextureReader.alpha(0));
        assertEquals(254, Rac1TextureReader.alpha(127));
        assertEquals(255, Rac1TextureReader.alpha(128));
        assertEquals(255, Rac1TextureReader.alpha(255));
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
