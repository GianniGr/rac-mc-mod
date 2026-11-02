package com.github.giannigr.racmc.asset;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader of a tie class of the game.
 *
 * <p>A tie class is the geometry of an instanced piece of scenery. It holds three levels of
 * detail, each of them a number of packets. A packet is a batch of geometry that the graphics
 * chip of the console receives in one go: it names the materials it uses, the strips of triangles
 * it draws and the vertices it uses.
 *
 * <p>The vertices of a packet are not stored in the order in which they are drawn. Every vertex
 * names the place in the graphics packet of the console where it is written, and the reader walks
 * that packet to find the triangles. The reader follows the rules of the program 13507 of the
 * console, as they were measured by the ReRAC project (ISC); see
 * {@code docs/formats/tie_rac1.md} of that project.
 */
public final class TieClassReader {

    /** The size of the header of a class in bytes. */
    public static final int HEADER_SIZE = 0x80;

    /** The size of the header of a packet in bytes. */
    public static final int PACKET_HEADER_SIZE = 0x10;

    /** The size of the unpack header of a packet in bytes. */
    private static final int UNPACK_HEADER_SIZE = 0x0C;

    /** The size of a stored vertex of the common kind in bytes. */
    private static final int DINKY_VERTEX_SIZE = 0x10;

    /** The size of a stored vertex of the morphing kind in bytes. */
    private static final int FAT_VERTEX_SIZE = 0x18;

    /** The number of the slots of the light of a class. */
    public static final int LIGHT_SLOTS = 64;

    /** The largest number of the GS slots of a packet. */
    private static final int GS_SLOTS = 0x400;

    private final byte[] blob;
    private final int[] packetTables = new int[3];
    private final int[] packetCounts = new int[3];
    private final int adGifOffset;
    private final int textureCount;
    private final float scale;
    private final int classNumber;
    private final float[] lightNormals;

    /**
     * The geometry of a class.
     */
    public static final class Geometry {

        /** The positions of the vertices, three floats each. */
        private final float[] positions;

        /** The texture coordinates of the vertices, two floats each. */
        private final float[] uvs;

        /** The triangles, three vertex indices each. */
        private final int[] indices;

        /** The material of every triangle. */
        private final int[] materials;

        /**
         * Creates the geometry.
         *
         * @param positions the positions of the vertices
         * @param uvs the texture coordinates of the vertices
         * @param indices the triangles
         * @param materials the material of every triangle
         */
        Geometry(float[] positions, float[] uvs, int[] indices, int[] materials) {
            this.positions = positions;
            this.uvs = uvs;
            this.indices = indices;
            this.materials = materials;
        }

        /**
         * The positions of the vertices.
         *
         * @return the positions, three floats each
         */
        public float[] positions() {
            return positions;
        }

        /**
         * The texture coordinates of the vertices.
         *
         * @return the coordinates, two floats each
         */
        public float[] uvs() {
            return uvs;
        }

        /**
         * The triangles of the geometry.
         *
         * @return the triangles, three vertex indices each
         */
        public int[] indices() {
            return indices;
        }

        /**
         * The material of every triangle.
         *
         * @return the materials, one per triangle
         */
        public int[] materials() {
            return materials;
        }

        /**
         * The number of vertices of the geometry.
         *
         * @return the number of vertices
         */
        public int vertexCount() {
            return positions.length / 3;
        }

        /**
         * The number of triangles of the geometry.
         *
         * @return the number of triangles
         */
        public int triangleCount() {
            return indices.length / 3;
        }
    }

    /**
     * Reads a tie class.
     *
     * @param blob the bytes of the class
     * @return the reader of the class
     * @throws AssetFormatException when the class does not follow the expected layout
     */
    public static TieClassReader of(byte[] blob) throws AssetFormatException {
        return new TieClassReader(blob);
    }

