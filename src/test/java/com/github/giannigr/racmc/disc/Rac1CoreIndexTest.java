package com.github.giannigr.racmc.disc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the core index of a level.
 *
 * <p>The core index that is read is built by hand. No data of the game is used anywhere.
 */
class Rac1CoreIndexTest {

    /**
     * Builds a core index with one class of a character, one class of the scenery, one gadget and
     * one animation sequence of the hero.
     *
     * @return the bytes of the core index
     */
    private static byte[] index() {
        byte[] index = new byte[Rac1CoreIndex.HEADER_SIZE + 0x600];
        // The size of the decompressed core data.
        writeInt(index, 0x8C, 0x10000);
        // The base of the pixels.
        writeInt(index, 0x60, 0x9000);
        // The table of the classes of the characters.
        writeInt(index, 0x18, 1);
        writeInt(index, 0x1C, Rac1CoreIndex.HEADER_SIZE);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE, 0x400);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 4, 0);
        // The table of the classes of the scenery.
        writeInt(index, 0x20, 1);
        writeInt(index, 0x24, Rac1CoreIndex.HEADER_SIZE + 0x20);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x20, 0x800);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x24, 452);
        // The table of the gadgets.
        writeInt(index, 0x80, 1);
        writeInt(index, 0x84, Rac1CoreIndex.HEADER_SIZE + 0x40);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x40, 0x1000);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x44, 168);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x48, 2048);
        // The table of the animation sequences of the hero.
        writeInt(index, 0x78, Rac1CoreIndex.HEADER_SIZE + 0x60);
        writeInt(index, Rac1CoreIndex.HEADER_SIZE + 0x60, 0x2000);
        return index;
    }

    @Test
    @DisplayName("The tables of the core index are read")
    void theTablesAreRead() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index());

        assertEquals(1, index.mobyClasses().size(), "The index holds one class of a character");
        assertEquals(0, index.mobyClass(0).classNumber());
        assertEquals(0x400, index.mobyClass(0).offset());
        assertEquals(1, index.tieClasses().size(), "The index holds one class of the scenery");
        assertEquals(452, index.tieClass(452).classNumber());
        assertEquals(0x800, index.tieClass(452).offset());
        assertNull(index.tieClass(1), "The index holds no other class of the scenery");
    }

    @Test
    @DisplayName("The table of the gadgets is read")
    void theTableOfTheGadgetsIsRead() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index());

        assertEquals(1, index.gadgets().size(), "The index holds one gadget");
        assertNotNull(index.gadget(168), "The index holds the Blaster");
        assertEquals(0x1000, index.gadget(168).offset());
        assertEquals(2048, index.gadget(168).compressedSize());
        assertNull(index.gadget(71), "The index holds no other gadget");
    }

    @Test
    @DisplayName("The animation sequences of the hero are read")
    void theSequencesOfTheHeroAreRead() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index());

        assertEquals(256, index.ratchetSequences().length);
        assertEquals(0x2000, index.ratchetSequences()[0]);
    }

    @Test
    @DisplayName("The size of the core data is read")
    void theSizeOfTheCoreDataIsRead() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index());

        assertEquals(0x10000, index.decompressedSize());
        assertEquals(0x9000, index.texturesBase());
    }

    @Test
    @DisplayName("The size of a block of the core data is measured with the blocks that follow it")
    void theSizeOfABlockIsMeasured() throws DiscFormatException {
        Rac1CoreIndex index = Rac1CoreIndex.parse(index());

        assertEquals(0x400, index.blockSize(0x400), "The class of the character is followed by the "
                + "class of the scenery");
        assertEquals(0x1800, index.blockSize(0x800), "The class of the scenery is followed by the "
                + "animation sequence of the hero");
    }

    @Test
    @DisplayName("A core index that is too short is refused")
    void aCoreIndexThatIsTooShortIsRefused() {
        assertThrows(DiscFormatException.class, () -> Rac1CoreIndex.parse(new byte[16]));
    }

    @Test
    @DisplayName("A core index without the size of the core data is refused")
    void aCoreIndexWithoutTheSizeIsRefused() {
        byte[] index = index();
        writeInt(index, 0x8C, 0);

        assertThrows(DiscFormatException.class, () -> Rac1CoreIndex.parse(index));
    }

    @Test
    @DisplayName("A table of the core index that points outside of it is refused")
    void aTableThatPointsOutsideIsRefused() {
        byte[] index = index();
        writeInt(index, 0x84, 0x10000);

        assertThrows(DiscFormatException.class, () -> Rac1CoreIndex.parse(index));
    }

    @Test
    @DisplayName("A table of the animation sequences that points outside of the index is refused")
    void aTableOfTheSequencesThatPointsOutsideIsRefused() {
        byte[] index = index();
        writeInt(index, 0x78, 0x10000);

        assertThrows(DiscFormatException.class, () -> Rac1CoreIndex.parse(index));
    }

    @Test
    @DisplayName("The bytes of the core index are kept")
    void theBytesAreKept() throws DiscFormatException {
        byte[] bytes = index();

        Rac1CoreIndex index = Rac1CoreIndex.parse(bytes);

        assertTrue(index.bytes().length >= Rac1CoreIndex.HEADER_SIZE);
        assertEquals(bytes[0x8C], index.bytes()[0x8C]);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }
}
