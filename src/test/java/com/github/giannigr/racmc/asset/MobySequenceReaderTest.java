package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the animation sequences of the game.
 *
 * <p>The sequences that are read are built by hand: a header, the pointers of the keys and the
 * keys themselves with the quaternions of the joints and the records of the joints whose
 * translation or scale differ from the rest pose. No data of the game is used anywhere.
 */
class MobySequenceReaderTest {

    /** The number of the joints of the skeleton of the checks. */
    private static final int JOINTS = 2;

    /**
     * Builds a sequence with the given keys.
     *
     * <p>Every key holds the quaternion of every joint, one scale record and one translation
     * record. The quaternions of the joints are the identity, the scale of the joint 0 is one and
     * a half and the translation of the joint 1 is one unit.
     *
     * @param rates the rate of every key, in keys per second
     * @return the bytes of the sequence
     */
    private static byte[] sequence(float[] rates) {
        int frames = rates.length;
        int quaternionBytes = JOINTS * 8;
        int scales = 1;
        int translations = 1;
        int quadwords = (quaternionBytes + scales * 8 + translations * 8 + 15) / 16;
        int frameSize = MobySequenceReader.FRAME_HEADER_SIZE + quadwords * 16;
        byte[] blob = new byte[MobySequenceReader.SEQUENCE_HEADER_SIZE + frames * 4
                + frames * frameSize];
        blob[offset(0) + 0x10] = (byte) frames;
        for (int frame = 0; frame < frames; frame++) {
            int at = MobySequenceReader.SEQUENCE_HEADER_SIZE + frame * 4;
            writeInt(blob, at, MobySequenceReader.SEQUENCE_HEADER_SIZE + frames * 4 + frame * frameSize);
            int frameAt = MobySequenceReader.SEQUENCE_HEADER_SIZE + frames * 4 + frame * frameSize;
            writeFloat(blob, frameAt, rates[frame]);
            writeShort(blob, frameAt + 6, quadwords);
            writeShort(blob, frameAt + 8, quaternionBytes);
            writeShort(blob, frameAt + 0x0A, scales);
            writeShort(blob, frameAt + 0x0C, quaternionBytes + scales * 8);
            writeShort(blob, frameAt + 0x0E, translations);
            int payloadAt = frameAt + MobySequenceReader.FRAME_HEADER_SIZE;
            // The quaternions of the joints are the identity.
            for (int joint = 0; joint < JOINTS; joint++) {
                writeShort(blob, payloadAt + joint * 8 + 6, 0x7FFF);
            }
            // The scale of the joint 0 is one and a half.
            writeShort(blob, payloadAt + quaternionBytes, 0x1800);
            writeShort(blob, payloadAt + quaternionBytes + 2, 0x1800);
            writeShort(blob, payloadAt + quaternionBytes + 4, 0x1800);
            blob[payloadAt + quaternionBytes + 6] = 0;
            blob[payloadAt + quaternionBytes + 7] = (byte) 0x80;
            // The translation of the joint 1 is one unit.
            int translationAt = payloadAt + quaternionBytes + scales * 8;
            writeShort(blob, translationAt, 0);
            writeShort(blob, translationAt + 2, 0);
            writeShort(blob, translationAt + 4, 0x0400);
            blob[translationAt + 6] = 1;
        }
        return blob;
    }

    /**
     * The offset of the header of the sequence of the checks.
     *
     * @param index the number of the key
     * @return the offset
     */
    private static int offset(int index) {
        return 0;
    }

    @Test
    @DisplayName("The keys of a sequence are read")
    void theKeysAreRead() throws AssetFormatException {
        byte[] blob = sequence(new float[] { 0.125f, 0.125f });

        MobySequenceReader.Keys keys = MobySequenceReader.read(blob, 0, JOINTS);

        assertEquals(3, keys.times().length, "The sequence holds two keys and the key it loops to");
        assertEquals(0.0f, keys.times()[0], 1.0e-6f);
        assertEquals(8.0f / 60.0f, keys.times()[1], 1.0e-6f,
                "A rate of an eighth of a second is eight ticks of the game");
        assertTrue(keys.loop(), "The sequence repeats itself");
        assertEquals(3 * JOINTS * 4, keys.rotations().length,
                "The sequence holds two keys and the key it loops to");
        assertEquals(0.99997f, keys.rotations()[3], 1.0e-4f,
                "The identity of the game is the quaternion (0, 0, 0, 0x7fff)");
    }