    private TieClassReader(byte[] blob) throws AssetFormatException {
        if (blob.length < HEADER_SIZE) {
            throw new AssetFormatException("The tie class is only " + blob.length + " bytes long.");
        }
        this.blob = blob;
        this.packetTables[0] = readInt(blob, 0x00);
        this.packetTables[1] = readInt(blob, 0x04);
        this.packetTables[2] = readInt(blob, 0x08);
        this.packetCounts[0] = blob[0x20] & 0xFF;
        this.packetCounts[1] = blob[0x21] & 0xFF;
        this.packetCounts[2] = blob[0x22] & 0xFF;
        this.textureCount = blob[0x23] & 0xFF;
        this.adGifOffset = readInt(blob, 0x2C);
        this.scale = readFloat(blob, 0x40);
        this.classNumber = readInt(blob, 0x44);
        int normalsOffset = readInt(blob, 0x0C);
        this.lightNormals = new float[LIGHT_SLOTS * 3];
        if (normalsOffset > 0 && normalsOffset + LIGHT_SLOTS * 8 <= blob.length) {
            for (int slot = 0; slot < LIGHT_SLOTS; slot++) {
                lightNormals[slot * 3] = readShort(blob, normalsOffset + slot * 8) / 32767.0f;
                lightNormals[slot * 3 + 1] = readShort(blob, normalsOffset + slot * 8 + 2) / 32767.0f;
                lightNormals[slot * 3 + 2] = readShort(blob, normalsOffset + slot * 8 + 4) / 32767.0f;
            }
        }
    }

    /**
     * The number of the class.
     *
     * @return the number
     */
    public int classNumber() {
        return classNumber;
    }

    /**
     * The scale that turns the packed positions of the class into the units of the game.
     *
     * @return the scale
     */
    public float scale() {
        return scale;
    }

    /**
     * The number of the materials of the class.
     *
     * @return the number of materials
     */
    public int textureCount() {
        return textureCount;
    }

    /**
     * The normals of the light slots of the class.
     *
     * @return the normals, three floats per slot
     */
    public float[] lightNormals() {
        return lightNormals;
    }

    /**
     * Reads the geometry of the level of detail with the highest detail.
     *
     * @return the geometry
     * @throws AssetFormatException when the class does not follow the expected layout
     */
    public Geometry readGeometry() throws AssetFormatException {
        if (packetCounts[0] <= 0 || packetTables[0] <= 0) {
            throw new AssetFormatException("The tie class has no geometry of the highest level of "
                    + "detail.");
        }
        int table = packetTables[0];
        if (table + (long) packetCounts[0] * PACKET_HEADER_SIZE > blob.length) {
            throw new AssetFormatException("The table of the packets of the tie class points "
                    + "outside of the class.");
        }
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        List<Integer> materialList = new ArrayList<>();
        for (int packet = 0; packet < packetCounts[0]; packet++) {
            int header = table + packet * PACKET_HEADER_SIZE;
            Packet result = readPacket(table, header);
            int base = positions.size();
            for (int index = 0; index < result.positions.length; index += 3) {
                positions.add(new float[] { result.positions[index], result.positions[index + 1],
                        result.positions[index + 2] });
            }
            for (int index = 0; index < result.uvs.length; index += 2) {
                uvs.add(new float[] { result.uvs[index], result.uvs[index + 1] });
            }
            for (int index = 0; index < result.triangles.length; index += 4) {
                triangles.add(new int[] { result.triangles[index] + base,
                        result.triangles[index + 1] + base, result.triangles[index + 2] + base });
                materialList.add(result.materials[index / 4]);
            }
        }
        float[] allPositions = new float[positions.size() * 3];
        float[] allUvs = new float[uvs.size() * 2];
        for (int index = 0; index < positions.size(); index++) {
            System.arraycopy(positions.get(index), 0, allPositions, index * 3, 3);
        }
        for (int index = 0; index < uvs.size(); index++) {
            System.arraycopy(uvs.get(index), 0, allUvs, index * 2, 2);
        }
        int[] allIndices = new int[triangles.size() * 3];
        for (int index = 0; index < triangles.size(); index++) {
            System.arraycopy(triangles.get(index), 0, allIndices, index * 3, 3);
        }
        int[] materials = new int[materialList.size()];
        for (int index = 0; index < materials.length; index++) {
            materials[index] = materialList.get(index);
        }
        return new Geometry(allPositions, allUvs, allIndices, materials);
    }

    /**
     * The geometry of one packet.
     */
    private static final class Packet {

