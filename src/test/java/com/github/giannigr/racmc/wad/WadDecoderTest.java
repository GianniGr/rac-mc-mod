package com.github.giannigr.racmc.wad;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the decoder of the compressed streams of the game.
 *
 * <p>The streams of the checks are built by hand. No data of the game is used anywhere.
 */
class WadDecoderTest {

    /**
     * Builds a stream out of the given packets.
     */
    private static byte[] stream(byte[] packets) {
        byte[] result = new byte[0x10 + packets.length];
        result[0] = 'W';
        result[1] = 'A';
        result[2] = 'D';
        int size = result.length;
        result[3] = (byte) (size & 0xFF);
        result[4] = (byte) ((size >> 8) & 0xFF);
        result[5] = (byte) ((size >> 16) & 0xFF);
        result[6] = (byte) ((size >> 24) & 0xFF);
        System.arraycopy(packets, 0, result, 0x10, packets.length);
        return result;
    }

    @Test
    @DisplayName("A literal packet is copied and a short match repeats it")
    void aLiteralAndAMatchAreDecoded() throws WadFormatException {
        byte[] decoded = WadDecoder.decode(stream(new byte[] { 0x01, 'a', 'b', 'c', 'd', (byte) 0xC0,
                0x00 }));

        assertArrayEquals("abcdddddddd".getBytes(StandardCharsets.ISO_8859_1), decoded);
    }

    @Test
    @DisplayName("A match with little literals is decoded")
    void aMatchWithLittleLiteralsIsDecoded() throws WadFormatException {
        byte[] decoded = WadDecoder.decode(stream(new byte[] { 0x01, 'x', 'y', 'z', 'w', 0x21, 0x02,
                0x00, '!', '?' }));

        assertArrayEquals("xyzwwww!?".getBytes(StandardCharsets.ISO_8859_1), decoded);
    }

    @Test
    @DisplayName("A dummy packet and a block marker are decoded")
    void aDummyPacketAndABlockMarkerAreDecoded() throws WadFormatException {
        byte[] dummy = WadDecoder.decode(stream(new byte[] { 0x01, 'a', 'b', 'c', 'd', 0x11, 0x02,
                0x00, 'e', 'f' }));
        assertArrayEquals("abcdef".getBytes(StandardCharsets.ISO_8859_1), dummy);

        byte[] marker = WadDecoder.decode(stream(new byte[] { 0x01, 'a', 'b', 'c', 'd', 0x12, 0x00,
                0x00, (byte) 0xEE }));
        assertArrayEquals("abcd".getBytes(StandardCharsets.ISO_8859_1), marker);
    }

    @Test
    @DisplayName("Two literal packets in a row are refused")
    void twoLiteralPacketsInARowAreRefused() {
        byte[] broken = stream(new byte[] { 0x01, 'a', 'b', 'c', 'd', 0x01, 'e', 'f', 'g', 'h' });

        assertThrows(WadFormatException.class, () -> WadDecoder.decode(broken));
    }

    @Test
    @DisplayName("Data that is not a stream is refused")
    void dataThatIsNotAStreamIsRefused() {
        byte[] broken = new byte[64];

        assertThrows(WadFormatException.class, () -> WadDecoder.decode(broken));
        assertThrows(WadFormatException.class, () -> WadDecoder.compressedSize(broken));
    }

    @Test
    @DisplayName("A stream that claims to be longer than it is refused")
    void aStreamThatIsTooShortIsRefused() {
        byte[] broken = stream(new byte[] { 0x01, 'a', 'b', 'c', 'd' });
        broken[3] = (byte) 0xFF;
        broken[4] = (byte) 0xFF;

        assertThrows(WadFormatException.class, () -> WadDecoder.decode(broken));
    }

    @Test
    @DisplayName("A stream that decodes to the wrong size is refused")
    void aStreamOfTheWrongSizeIsRefused() throws WadFormatException {
        byte[] data = stream(new byte[] { 0x01, 'a', 'b', 'c', 'd' });

        assertThrows(WadFormatException.class, () -> WadDecoder.decode(data, 5));
        assertEquals(4, WadDecoder.decode(data, 4).length);
    }

    @Test
    @DisplayName("A match that reaches before the start of the output is refused")
    void aMatchBeforeTheStartIsRefused() {
        byte[] broken = stream(new byte[] { (byte) 0xC0, 0x00 });

        WadFormatException failure = assertThrows(WadFormatException.class,
                () -> WadDecoder.decode(broken));
        assertTrue(failure.getMessage().contains("before the start"),
                "The message has to say what is wrong: " + failure.getMessage());
    }
}
