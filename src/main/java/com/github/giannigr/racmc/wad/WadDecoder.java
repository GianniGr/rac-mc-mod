package com.github.giannigr.racmc.wad;

import java.util.Arrays;

/**
 * The decoder of the "WAD" streams of a Ratchet &amp; Clank disc.
 *
 * <p>A WAD stream is a single asset of the game that was compressed with the LZ variant of
 * Insomniac. It is not an archive: the three bytes {@code WAD} are the magic of a compressed
 * stream, and the assets of the game are addressed by the tables of the disc, not by the stream
 * itself.
 *
 * <p>The layout of a stream is:
 *
 * <pre>
 * 0x00  3 bytes  the magic "WAD"
 * 0x03  4 bytes  the size of the compressed stream, including this header
 * 0x07  9 bytes  a short tag that names the kind of the asset, for example "coredata"
 * 0x10           the packets of the stream
 * </pre>
 *
 * <p>Every packet starts with a flag byte. The flag decides whether the packet copies literals
 * from the stream or repeats a part of the output that was written earlier:
 *
 * <table border="1">
 *   <caption>The packets of a WAD stream</caption>
 *   <tr><th>Flag</th><th>Packet</th><th>Length</th><th>Distance</th></tr>
 *   <tr><td>{@code 0x00}</td><td>long literal</td><td>next byte plus 18</td><td>-</td></tr>
 *   <tr><td>{@code 0x01}-{@code 0x0F}</td><td>literal</td><td>flag plus 3</td><td>-</td></tr>
 *   <tr><td>{@code 0x10}-{@code 0x1F}</td><td>far match</td><td>flag and 7, or next byte plus 7,
 *       plus 2</td><td>{@code 0x4000 * (A + 1) + b1 * 0x40 + (b0 >> 2)}</td></tr>
 *   <tr><td>{@code 0x20}-{@code 0x3F}</td><td>match</td><td>flag and 0x1F, or next byte plus 0x1F,
 *       plus 2</td><td>{@code b2 * 0x40 + (b1 >> 2) + 1}</td></tr>
 *   <tr><td>{@code 0x40}-{@code 0xFF}</td><td>short match</td><td>flag right 5, plus 1</td>
 *       <td>{@code b1 * 8 + ((flag >> 2) and 7) + 1}</td></tr>
 * </table>
 *
 * <p>{@code A} is bit 3 of the flag. A match of zero length is a control packet: a dummy that
 * only carries the little literals that follow it, or a block marker that makes the reader skip to
 * the next block of the stream.
 *
 * <p>The decoder is strict. A stream that does not follow the rules raises a
 * {@link WadFormatException} instead of returning half a result, because a wrong asset is worse
 * than a missing one.
 *
 * <p>The format was measured by the ReRAC project (ISC), see
 * {@code docs/formats/wad_layouts_rac1.md} of that project and the format description of the
 * OpenRAC project.
 */
public final class WadDecoder {

    /** The size of the header of a stream, in bytes. */
    public static final int HEADER_SIZE = 0x10;

    /** The magic that every stream starts with. */
    private static final byte[] MAGIC = { 'W', 'A', 'D' };

    /** The size of a block of the stream, in bytes. */
    private static final int BLOCK_SIZE = 0x1000;

    private WadDecoder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Whether the given bytes start with the magic of a compressed stream.
     *
     * @param data the bytes to look at
     * @return {@code true} when the bytes could be the start of a WAD stream
     */
    public static boolean isWad(byte[] data) {
        return data != null && data.length >= HEADER_SIZE
                && data[0] == MAGIC[0] && data[1] == MAGIC[1] && data[2] == MAGIC[2];
    }

    /**
     * The size of the compressed stream, including its header.
     *
     * @param data the bytes of the stream
     * @return the size in bytes
     * @throws WadFormatException when the bytes are not a WAD stream
     */
    public static int compressedSize(byte[] data) throws WadFormatException {
        if (!isWad(data)) {
            throw new WadFormatException("The data does not start with a WAD stream.");
        }
        long size = readUInt32LittleEndian(data, 3) & 0xFFFFFFFFL;
        if (size < HEADER_SIZE || size > data.length) {
            throw new WadFormatException("The WAD stream declares " + size
                    + " bytes, which does not fit in the " + data.length + " bytes that were read.");
        }
        return (int) size;
    }