    @Test
    @DisplayName("The records of the channels belong to their key")
    void theRecordsOfTheChannelsBelongToTheirKey() throws AssetFormatException {
        byte[] blob = sequence(new float[] { 0.125f, 0.125f });

        MobySequenceReader.Keys keys = MobySequenceReader.read(blob, 0, JOINTS);

        assertEquals(2, keys.scaleKeys().length, "Every key of the sequence holds a scale");
        for (int index = 0; index < keys.scaleKeys().length; index++) {
            assertTrue(keys.scaleKeys()[index] < keys.times().length,
                    "The scale belongs to a key of the sequence");
        }
        assertEquals(0, keys.scaleJoints()[0]);
        assertTrue(keys.scaleInherited()[0], "The scale of the record reaches the children");
        assertEquals(1.5f, keys.scaleValues()[0], 1.0e-5f);
        assertEquals(1, keys.translationJoints()[0]);
        assertEquals(1.0f, keys.translationValues()[2], 1.0e-5f);
    }

    @Test
    @DisplayName("A key that has the same time as the key before it is dropped")
    void aKeyWithTheSameTimeIsDropped() throws AssetFormatException {
        // The second key has the time of the first one, so it is dropped.
        byte[] blob = sequence(new float[] { Float.POSITIVE_INFINITY, 0.125f });

        MobySequenceReader.Keys keys = MobySequenceReader.read(blob, 0, JOINTS);

        assertEquals(2, keys.times().length, "The key with the same time was dropped");
        for (int index = 0; index < keys.scaleKeys().length; index++) {
            assertTrue(keys.scaleKeys()[index] < keys.times().length,
                    "The scale of the dropped key was not dropped with it");
        }
        for (int index = 0; index < keys.translationKeys().length; index++) {
            assertTrue(keys.translationKeys()[index] < keys.times().length,
                    "The translation of the dropped key was not dropped with it");
        }
    }

    @Test
    @DisplayName("A sequence without keys is refused")
    void aSequenceWithoutKeysIsRefused() {
        byte[] blob = new byte[MobySequenceReader.SEQUENCE_HEADER_SIZE + 16];

        assertThrows(AssetFormatException.class, () -> MobySequenceReader.read(blob, 0, JOINTS));
    }

    @Test
    @DisplayName("A key whose payload does not fit is refused")
    void aKeyWithABrokenPayloadIsRefused() throws AssetFormatException {
        byte[] blob = sequence(new float[] { 0.125f });

        writeShort(blob, MobySequenceReader.SEQUENCE_HEADER_SIZE + 4 + 8, 0);

        assertThrows(AssetFormatException.class, () -> MobySequenceReader.read(blob, 0, JOINTS));
    }

    @Test
    @DisplayName("A key of the special format of the game is refused")
    void aKeyOfTheSpecialFormatIsRefused() throws AssetFormatException {
        byte[] blob = sequence(new float[] { 0.125f });

        writeInt(blob, MobySequenceReader.SEQUENCE_HEADER_SIZE, 0x80000000);

        assertThrows(AssetFormatException.class, () -> MobySequenceReader.read(blob, 0, JOINTS));
    }

    @Test
    @DisplayName("A sequence that lies outside of its data is refused")
    void aSequenceOutsideOfItsDataIsRefused() {
        byte[] blob = new byte[16];

        assertThrows(AssetFormatException.class, () -> MobySequenceReader.read(blob, 0, JOINTS));
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

    private static void writeFloat(byte[] data, int offset, float value) {
        writeInt(data, offset, Float.floatToRawIntBits(value));
    }
}
