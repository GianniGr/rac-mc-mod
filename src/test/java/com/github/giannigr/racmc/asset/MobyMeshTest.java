package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the classes of the characters with more than one packet.
 *
 * <p>The class that is read is built by hand: it holds two packets, every packet with three
 * vertices and one triangle. The vertices of the second packet stand a unit above the vertices of
 * the first packet, so the triangles of the two packets are two different triangles.
 *
 * <p>No data of the game is used anywhere.
 */
class MobyMeshTest {

    /** The number of the vertices of every packet of the class of the checks. */
    private static final int VERTICES_PER_PACKET = 3;

    /** The number of the packets of the class of the checks. */
    private static final int PACKETS = 2;

    /**
     * Builds a class of a character with the given number of packets.
     *
     * <p>Every packet holds three vertices and one triangle. The vertices of the first packet
     * stand in the middle of the ground, the vertices of the second packet one unit above them.
     *
     * @param packets the number of the packets of the class
     * @param indexOfTheLastVertex the index of the last vertex of the last packet, counted from
     *        the first vertex of the class, or {@code -1} when the indices of the last packet
     *        name a vertex that does not exist
     * @return the bytes of the class
     */
    private static byte[] classBlob(int packets, int indexOfTheLastVertex) {
        byte[] blob = new byte[0x1000];
        int packetTable = 0x48;
        // The header of the class.
        writeInt(blob, 0x00, packetTable);
        blob[0x04] = (byte) packets;
        blob[0x08] = 1;
        writeInt(blob, 0x14, 0x800);
        writeInt(blob, 0x18, 0x880);
        writeFloat(blob, 0x24, 1.0f);
        // The skeleton of the one joint of the class.
        writeInt(blob, 0x800 + 0 * 0x40 + 0 * 4, 0x3F800000);
        writeInt(blob, 0x800 + 0 * 0x40 + 4 * 4, 0x3F800000);
        writeInt(blob, 0x800 + 0 * 0x40 + 8 * 4, 0x3F800000);
        writeInt(blob, 0x800 + 0 * 0x40 + 12 * 4, 0x3F800000);

        int listAt = 0x900;
        int vertexAt = 0xB00;
        for (int packet = 0; packet < packets; packet++) {
            int entry = packetTable + packet * 0x10;
            // The command list of the packet: the coordinates of the texture, the indices and the
            // materials.
            int list = listAt;
            int coordinates = 12;
            int indices = 12;
            int materials = 64;
            byte[] commandList = new byte[16 + coordinates + indices + materials];
            // The unpack of the coordinates of the texture: two numbers of 16 bits for every
            // vertex.
            writeCommand(commandList, 0, 0x65, VERTICES_PER_PACKET, 0, coordinates);
            for (int vertex = 0; vertex < VERTICES_PER_PACKET; vertex++) {
                writeShort(commandList, 4 + vertex * 4, vertex * 100);
                writeShort(commandList, 4 + vertex * 4 + 2, vertex * 200);
            }
            // The unpack of the indices: four numbers of 8 bits for every element. The first
            // four bytes of it are a header, the rest are the indices of the triangle.
            int indexAt = 16;
            writeCommand(commandList, indexAt, 0x6E, 3, 0, indices);
            commandList[indexAt + 4 + 2] = 0;
            int last = packet == packets - 1 && indexOfTheLastVertex >= 0
                    ? indexOfTheLastVertex + 1 - packet * VERTICES_PER_PACKET
                    : VERTICES_PER_PACKET;
            for (int index = 0; index < 6; index++) {
                commandList[indexAt + 8 + index] = (byte) (index < last ? index + 1 : last);
            }
            // The end of the list of the indices.
            commandList[indexAt + 8 + 6] = 0;
            // The unpack of the materials: one block with the texture zero.
            int materialAt = 32;
            writeCommand(commandList, materialAt, 0x6C, 4, 0, materials);
            writeInt(commandList, materialAt + 4 + 0x20, 0);
            System.arraycopy(commandList, 0, blob, list, commandList.length);
            writeInt(blob, entry, list);
            int listSize = (commandList.length + 15) / 16;
            writeShort(blob, entry + 4, listSize);
            writeInt(blob, entry + 8, vertexAt);
            blob[entry + 0x0C] = (byte) ((VERTEX_TABLE_HEADER + (VERTICES_PER_PACKET + 1) * 0x10)
                    / 16);
            blob[entry + 0x0F] = (byte) VERTICES_PER_PACKET;

            // The table of the vertices of the packet.
            int header = vertexAt;
            writeInt(blob, header + 0x0C, VERTICES_PER_PACKET);
            writeInt(blob, header + 0x14, VERTICES_PER_PACKET);
            writeInt(blob, header + 0x18, VERTEX_TABLE_HEADER);
            writeInt(blob, header + 0x1C, VERTEX_TABLE_HEADER + (VERTICES_PER_PACKET + 1) * 0x10);
            for (int vertex = 0; vertex < VERTICES_PER_PACKET; vertex++) {
                int at = header + VERTEX_TABLE_HEADER + vertex * 0x10;
                // The joint of the vertex, the places of the memory of the vector unit and the
                // place of the vertex.
                blob[at + 1] = 0;
                blob[at + 2] = 0;
                blob[at + 3] = 0;
                // The normal of the vertex: straight up.
                blob[at + 8] = 0;
                blob[at + 9] = 64;
                // The place of the vertex: the packets stand above each other.
                int height = packet * 1024;
                writeShort(blob, at + 0x0A, vertex * 100);
                writeShort(blob, at + 0x0C, vertex * 200);
                writeShort(blob, at + 0x0E, height);
            }
            listAt += listSize * 16;
            vertexAt += VERTEX_TABLE_HEADER + (VERTICES_PER_PACKET + 1) * 0x10;
        }
        return blob;
    }