    /**
     * Decodes a compressed stream.
     *
     * @param data the bytes of the stream
     * @param expectedSize the number of bytes the result has to have, or {@code -1} when the size
     *        is not known
     * @return the decoded bytes
     * @throws WadFormatException when the stream is damaged
     */
    public static byte[] decode(byte[] data, int expectedSize) throws WadFormatException {
        int total = compressedSize(data);
        byte[] out = new byte[Math.max(total * 2, 64)];
        int length = 0;
        int pointer = HEADER_SIZE;
        int end = total;

        while (pointer < end) {
            int flag = data[pointer++] & 0xFF;
            if (flag < 0x10) {
                int count = flag == 0 ? (data[pointer++] & 0xFF) + 18 : flag + 3;
                if (pointer + count > end) {
                    throw new WadFormatException("A literal packet of the WAD stream reaches past "
                            + "the end of the stream.");
                }
                length = append(out, length, data, pointer, count);
                pointer += count;
                if (pointer < end && (data[pointer] & 0xFF) < 0x10) {
                    throw new WadFormatException("The WAD stream holds two literal packets in a row.");
                }
                continue;
            }

            int matchLength;
            int displacement;
            int little;
            if (flag < 0x20) {
                int raw = flag & 7;
                if (raw == 0) {
                    raw = (data[pointer++] & 0xFF) + 7;
                }
                int b0 = data[pointer++] & 0xFF;
                int b1 = data[pointer++] & 0xFF;
                int far = b1 * 0x40 + (b0 >> 2);
                int a = (flag >> 3) & 1;
                if (a == 0 && far == 0) {
                    if (raw != 1) {
                        // A block marker: the reader skips to the start of the next block. The
                        // blocks are counted from the start of the packets, not from the start
                        // of the header.
                        pointer = ((pointer - HEADER_SIZE + BLOCK_SIZE - 1) & ~(BLOCK_SIZE - 1))
                                + HEADER_SIZE;
                        continue;
                    }
                    // A dummy packet that only carries the little literals.
                    int literalCount = b0 & 3;
                    length = append(out, length, data, pointer, literalCount);
                    pointer += literalCount;
                    continue;
                }
                matchLength = raw + 2;
                displacement = 0x4000 * (a + 1) + far;
                little = b0;
            } else if (flag < 0x40) {
                int raw = flag & 0x1F;
                if (raw == 0) {
                    raw = (data[pointer++] & 0xFF) + 0x1F;
                }
                int b1 = data[pointer++] & 0xFF;
                int b2 = data[pointer++] & 0xFF;
                matchLength = raw + 2;
                displacement = b2 * 0x40 + (b1 >> 2) + 1;
                little = b1;
            } else {
                int b1 = data[pointer++] & 0xFF;
                matchLength = (flag >> 5) + 1;
                displacement = b1 * 8 + ((flag >> 2) & 7) + 1;
                little = flag;
            }

            if (displacement <= 0 || displacement > length) {
                throw new WadFormatException("A match of the WAD stream reaches before the start "
                        + "of the output.");
            }
            int source = length - displacement;
            for (int index = 0; index < matchLength; index++) {
                out = ensure(out, length + 1);
                out[length++] = out[source + index];
            }
            int literalCount = little & 3;
            if (pointer + literalCount > end) {
                throw new WadFormatException("The little literals of a WAD packet reach past the "
                        + "end of the stream.");
            }
            length = append(out, length, data, pointer, literalCount);
            pointer += literalCount;
        }

        byte[] result = Arrays.copyOf(out, length);
        if (expectedSize >= 0 && result.length != expectedSize) {
            throw new WadFormatException("The WAD stream decoded to " + result.length
                    + " bytes, but " + expectedSize + " bytes were expected.");
        }
        return result;
    }

    /**
     * Decodes a compressed stream whose size is not known.
     *
     * @param data the bytes of the stream
     * @return the decoded bytes
     * @throws WadFormatException when the stream is damaged
     */
    public static byte[] decode(byte[] data) throws WadFormatException {
        return decode(data, -1);
    }

    private static int append(byte[] out, int length, byte[] data, int pointer, int count) {
        byte[] target = ensure(out, length + count);
        System.arraycopy(data, pointer, target, length, count);
        return length + count;
    }

    private static byte[] ensure(byte[] out, int needed) {
        if (needed <= out.length) {
            return out;
        }
        int size = Math.max(needed, out.length * 2);
        return Arrays.copyOf(out, size);
    }

    private static long readUInt32LittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }
}
