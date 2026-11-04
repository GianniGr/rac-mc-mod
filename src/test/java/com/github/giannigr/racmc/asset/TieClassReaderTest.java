package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the classes of the scenery of the game.
 *
 * <p>The class that is read is built by hand. It holds one packet with five vertices and one
 * strip, which makes three triangles. No data of the game is used anywhere.
 */
class TieClassReaderTest {

    /** The number of the vertices of the class of the checks. */
    private static final int VERTEX_COUNT = 5;

    /**
     * Builds a class of the scenery with one packet and one strip.
     *
     * @return the bytes of the class
     */
    private static byte[] classBlob() {
        byte[] blob = new byte[0x450];
        // The header of the class.
        writeInt(blob, 0x00, 0x140);
        writeInt(blob, 0x0C, 0x400);
        blob[0x20] = 1;
        blob[0x23] = 1;
        writeInt(blob, 0x2C, 0x300);
        writeFloat(blob, 0x40, 1.0f);
        writeInt(blob, 0x44, 452);
        // The table of the packets.
        writeInt(blob, 0x140, 0x10);
        blob[0x144] = 1;
        blob[0x147] = 4;
        blob[0x148] = 8;
        blob[0x149] = 5;
        blob[0x14A] = 14;
        blob[0x14B] = 2;
        blob[0x14E] = 1;
        blob[0x14F] = VERTEX_COUNT;
        // The data of the packet.
        int data = 0x150;
        // The header of the unpack.
        blob[data + 0x20] = 1;
        blob[data + 0x21] = 1;
        blob[data + 0x23] = 1;
        blob[data + 0x24] = 10;
        blob[data + 0x28] = VERTEX_COUNT * 2 + 4;
        blob[data + 0x2A] = VERTEX_COUNT;
        // The strip.
        blob[data + 0x2C] = VERTEX_COUNT;
        blob[data + 0x2E] = 6;
        // The vertices: the strip reads the places 7, 10, 13, 16 and 19.
        int[] slots = { 7, 10, 13, 16, 19 };
        for (int index = 0; index < VERTEX_COUNT; index++) {
            int at = data + 128 + index * 0x10;
            writeShort(blob, at, index * 10);
            writeShort(blob, at + 2, index * 20);
            writeShort(blob, at + 4, index * 30);
            writeShort(blob, at + 6, slots[index]);
            writeShort(blob, at + 8, index * 100);
            writeShort(blob, at + 10, index * 200);
        }
        // The colours of the vertices.
        for (int index = 0; index < VERTEX_COUNT; index++) {
            blob[data + 224 + index] = (byte) index;
        }
        return blob;
    }

    @Test
    @DisplayName("A class of the scenery is read")
    void aClassIsRead() throws AssetFormatException {
        TieClassReader reader = TieClassReader.of(classBlob());

        assertEquals(452, reader.classNumber());
        assertEquals(1.0f, reader.scale());
        assertEquals(1, reader.textureCount());

        TieClassReader.Geometry geometry = reader.readGeometry();
        assertEquals(VERTEX_COUNT, geometry.vertexCount());
        assertEquals(3, geometry.triangleCount(), "A strip of five vertices makes three triangles");
        for (int material : geometry.materials()) {
            assertEquals(0, material, "The class has one material");
        }
        assertEquals(0.0f, geometry.positions()[0]);
        assertEquals(10.0f / 1024.0f, geometry.positions()[3]);
        assertEquals(0.0f, geometry.uvs()[0]);
        assertEquals(100.0f / 4096.0f, geometry.uvs()[2]);
    }

    @Test
    @DisplayName("A class whose header is too short is refused")
    void aClassWithoutAHeaderIsRefused() {
        assertThrows(AssetFormatException.class, () -> TieClassReader.of(new byte[16]));
    }

    @Test
    @DisplayName("A class whose packet table lies outside of the class is refused")
    void aClassWithABrokenPacketTableIsRefused() throws AssetFormatException {
        byte[] blob = classBlob();
        writeInt(blob, 0x00, 0x10000);

        TieClassReader reader = TieClassReader.of(blob);
        AssetFormatException failure = assertThrows(AssetFormatException.class,
                reader::readGeometry);
        assertTrue(failure.getMessage().contains("outside"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A class whose vertices do not fit into their region is refused")
    void aClassWithTooManyVerticesIsRefused() throws AssetFormatException {
        byte[] blob = classBlob();
        blob[0x149] = 4;

        TieClassReader reader = TieClassReader.of(blob);
        assertThrows(AssetFormatException.class, reader::readGeometry);
    }

    @Test
    @DisplayName("A class whose strip does not start where the graphics packet says is refused")
    void aClassWithABrokenStripIsRefused() throws AssetFormatException {
        byte[] blob = classBlob();
        blob[0x150 + 0x2E] = 9;

        TieClassReader reader = TieClassReader.of(blob);
        assertThrows(AssetFormatException.class, reader::readGeometry);
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