    /** The size of the header of the table of the vertices of a packet. */
    private static final int VERTEX_TABLE_HEADER = 0x20;

    @Test
    @DisplayName("The indices of the second packet name the vertices of the second packet")
    void theIndicesOfTheSecondPacketNameItsOwnVertices() throws AssetFormatException {
        MobyClassReader reader = MobyClassReader.of(classBlob(PACKETS, -1));

        MobyClassReader.Mesh mesh = reader.readMesh();

        assertEquals(PACKETS * VERTICES_PER_PACKET, mesh.vertexCount(),
                "The class holds the vertices of both packets");
        assertEquals(PACKETS, mesh.triangleCount(), "Every packet holds one triangle");
        // The first triangle names the first three vertices of the class.
        assertEquals(0, mesh.indices()[0]);
        assertEquals(1, mesh.indices()[1]);
        assertEquals(2, mesh.indices()[2]);
        // The second triangle names the three vertices of the second packet.
        assertEquals(VERTICES_PER_PACKET, mesh.indices()[3],
                "The indices of the second packet were not moved onto its vertices");
        assertEquals(VERTICES_PER_PACKET + 1, mesh.indices()[4]);
        assertEquals(VERTICES_PER_PACKET + 2, mesh.indices()[5]);
    }

    @Test
    @DisplayName("Every index of a mesh names a vertex of the mesh")
    void everyIndexNamesAVertexOfTheMesh() throws AssetFormatException {
        MobyClassReader reader = MobyClassReader.of(classBlob(PACKETS, -1));

        MobyClassReader.Mesh mesh = reader.readMesh();

        for (int index : mesh.indices()) {
            assertTrue(index >= 0 && index < mesh.vertexCount(),
                    "The index " + index + " names no vertex of the mesh");
        }
    }

    @Test
    @DisplayName("The triangles of the packets of a class are different triangles")
    void theTrianglesOfThePacketsAreDifferent() throws AssetFormatException {
        MobyClassReader reader = MobyClassReader.of(classBlob(PACKETS, -1));

        MobyClassReader.Mesh mesh = reader.readMesh();

        Set<Integer> first = new HashSet<>();
        for (int corner = 0; corner < 3; corner++) {
            first.add(mesh.indices()[corner]);
        }
        Set<Integer> second = new HashSet<>();
        for (int corner = 3; corner < 6; corner++) {
            second.add(mesh.indices()[corner]);
        }
        assertNotEquals(first, second, "The two packets drew the same triangle");
        // The vertices of the second packet stand a unit above the vertices of the first one.
        float firstHeight = mesh.positions()[2];
        float secondHeight = mesh.positions()[(VERTICES_PER_PACKET + 2) * 3 + 2];
        assertEquals(1.0f, secondHeight - firstHeight, 1.0e-4f,
                "The vertices of the second packet do not stand above the first packet");
    }

    @Test
    @DisplayName("An index that names a vertex that does not exist is refused")
    void anIndexThatNamesNoVertexIsRefused() throws AssetFormatException {
        // The last packet names a vertex that lies behind the last one of the class.
        MobyClassReader reader = MobyClassReader.of(classBlob(PACKETS, PACKETS * VERTICES_PER_PACKET));

        AssetFormatException failure = assertThrows(AssetFormatException.class, reader::readMesh);
        assertTrue(failure.getMessage().contains("vertex"),
                "The message has to say what is wrong: " + failure.getMessage());
    }

    @Test
    @DisplayName("A class without a mesh is refused")
    void aClassWithoutAMeshIsRefused() throws AssetFormatException {
        byte[] blob = classBlob(PACKETS, -1);
        writeInt(blob, 0x00, 0);

        MobyClassReader reader = MobyClassReader.of(blob);

        assertThrows(AssetFormatException.class, reader::readMesh);
    }

