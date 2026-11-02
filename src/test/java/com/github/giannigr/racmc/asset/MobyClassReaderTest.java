package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the classes of the characters of the game.
 *
 * <p>The class that is read is built by hand. It holds the joint lists of the class, which name
 * the chain of joints the items of the hero hang on. No data of the game is used anywhere.
 */
class MobyClassReaderTest {

    /**
     * Builds a class with one joint list.
     *
     * @param chain the joints of the chain
     * @param target the joints of the second list
     * @return the bytes of the class
     */
    private static byte[] classBlob(byte[] chain, byte[] target) {
        byte[] blob = new byte[0x100];
        // The header of the class.
        writeInt(blob, 0x1C, 0x40);
        // The table of the joint lists.
        writeInt(blob, 0x40, 1);
        writeInt(blob, 0x44, 0x50);
        // The list itself.
        writeShort(blob, 0x50, chain.length);
        writeShort(blob, 0x52, target.length);
        System.arraycopy(chain, 0, blob, 0x54, chain.length);
        System.arraycopy(target, 0, blob, 0x54 + chain.length, target.length);
        blob[0x54 + chain.length + target.length] = (byte) 0xFF;
        return blob;
    }

    @Test
    @DisplayName("The joint lists of a class are read")
    void theJointListsAreRead() throws AssetFormatException {
        MobyClassReader reader = MobyClassReader.of(
                classBlob(new byte[] { 0, 1, 2, 3, 4, 5, 6, 52, 53, 54, 55, 56 },
                        new byte[] { 42 }));

        List<MobyClassReader.JointList> lists = reader.readJointLists();

        assertEquals(1, lists.size());
        assertEquals(12, lists.get(0).chain().length);
        assertEquals(56, lists.get(0).chain()[11], "The chain ends at the joint of the hand");
        assertEquals(1, lists.get(0).target().length);
        assertEquals(42, lists.get(0).target()[0]);
    }

    @Test
    @DisplayName("A class without joint lists has none")
    void aClassWithoutJointListsHasNone() throws AssetFormatException {
        byte[] blob = new byte[0x48];

        MobyClassReader reader = MobyClassReader.of(blob);

        assertTrue(reader.readJointLists().isEmpty());
    }

    @Test
    @DisplayName("A joint list that lies outside of the class is refused")
    void aJointListOutsideOfTheClassIsRefused() throws AssetFormatException {
        byte[] blob = classBlob(new byte[] { 1 }, new byte[] { 2 });
        writeInt(blob, 0x44, 0x10000);

        MobyClassReader reader = MobyClassReader.of(blob);

        assertThrows(AssetFormatException.class, reader::readJointLists);
    }

    @Test
    @DisplayName("A joint list with an implausible size is refused")
    void aJointListWithAnImplausibleSizeIsRefused() throws AssetFormatException {
        byte[] blob = classBlob(new byte[] { 1 }, new byte[] { 2 });
        writeShort(blob, 0x50, 0x7FFF);

        MobyClassReader reader = MobyClassReader.of(blob);

        assertThrows(AssetFormatException.class, reader::readJointLists);
    }

    @Test
    @DisplayName("A matrix of a joint that collapsed is recognised")
    void aMatrixOfAJointThatCollapsedIsRecognised() {
        float[] sane = new float[16];
        sane[0] = 1.0f;
        sane[5] = 1.0f;
        sane[10] = 1.0f;
        sane[15] = 1.0f;
        assertTrue(!Rac1AssetExtractor.degenerate(sane),
                "The matrix of a joint that is in shape must not be refused");

        float[] collapsed = sane.clone();
        collapsed[0] = 1.0e12f;
        assertTrue(Rac1AssetExtractor.degenerate(collapsed),
                "The matrix of a joint that collapsed must be refused");

        float[] broken = sane.clone();
        broken[5] = Float.NaN;
        assertTrue(Rac1AssetExtractor.degenerate(broken),
                "A matrix that holds a number that is not finite must be refused");
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