        private final float[] positions;
        private final float[] uvs;
        private final int[] triangles;
        private final int[] materials;

        Packet(float[] positions, float[] uvs, int[] triangles, int[] materials) {
            this.positions = positions;
            this.uvs = uvs;
            this.triangles = triangles;
            this.materials = materials;
        }
    }

    private Packet readPacket(int table, int header) throws AssetFormatException {
        int offset = readInt(blob, header);
        if (offset < 0) {
            throw new AssetFormatException("A packet of the tie class names an offset that is "
                    + "before the class.");
        }
        long start = (long) table + offset;
        int shaderCount = blob[header + 4] & 0xFF;
        int controlSize = blob[header + 7] & 0xFF;
        int vertexOffset = blob[header + 8] & 0xFF;
        int vertexSize = blob[header + 9] & 0xFF;
        int colourOffset = blob[header + 0x0A] & 0xFF;
        int colourCount = blob[header + 0x0B] & 0xFF;
        int stripCount = blob[header + 0x0E] & 0xFF;

        require(start + 0x20 + UNPACK_HEADER_SIZE + stripCount * 4 <= blob.length,
                "The data of a packet of the tie class lies outside of the class.");
        int data = (int) start;

        int[] adGifDest = new int[4];
        int[] adGifSrc = new int[4];
        for (int index = 0; index < 4; index++) {
            adGifDest[index] = readInt(blob, data + index * 4);
            adGifSrc[index] = readInt(blob, data + 0x10 + index * 4);
        }

        int unpack = data + 0x20;
        int dinkySingleOnly = blob[unpack] & 0xFF;
        int noFat = blob[unpack + 1] & 0xFF;
        int dinkySingleEnd = blob[unpack + 4] & 0xFF;
        int dinkyDoubleEnd = blob[unpack + 5] & 0xFF;
        int fatSingleEnd = blob[unpack + 6] & 0xFF;
        int fatDoubleEnd = blob[unpack + 7] & 0xFF;
        int dinkyCount = blob[unpack + 0x0A] & 0xFF;
        int fatCount = blob[unpack + 0x0B] & 0xFF;
        int unpackStripCount = blob[unpack + 3] & 0xFF;
        if (unpackStripCount != stripCount) {
            throw new AssetFormatException("A packet of the tie class names " + stripCount
                    + " strips, but its unpack header names " + unpackStripCount + ".");
        }
        if (controlSize * 16 < UNPACK_HEADER_SIZE + stripCount * 4) {
            throw new AssetFormatException("The control region of a packet of the tie class is "
                    + "smaller than its header and its strips.");
        }
        if (dinkyCount * DINKY_VERTEX_SIZE + fatCount * FAT_VERTEX_SIZE > vertexSize * 16) {
            throw new AssetFormatException("The vertices of a packet of the tie class do not fit "
                    + "into their region.");
        }

        int strips = data + 0x2C;
        int[] stripVertexCount = new int[stripCount];
        int[] stripTagOffset = new int[stripCount];
        int[] stripWinding = new int[stripCount];
        for (int strip = 0; strip < stripCount; strip++) {
            stripVertexCount[strip] = blob[strips + strip * 4] & 0xFF;
            stripTagOffset[strip] = blob[strips + strip * 4 + 2] & 0xFF;
            stripWinding[strip] = blob[strips + strip * 4 + 3] & 0xFF;
        }

        int vertices = data + vertexOffset * 16;
        require(vertices + dinkyCount * DINKY_VERTEX_SIZE + fatCount * FAT_VERTEX_SIZE <= blob.length,
                "The vertices of a packet of the tie class lie outside of the class.");

        int colourStart = data + colourOffset * 16;
        int colourBytes = colourCount * 4;
        require(colourStart + colourBytes <= blob.length,
                "The colours of a packet of the tie class lie outside of the class.");

        // The vertices are resolved the way the program of the console writes them: the vertices
        // of the common kind first, then the vertices of the morphing kind, and the vertices of
        // the double writing phases are written twice.
        int dinkySingle = 0;
        if (dinkyCount > 0) {
            int marker = findSlot(vertices, dinkyCount, DINKY_VERTEX_SIZE, 0, dinkySingleEnd);
            if (marker < 0) {
                throw new AssetFormatException("A packet of the tie class has no vertex that ends "
                        + "the first phase of the common vertices.");
            }
            dinkySingle = marker + 4;
            int end;
            if (dinkySingleOnly != 0) {
                end = dinkySingle;
            } else {
                int doubleMarker = findSlot(vertices, dinkyCount, DINKY_VERTEX_SIZE, dinkySingle, dinkyDoubleEnd);
                if (doubleMarker < 0) {
                    throw new AssetFormatException("A packet of the tie class has no vertex that "
                            + "ends the second phase of the common vertices.");
                }
                end = doubleMarker + 3;
            }
            if (end != dinkyCount) {
                throw new AssetFormatException("The phases of the common vertices of a packet of "
                        + "the tie class do not end at the last vertex.");
            }
        }
        int fatSingle = 0;
        if (noFat == 0) {
            if (fatCount == 0) {
                throw new AssetFormatException("A packet of the tie class has no morphing vertices "
                        + "although its unpack header does not say that it has none.");
            }
            int marker = findSlot(vertices + dinkyCount * DINKY_VERTEX_SIZE, fatCount, FAT_VERTEX_SIZE, 0, fatSingleEnd);
            if (marker < 0) {
                throw new AssetFormatException("A packet of the tie class has no vertex that ends "
                        + "the first phase of the morphing vertices.");
            }
            fatSingle = marker + 1;
            int doubleMarker = findSlot(vertices + dinkyCount * DINKY_VERTEX_SIZE, fatCount, FAT_VERTEX_SIZE, fatSingle, fatDoubleEnd);
            if (doubleMarker != fatCount - 1) {
                throw new AssetFormatException("The phases of the morphing vertices of a packet of "
                        + "the tie class do not end at the last vertex.");
            }
        } else if (fatCount > 0) {
            throw new AssetFormatException("A packet of the tie class has morphing vertices "
                    + "although its unpack header says that it has none.");
        }

        int total = dinkyCount + fatCount;
        float[] positions = new float[total * 3];
        float[] uvs = new float[total * 2];
        int[] slots = new int[total];
        int[] secondSlots = new int[total];
        int fatBase = ((dinkyCount + 3) / 4) * 4;
        for (int index = 0; index < dinkyCount; index++) {
            int at = vertices + index * DINKY_VERTEX_SIZE;
            positions[index * 3] = readShort(blob, at) * scale / 1024.0f;
            positions[index * 3 + 1] = readShort(blob, at + 2) * scale / 1024.0f;
            positions[index * 3 + 2] = readShort(blob, at + 4) * scale / 1024.0f;
            uvs[index * 2] = readUnsignedShort(blob, at + 8) / 4096.0f;
            uvs[index * 2 + 1] = readUnsignedShort(blob, at + 0x0A) / 4096.0f;
            slots[index] = readUnsignedShort(blob, at + 6);
            secondSlots[index] = index >= dinkySingle ? readUnsignedShort(blob, at + 0x0E) : 0;
        }
        for (int index = 0; index < fatCount; index++) {
            int at = vertices + dinkyCount * DINKY_VERTEX_SIZE + index * FAT_VERTEX_SIZE;
            int vertex = dinkyCount + index;
            positions[vertex * 3] = readShort(blob, at + 8) * scale / 1024.0f;
            positions[vertex * 3 + 1] = readShort(blob, at + 0x0A) * scale / 1024.0f;
            positions[vertex * 3 + 2] = readShort(blob, at + 0x0C) * scale / 1024.0f;
            uvs[vertex * 2] = readUnsignedShort(blob, at + 0x10) / 4096.0f;
            uvs[vertex * 2 + 1] = readUnsignedShort(blob, at + 0x12) / 4096.0f;
            slots[vertex] = readUnsignedShort(blob, at + 6);
            secondSlots[vertex] = index >= fatSingle ? readUnsignedShort(blob, at + 0x16) : 0;
        }
        if (fatBase + fatCount * 4 > colourBytes) {
            throw new AssetFormatException("The colours of a packet of the tie class are too few "
                    + "for its vertices.");
        }

        // The places of the graphics packet of the console: the last writer of a place wins.
        int[] slotMap = new int[GS_SLOTS];
        java.util.Arrays.fill(slotMap, -1);
        for (int index = 0; index < total; index++) {
            if (slots[index] >= GS_SLOTS || secondSlots[index] >= GS_SLOTS) {
                throw new AssetFormatException("A vertex of a packet of the tie class names a "
                        + "place outside of the graphics packet.");
            }
            slotMap[slots[index]] = index;
            if (secondSlots[index] != 0) {
                slotMap[secondSlots[index]] = index;
            }
        }

        // The places of the material blocks: the first one is always at the start.
        int[] materialPositions = new int[4];
        int materialCount = 1;
        materialPositions[0] = 0;
        while (materialCount < 4 && adGifDest[materialCount - 1] > 0) {
            materialPositions[materialCount] = adGifDest[materialCount - 1];
            materialCount++;
        }
        if (materialCount != shaderCount) {
            throw new AssetFormatException("A packet of the tie class names " + shaderCount
                    + " materials, but " + materialCount + " of them are placed.");
        }
        for (int index = 0; index < materialCount; index++) {
            if (adGifSrc[index] < 0 || adGifSrc[index] % 0x50 != 0) {
                throw new AssetFormatException("A material of a packet of the tie class does not "
                        + "name a material of the class.");
            }
        }

        List<int[]> triangles = new ArrayList<>();
        List<Integer> triangleMaterials = new ArrayList<>();
        int cursor = 0;
        int nextMaterial = 0;
        int strip = 0;
        while (strip < stripCount) {
            if (nextMaterial < materialCount && materialPositions[nextMaterial] == cursor) {
                nextMaterial++;
                cursor += 6;
                continue;
            }
            if (stripTagOffset[strip] != cursor) {
                throw new AssetFormatException("A packet of the tie class has no strip at the "
                        + "place " + cursor + " of the graphics packet.");
            }
            int material = adGifSrc[nextMaterial - 1] / 0x50;
            int[] drawn = new int[stripVertexCount[strip]];
            for (int index = 0; index < stripVertexCount[strip]; index++) {
                int place = cursor + 1 + 3 * index;
                if (place >= GS_SLOTS || slotMap[place] < 0) {
                    throw new AssetFormatException("A strip of a packet of the tie class reads a "
                            + "place of the graphics packet that no vertex writes.");
                }
                drawn[index] = slotMap[place];
            }
            cursor += 1 + 3 * stripVertexCount[strip];
            int parity = stripWinding[strip] != 0 ? 1 : 0;
            for (int index = 2; index < drawn.length; index++) {
                if (index % 2 == parity) {
                    triangles.add(new int[] { drawn[index - 2], drawn[index - 1], drawn[index],
                            material });
                } else {
                    triangles.add(new int[] { drawn[index], drawn[index - 1], drawn[index - 2],
                            material });
                }
            }
            strip++;
        }
        if (nextMaterial != materialCount) {
            throw new AssetFormatException("A packet of the tie class has a material that comes "
                    + "after its last strip.");
        }

        int[] triangleArray = new int[triangles.size() * 4];
        int[] triangleMaterialsArray = new int[triangles.size()];
        for (int index = 0; index < triangles.size(); index++) {
            System.arraycopy(triangles.get(index), 0, triangleArray, index * 4, 3);
            triangleMaterialsArray[index] = triangles.get(index)[3];
        }
        return new Packet(positions, uvs, triangleArray, triangleMaterialsArray);
    }

    private int findSlot(int base, int count, int stride, int from, int slot)
            throws AssetFormatException {
        for (int index = from; index < count; index++) {
            int at = base + index * stride;
            if ((blob[at + 6] & 0xFF) == (slot & 0xFF) && (blob[at + 7] & 0xFF) == ((slot >> 8) & 0xFF)) {
                return index;
            }
        }
        return -1;
    }

    private void require(boolean condition, String message) throws AssetFormatException {
        if (!condition) {
            throw new AssetFormatException(message);
        }
    }

    private static int readShort(byte[] data, int offset) {
        return (short) ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static float readFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(readInt(data, offset));
    }
}