    /**
     * The triangles of a packet come out in the order of its strip.
     */
    @Test
    @DisplayName("The triangles of a packet come out in the order of its strip")
    void theTrianglesComeOutInTheOrderOfTheStrip() throws AssetFormatException {
        // A strip of five vertices makes three triangles that follow each other, and their
        // facing alternates: the game draws them on both sides.
        MobyClassReader reader = MobyClassReader.of(stripBlob());

        MobyClassReader.Mesh mesh = reader.readMesh();

        assertEquals(3, mesh.triangleCount(), "A strip of five vertices makes three triangles");
        assertEquals(0, mesh.indices()[0]);
        assertEquals(1, mesh.indices()[1]);
        assertEquals(2, mesh.indices()[2]);
        assertEquals(1, mesh.indices()[3]);
        assertEquals(2, mesh.indices()[4]);
        assertEquals(3, mesh.indices()[5]);
        assertEquals(2, mesh.indices()[6]);
        assertEquals(3, mesh.indices()[7]);
        assertEquals(4, mesh.indices()[8]);
    }

    /**
     * Builds a class with one packet whose strip holds five vertices.
     *
     * <p>The list of the indices of the packet ends with the trailer of the game, three times the
     * first vertex and the end of the list, so that the last three indices are dropped.
     *
     * @return the bytes of the class
     */
    private static byte[] stripBlob() {
        byte[] blob = new byte[0x1000];
        int packetTable = 0x48;
        writeInt(blob, 0x00, packetTable);
        blob[0x04] = 1;
        blob[0x08] = 1;
        writeInt(blob, 0x14, 0x800);
        writeInt(blob, 0x18, 0x880);
        writeFloat(blob, 0x24, 1.0f);

        int list = 0x900;
        int vertexAt = 0xB00;
        int vertices = 5;
        int coordinates = vertices * 4;
        int indices = 16;
        int materials = 64;
        int listSize = (16 + coordinates + indices + materials + 15) / 16;
        writeInt(blob, packetTable, list);
        writeShort(blob, packetTable + 4, listSize);
        writeInt(blob, packetTable + 8, vertexAt);
        blob[packetTable + 0x0C] = (byte) ((VERTEX_TABLE_HEADER + (vertices + 1) * 0x10) / 16);
        blob[packetTable + 0x0F] = (byte) vertices;

        // The unpack of the coordinates of the texture.
        writeCommand(blob, list, 0x65, vertices, 0, coordinates);
        for (int vertex = 0; vertex < vertices; vertex++) {
            writeShort(blob, list + 4 + vertex * 4, vertex * 100);
            writeShort(blob, list + 4 + vertex * 4 + 2, vertex * 200);
        }
        // The unpack of the indices: the five vertices of the strip and the trailer of the game.
        int indexAt = list + 4 + coordinates;
        writeCommand(blob, indexAt, 0x6E, 4, 0, indices);
        blob[indexAt + 4 + 2] = 0;
        for (int index = 0; index < vertices; index++) {
            blob[indexAt + 8 + index] = (byte) (index + 1);
        }
        for (int index = 0; index < 3; index++) {
            blob[indexAt + 8 + vertices + index] = 1;
        }
        blob[indexAt + 8 + vertices + 3] = 0;
        // The unpack of the materials: one block with the texture zero.
        int materialAt = indexAt + 4 + indices;
        writeCommand(blob, materialAt, 0x6C, 4, 0, materials);
        writeInt(blob, materialAt + 4 + 0x20, 0);

        // The table of the vertices of the packet.
        writeInt(blob, vertexAt + 0x0C, vertices);
        writeInt(blob, vertexAt + 0x14, vertices);
        writeInt(blob, vertexAt + 0x18, VERTEX_TABLE_HEADER);
        writeInt(blob, vertexAt + 0x1C, VERTEX_TABLE_HEADER + (vertices + 1) * 0x10);
        for (int vertex = 0; vertex < vertices; vertex++) {
            int at = vertexAt + VERTEX_TABLE_HEADER + vertex * 0x10;
            blob[at + 8] = 0;
            blob[at + 9] = 64;
            writeShort(blob, at + 0x0A, vertex * 100);
            writeShort(blob, at + 0x0C, 0);
            writeShort(blob, at + 0x0E, 0);
        }
        return blob;
    }

    /**
     * Writes a command of a command list of the graphics chip.
     *
     * @param list the list of the commands
     * @param at the place of the command in the list
     * @param command the number of the command
     * @param count the number of the elements of the command
     * @param immediate the number that follows the command
     * @param payload the number of the bytes of the data of the command
     */
    private static void writeCommand(byte[] list, int at, int command, int count, int immediate,
                                     int payload) {
        list[at] = (byte) (immediate & 0xFF);
        list[at + 1] = (byte) ((immediate >> 8) & 0xFF);
        list[at + 2] = (byte) (count & 0xFF);
        list[at + 3] = (byte) (command & 0x7F);
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
