package com.github.giannigr.racmc.disc;

/**
 * The reader of the little endian numbers of the tables of a disc.
 *
 * <p>The game stores its tables in the little endian order of the processor of the console. Every
 * number of the tables of the disc is read with the helpers of this class.
 */
final class DiscBytes {

    private DiscBytes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads an unsigned byte.
     *
     * @param data the buffer to read from
     * @param offset the offset of the byte
     * @return the value of the byte
     */
    static int u8(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    /**
     * Reads a signed byte.
     *
     * @param data the buffer to read from
     * @param offset the offset of the byte
     * @return the value of the byte
     */
    static int i8(byte[] data, int offset) {
        return data[offset];
    }

    /**
     * Reads an unsigned 16 bit number.
     *
     * @param data the buffer to read from
     * @param offset the offset of the number
     * @return the value of the number
     */
    static int u16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Reads a signed 16 bit number.
     *
     * @param data the buffer to read from
     * @param offset the offset of the number
     * @return the value of the number
     */
    static int i16(byte[] data, int offset) {
        return (short) u16(data, offset);
    }

    /**
     * Reads an unsigned 32 bit number.
     *
     * @param data the buffer to read from
     * @param offset the offset of the number
     * @return the value of the number
     */
    static long u32(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    /**
     * Reads a signed 32 bit number.
     *
     * @param data the buffer to read from
     * @param offset the offset of the number
     * @return the value of the number
     */
    static int i32(byte[] data, int offset) {
        return (int) u32(data, offset);
    }

    /**
     * Reads a 32 bit floating point number.
     *
     * @param data the buffer to read from
     * @param offset the offset of the number
     * @return the value of the number
     */
    static float f32(byte[] data, int offset) {
        return Float.intBitsToFloat(i32(data, offset));
    }

    /**
     * Whether the range lies inside the buffer.
     *
     * @param data the buffer to check against
     * @param offset the offset of the range
     * @param length the length of the range
     * @return {@code true} when the range lies inside the buffer
     */
    static boolean inBounds(byte[] data, int offset, int length) {
        return offset >= 0 && length >= 0 && offset <= data.length - length;
    }
}
