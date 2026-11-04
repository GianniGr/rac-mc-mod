package com.github.giannigr.racmc.asset;

/**
 * The animation sequences of the game that the checks are built from.
 *
 * <p>The sequences are built by hand: a header, the pointers of the keys and the keys themselves
 * with the quaternions of the joints and the records of the joints whose translation or scale
 * differ from the rest pose. No data of the game is used anywhere.
 *
 * <p>Every key of a sequence holds the scale of the first joint, which reaches its children, and
 * the translation of the second joint. The height of that translation and the size of that scale
 * are given by the caller, so that a check can tell the keys of a sequence apart.
 */
final class SyntheticMobySequence {

    /** The number of the joints of the skeleton of the sequences. */
    static final int JOINTS = 2;

    private SyntheticMobySequence() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a sequence whose keys hold the same records.
     *
     * @param rates the rate of every key, in keys per second
     * @return the bytes of the sequence
     */
    static byte[] sequence(float[] rates) {
        return sequence(rates, fill(rates.length, 1.0f), fill(rates.length, 1.5f));
    }

    /**
     * Builds a sequence whose keys hold the records of the first joint and of the second one.
     *
     * @param rates the rate of every key, in keys per second
     * @param heights the height of the translation of the second joint of every key, along the
     *        third axis
     * @return the bytes of the sequence
     */
    static byte[] sequence(float[] rates, float[] heights) {
        return sequence(rates, heights, fill(rates.length, 1.5f));
    }

    /**
     * Builds a sequence whose keys hold the records of the first joint and of the second one.
     *
     * @param rates the rate of every key, in keys per second
     * @param heights the height of the translation of the second joint of every key, along the
     *        third axis
     * @param sizes the size of the scale of the first joint of every key
     * @return the bytes of the sequence
     */
    static byte[] sequence(float[] rates, float[] heights, float[] sizes) {
        int frames = rates.length;
        int quaternionBytes = JOINTS * 8;
        int scales = 1;
        int translations = 1;
        int quadwords = (quaternionBytes + scales * 8 + translations * 8 + 15) / 16;
        int frameSize = MobySequenceReader.FRAME_HEADER_SIZE + quadwords * 16;
        byte[] blob = new byte[MobySequenceReader.SEQUENCE_HEADER_SIZE + frames * 4
                + frames * frameSize];
        blob[0x10] = (byte) frames;
        for (int frame = 0; frame < frames; frame++) {
            int at = MobySequenceReader.SEQUENCE_HEADER_SIZE + frames * 4 + frame * frameSize;
            writeInt(blob, MobySequenceReader.SEQUENCE_HEADER_SIZE + frame * 4, at);
            writeFloat(blob, at, rates[frame]);
            writeShort(blob, at + 6, quadwords);
            writeShort(blob, at + 8, quaternionBytes);
            writeShort(blob, at + 0x0A, scales);
            writeShort(blob, at + 0x0C, quaternionBytes + scales * 8);
            writeShort(blob, at + 0x0E, translations);
            int payload = at + MobySequenceReader.FRAME_HEADER_SIZE;
            // The quaternions of the joints are the identity.
            for (int joint = 0; joint < JOINTS; joint++) {
                writeShort(blob, payload + joint * 8 + 6, 0x7FFF);
            }
            // The scale of the first joint is the size of the key and reaches its children.
            writeShort(blob, payload + quaternionBytes, Math.round(sizes[frame] * 4096.0f));
            writeShort(blob, payload + quaternionBytes + 2, Math.round(sizes[frame] * 4096.0f));
            writeShort(blob, payload + quaternionBytes + 4, Math.round(sizes[frame] * 4096.0f));
            blob[payload + quaternionBytes + 6] = 0;
            blob[payload + quaternionBytes + 7] = (byte) 0x80;
            // The translation of the second joint is the height of the key.
            int translationAt = payload + quaternionBytes + scales * 8;
            writeShort(blob, translationAt, 0);
            writeShort(blob, translationAt + 2, 0);
            writeShort(blob, translationAt + 4, Math.round(heights[frame] * 1024.0f));
            blob[translationAt + 6] = 1;
        }
        return blob;
    }

    /**
     * A number for every key of a sequence.
     *
     * @param keys the number of the keys
     * @param value the number of every key
     * @return the numbers
     */
    private static float[] fill(int keys, float value) {
        float[] values = new float[keys];
        for (int key = 0; key < keys; key++) {
            values[key] = value;
        }
        return values;
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
