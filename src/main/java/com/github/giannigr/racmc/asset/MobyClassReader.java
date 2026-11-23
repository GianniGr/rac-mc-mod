package com.github.giannigr.racmc.asset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The reader of a moby class of the game.
 *
 * <p>A moby class is the geometry of a character of the game and the skeleton it is animated with.
 * Its mesh is a number of packets, each of them small enough to fit into the memory of the vector
 * unit of the console. A packet holds the texture coordinates of its vertices, the indices of its
 * triangles and the material blocks it draws with.
 *
 * <p>The vertices of a packet do not name their joints directly. They name the places of the
 * memory of the vector unit in which the matrices of the joints are stored, and the reader has to
 * replay the machine of the console to find out which joints a vertex follows. The places are
 * shared by the packets of one level of detail.
 *
 * <p>The layout was measured by the ReRAC project (ISC); see
 * {@code docs/formats/moby_rac1.md} of that project.
 */
public final class MobyClassReader {

    /** Raw PS2 material state, including signed untextured/environment selectors. */
    public record PacketMaterial(int textureSelector, long tex1, long clamp) {}

    /** Source packet identity; metal packets have no normal multiplier stream. */
    public record PacketMetadata(int packetIndex, boolean metal, int vertexCount,
                                 byte[] normalMultipliers, List<PacketMaterial> materials) {
        public PacketMetadata {
            normalMultipliers = normalMultipliers.clone();
            materials = List.copyOf(materials);
        }
        @Override public byte[] normalMultipliers() { return normalMultipliers.clone(); }
    }

    /** Raw source words only, not inferred lighting, Minecraft colors or shader behavior. */
    public record ClassMaterialMetadata(int glowRgba, int mode44, int mode47,
                                        List<PacketMetadata> packets) {
        public ClassMaterialMetadata { packets = List.copyOf(packets); }
    }

    /** Packed metal vertex; count0/1 ignores raw weights, count2/3 uses direct joints and /256. */
    public record MetalVertex(short x, short y, short z, int normalAzimuth, int normalElevation,
                              int count, byte[] jointIndices, byte[] weightBytes) {
        public MetalVertex {
            if (normalAzimuth < 0 || normalAzimuth > 255 || normalElevation < 0 || normalElevation > 255
                    || count < 0 || count > 3 || jointIndices.length != 3 || weightBytes.length != 3)
                throw new IllegalArgumentException("Invalid raw metal vertex");
            jointIndices = jointIndices.clone(); weightBytes = weightBytes.clone();
            int sum = 0;
            for (int i = 0; i < count; i++) sum += weightBytes[i] & 255;
            if (count >= 2 && sum != 256) throw new IllegalArgumentException("Invalid raw metal weights");
        }
        @Override public byte[] jointIndices() { return jointIndices.clone(); }
        @Override public byte[] weightBytes() { return weightBytes.clone(); }
    }

    /** Zero-based packet-local corner and original AD-GIF identity, including cross-packet carry. */
    public record MetalCorner(int vertex, boolean noKick, int materialPacket, int materialBlock) {
        public MetalCorner {
            if (vertex < 0 || materialPacket < 0 || materialPacket > 764 || materialBlock < 0 || materialBlock > 63)
                throw new IllegalArgumentException("Invalid raw metal corner");
        }
    }

    /** Raw draw-corner stream without its three pipeline-tail corners; no triangle repair or UVs. */
    public record MetalPacket(int packetIndex, List<MetalVertex> vertices, List<MetalCorner> corners) {
        public MetalPacket {
            if (packetIndex < 0 || packetIndex > 764 || vertices.size() > 255 || corners.size() > 1017)
                throw new IllegalArgumentException("Invalid raw metal packet");
            vertices = List.copyOf(vertices); corners = List.copyOf(corners);
        }
    }

    /** The size of the header of a class in bytes. */
    public static final int HEADER_SIZE = 0x48;

    /** The size of an entry of the packet table in bytes. */
    private static final int PACKET_ENTRY_SIZE = 0x10;

    /** The size of the header of the table of the vertices in bytes. */
    private static final int VERTEX_TABLE_HEADER_SIZE = 0x20;

    /** The size of a record of the vertices in bytes. */
    private static final int VERTEX_SIZE = 0x10;

    /** The number of the places of the memory of the vector unit. */
    private static final int VU0_SLOTS = 256;

    /** The number of the entries of the cache of the vertices. */
    private static final int VERTEX_CACHE_SIZE = 512;

    /**
     * The skin of a vertex: the joints it follows and their weights.
     */
    private static final class Skin {

        private final int[] joints = new int[3];
        private final float[] weights = new float[3];
        private int count;

        private Skin() {
        }

        private static Skin single(int joint) {
            Skin skin = new Skin();
            skin.joints[0] = joint;
            skin.weights[0] = 1.0f;
            skin.count = 1;
            return skin;
        }
    }

    /**
     * A vertex of the mesh of a class.
     */
    private static final class Vertex {

        private final float[] position = new float[3];
        private final float[] normal = new float[3];
        private Skin skin;

        private Vertex() {
        }

        private Vertex copy() {
            Vertex copy = new Vertex();
            System.arraycopy(position, 0, copy.position, 0, 3);
            System.arraycopy(normal, 0, copy.normal, 0, 3);
            copy.skin = skin;
            return copy;
        }
    }

    /**
     * The mesh of a class.
     */
    public static final class Mesh {

        private final float[] positions;
        private final float[] normals;
        private final float[] uvs;
        private final int[] indices;
        private final int[] materials;
        private final int[] joints;
        private final float[] weights;

        Mesh(float[] positions, float[] normals, float[] uvs, int[] indices, int[] materials,
             int[] joints, float[] weights) {
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.indices = indices;
            this.materials = materials;
            this.joints = joints;
            this.weights = weights;
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
         * The normals of the vertices.
         *
         * @return the normals, three floats each
         */
        public float[] normals() {
            return normals;
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
         * The triangles of the mesh.
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
         * The joints of every vertex.
         *
         * @return the joints, three indices each
         */
        public int[] joints() {
            return joints;
        }

        /**
         * The weights of the joints of every vertex.
         *
         * @return the weights, three floats each
         */
        public float[] weights() {
            return weights;
        }

        /**
         * The number of vertices of the mesh.
         *
         * @return the number of vertices
         */
        public int vertexCount() {
            return positions.length / 3;
        }

        /**
         * The number of triangles of the mesh.
         *
         * @return the number of triangles
         */
        public int triangleCount() {
            return indices.length / 3;
        }
    }

    /**
     * The skeleton of a class.
     */
    public static final class Skeleton {

        private final float[] inverseBind;
        private final int[] parents;
        private final float[] restTranslations;

        Skeleton(float[] inverseBind, int[] parents, float[] restTranslations) {
            this.inverseBind = inverseBind;
            this.parents = parents;
            this.restTranslations = restTranslations;
        }

        /**
         * The inverse bind matrices of the joints.
         *
         * @return the matrices, sixteen floats each
         */
        public float[] inverseBind() {
            return inverseBind;
        }

        /**
         * The parent of every joint.
         *
         * @return the parents, {@code -1} for the root
         */
        public int[] parents() {
            return parents;
        }

        /**
         * The rest translation of every joint.
         *
         * @return the translations, three floats each
         */
        public float[] restTranslations() {
            return restTranslations;
        }

        /**
         * The number of joints of the skeleton.
         *
         * @return the number of joints
         */
        public int jointCount() {
            return parents.length;
        }
    }

    private final byte[] blob;
    private final int packetTable;
    private final int highLodCount;
    private final int lowLodCount;
    private final int metalCount;
    private final int metalBegin;
    private final int jointCount;
    private final int sequenceCount;
    private final int skeletonOffset;
    private final int commonTransOffset;
    private final float scale;

    private MobyClassReader(byte[] blob) {
        this.blob = blob;
        this.packetTable = readInt(blob, 0x00);
        this.highLodCount = blob[0x04] & 0xFF;
        this.lowLodCount = blob[0x05] & 0xFF;
        this.metalCount = blob[0x06] & 0xFF;
        this.metalBegin = blob[0x07] & 0xFF;
        this.jointCount = blob[0x08] & 0xFF;
        this.sequenceCount = blob[0x0C] & 0xFF;
        this.skeletonOffset = readInt(blob, 0x14);
        this.commonTransOffset = readInt(blob, 0x18);
        this.scale = readFloat(blob, 0x24);
    }

    /**
     * Reads a moby class.
     *
     * @param blob the bytes of the class
     * @return the reader of the class
     * @throws AssetFormatException when the class does not follow the expected layout
     */
    public static MobyClassReader of(byte[] blob) throws AssetFormatException {
        if (blob.length < HEADER_SIZE) {
            throw new AssetFormatException("The moby class is only " + blob.length
                    + " bytes long.");
        }
        return new MobyClassReader(blob);
    }

    /**
     * The number of the joints of the class.
     *
     * @return the number of joints
     */
    public int jointCount() {
        return jointCount;
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
     * The number of the animation sequences of the class.
     *
     * @return the number of sequences
     */
    public int sequenceCount() {
        return sequenceCount;
    }

    /**
     * The offset of an animation sequence of the class.
     *
     * @param slot the number of the slot of the sequence
     * @return the offset in the class, or {@code 0} when the slot is empty
     */
    public int sequenceOffset(int slot) {
        if (slot < 0 || slot >= sequenceCount) {
            return 0;
        }
        return readInt(blob, HEADER_SIZE + slot * 4);
    }

    /**
     * The joint lists of a class.
     *
     * <p>The lists name the joints that belong together. The first list of every entry is a chain
     * that leads from the root of the skeleton to a joint: the game uses it to place an item in
     * the hand of the hero. The second list names the joint that a modifier of the game acts on.
     *
     * @param chain the joints of the chain, from the root to the joint
     * @param target the joints of the second list
     */
    public record JointList(byte[] chain, byte[] target) {
    }

    /**
     * Reads the joint lists of the class.
     *
     * @return the lists, in the order of the class
     * @throws AssetFormatException when the lists do not follow the expected layout
     */
    public List<JointList> readJointLists() throws AssetFormatException {
        List<JointList> lists = new ArrayList<>();
        int table = readInt(blob, 0x1C);
        if (table <= 0 || table > blob.length - 4) {
            return lists;
        }
        int count = readInt(blob, table);
        if (count <= 0) {
            return lists;
        }
        if (count > 1024 || table > blob.length - count * 4) {
            throw new AssetFormatException("The joint lists of the moby class lie outside of the "
                    + "class.");
        }
        for (int index = 0; index < count; index++) {
            int at = readInt(blob, table + 4 + index * 4);
            if (at <= 0 || at > blob.length - 4) {
                throw new AssetFormatException("A joint list of the moby class lies outside of the "
                        + "class.");
            }
            int chainCount = readUnsignedShort(blob, at);
            int targetCount = readUnsignedShort(blob, at + 2);
            if (chainCount < 0 || targetCount < 0 || chainCount > 256 || targetCount > 256
                    || at + 4 + chainCount + targetCount + 1 > blob.length) {
                throw new AssetFormatException("A joint list of the moby class has an implausible "
                        + "size.");
            }
            byte[] chain = new byte[chainCount];
            byte[] target = new byte[targetCount];
            System.arraycopy(blob, at + 4, chain, 0, chainCount);
            System.arraycopy(blob, at + 4 + chainCount, target, 0, targetCount);
            lists.add(new JointList(chain, target));
        }
        return lists;
    }

    /**
     * Reads an animation sequence of the class.
     *
     * @param offset the offset of the sequence in the class
     * @return the keys of the sequence
     * @throws AssetFormatException when the sequence does not follow the expected layout
     */
    public MobySequenceReader.Keys readSequence(int offset) throws AssetFormatException {
        return MobySequenceReader.read(blob, offset, jointCount);
    }

    /**
     * Reads the mesh of the class.
     *
     * @return the mesh of the level of detail with the highest detail
     * @throws AssetFormatException when the class does not follow the expected layout
     */
    public Mesh readMesh() throws AssetFormatException {
        if (packetTable <= 0) {
            throw new AssetFormatException("The moby class has no mesh.");
        }
        int total = highLodCount + lowLodCount + metalCount;
        if (metalCount > 0 && metalBegin + metalCount > total) {
            throw new AssetFormatException("The metal packets of the moby class lie past the end "
                    + "of its packet table.");
        }
        if (packetTable + (long) total * PACKET_ENTRY_SIZE > blob.length) {
            throw new AssetFormatException("The packet table of the moby class lies outside of "
                    + "the class.");
        }
        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Integer> triangles = new ArrayList<>();
        List<Integer> materials = new ArrayList<>();
        List<Integer> joints = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        readList(0, highLodCount, false, positions, normals, uvs, triangles, materials, joints,
                weights);
        int[] indices = toInts(triangles);
        int vertices = positions.size() / 3;
        // Every triangle has to name three vertices of the mesh. The check catches a packet whose
        // indices were not moved onto the vertices of the packets that came before it.
        for (int index : indices) {
            if (index < 0 || index >= vertices) {
                throw new AssetFormatException("A triangle of the moby class names a vertex that "
                        + "does not exist.");
            }
        }
        return new Mesh(toArray(positions), toArray(normals), toArray(uvs), indices,
                toInts(materials), toInts(joints), toArray(weights));
    }

    /**
     * Reads high-LOD normal and separately indexed metal packet metadata without decoding geometry.
     * Missing per-packet material unpacks are represented by an empty list (source state carry);
     * multiplier bytes are never fabricated. Low-LOD payloads are not decoded.
     *
     * @throws AssetFormatException for malformed or unsupported packet metadata layouts
     */
    public ClassMaterialMetadata readMaterialMetadata() throws AssetFormatException {
        int total = highLodCount + lowLodCount + metalCount;
        if (packetTable <= 0 || total == 0) {
            throw new AssetFormatException("The moby class has no material packet table.");
        }
        metadataRange(packetTable, (long) total * PACKET_ENTRY_SIZE);
        if (metalCount > 0 && (metalBegin < highLodCount || metalBegin + metalCount > total)) {
            throw new AssetFormatException("The metal metadata packet range overlaps or exceeds the packet table.");
        }
        List<PacketMetadata> packets = new ArrayList<>();
        for (int p = 0; p < highLodCount; p++) packets.add(readPacketMetadata(p, false));
        for (int p = 0; p < metalCount; p++) packets.add(readPacketMetadata(metalBegin + p, true));
        return new ClassMaterialMetadata(readInt(blob, 0x40), readUnsignedShort(blob, 0x44),
                readByte(blob, 0x47), packets);
    }

    private PacketMetadata readPacketMetadata(int packet, boolean metal) throws AssetFormatException {
        int entry = packetTable + packet * PACKET_ENTRY_SIZE;
        int list = readInt(blob, entry), listSize = readUnsignedShort(blob, entry + 4);
        int header = readInt(blob, entry + 8), extent = readByte(blob, entry + 12) * 16;
        int count = readByte(blob, entry + 15);
        if (list <= 0 || listSize == 0 || header <= 0 || extent < (metal ? 16 : 32)) {
            throw new AssetFormatException("A material metadata packet has missing command or vertex data.");
        }
        metadataRange(list, (long) listSize * 16);
        metadataRange(header, extent);
        byte[] multipliers;
        if (metal) {
            if (readInt(blob, header) != count || 16L + count * 16L > extent) {
                throw new AssetFormatException("A metal metadata packet has inconsistent vertex counts or extent.");
            }
            multipliers = new byte[0];
        } else {
            int transfers = readInt(blob, header), twoWay = readInt(blob, header + 4);
            int threeWay = readInt(blob, header + 8), main = readInt(blob, header + 12);
            int duplicates = readInt(blob, header + 16), transferCount = readInt(blob, header + 20);
            int vertices = readInt(blob, header + 24), multiplier = readInt(blob, header + 28);
            long inFile = (long) twoWay + threeWay + main;
            long duplicatesAt = (32L + transfers * 2L + 7) & ~7L;
            long trailing = (long) multiplier - vertices - inFile * VERTEX_SIZE;
            if (transfers < 0 || twoWay < 0 || threeWay < 0 || main < 0 || duplicates < 0
                    || transferCount != count || inFile + duplicates != count
                    || vertices < 32 || duplicatesAt + duplicates * 2L > vertices
                    || multiplier < vertices || trailing < VERTEX_SIZE || trailing > 6L * VERTEX_SIZE
                    || trailing % VERTEX_SIZE != 0 || multiplier + count * 4L > extent) {
                throw new AssetFormatException("A normal metadata packet has inconsistent counts or multiplier bounds.");
            }
            multipliers = slice(header + multiplier, count * 4);
        }
        List<VifCommandList.Command> unpacks = VifCommandList.read(slice(list, listSize * 16)).unpacks();
        int materialIndex = metal ? 1 : 2;
        if (unpacks.size() < materialIndex) {
            throw new AssetFormatException("A material metadata packet is missing required unpacks.");
        }
        if (!metal && (unpacks.get(0).components() != 1 || unpacks.get(0).width() != 1
                || unpacks.get(0).count() != count)) {
            throw new AssetFormatException("A normal metadata packet has invalid coordinate counts or shape.");
        }
        var indices = unpacks.get(materialIndex - 1);
        if (indices.components() != 3 || indices.width() != 2 || indices.count() < 1
                || indices.data().length != indices.count() * 4) {
            throw new AssetFormatException("A material metadata packet has invalid index unpack shape.");
        }
        List<PacketMaterial> materials = new ArrayList<>();
        if (unpacks.size() > materialIndex) {
            var source = unpacks.get(materialIndex);
            if (unpacks.size() != materialIndex + 1 || source.components() != 3 || source.width() != 0
                    || source.count() % 4 != 0 || source.data().length != source.count() * 16) {
                throw new AssetFormatException("A material metadata packet has incomplete or unsupported material blocks.");
            }
            byte[] data = source.data();
            for (int at = 0; at < data.length; at += 64) {
                int selector = readInt(data, at + 32);
                if (metal ? selector != -2 && selector != -3 : selector < -1) {
                    throw new AssetFormatException("A material metadata packet has an unsupported texture selector.");
                }
                long tex1 = Integer.toUnsignedLong(readInt(data, at))
                        | (long) readInt(data, at + 4) << 32;
                long clamp = Integer.toUnsignedLong(readInt(data, at + 16))
                        | (long) readInt(data, at + 20) << 32;
                materials.add(new PacketMaterial(selector, tex1, clamp));
            }
        }
        return new PacketMetadata(packet, metal, count, multipliers, materials);
    }

    private void metadataRange(int offset, long length) throws AssetFormatException {
        if (offset < 0 || length < 0 || offset > blob.length || length > blob.length - offset) {
            throw new AssetFormatException("A material metadata range lies outside of the moby class.");
        }
    }

    /**
     * Additive raw metal access. Does not alter readMesh, decode UVs/colors, evaluate pose or serialize.
     * Original material state persists only within this class's metal list; corner history is local.
     */
    public List<MetalPacket> readMetalPackets() throws AssetFormatException {
        if (metalCount == 0) return List.of();
        int total = highLodCount + lowLodCount + metalCount;
        if (packetTable <= 0 || metalBegin < highLodCount + lowLodCount
                || metalBegin + metalCount > total || jointCount < 1 || jointCount > 111)
            throw new AssetFormatException("Invalid separate metal packet table or joint count");
        metadataRange(packetTable, (long) total * PACKET_ENTRY_SIZE);
        List<MetalPacket> packets = new ArrayList<>();
        int boundPacket = -1, boundBlock = -1;
        for (int packet = metalBegin; packet < metalBegin + metalCount; packet++) {
            // Reuse only the approved raw layout validator, never the normal geometry decoder.
            var metadata = readPacketMetadata(packet, true);
            int entry = packetTable + packet * PACKET_ENTRY_SIZE, header = readInt(blob, entry + 8);
            int count = metadata.vertexCount();
            int positions = (count * 4 + 15) & ~15, colors = positions + ((count * 6 + 15) & ~15);
            int output = colors + ((count * 4 + 15) & ~15);
            if (readInt(blob, header + 4) != positions || readInt(blob, header + 8) != colors
                    || readInt(blob, header + 12) != output)
                throw new AssetFormatException("Unsupported metal output packing layout");
            List<MetalVertex> vertices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int at = header + 16 + i * 16, influences = readByte(blob, at + 11);
                if (influences > 3 || readByte(blob, at + 15) != 0)
                    throw new AssetFormatException("Unsupported metal influence count or padding");
                byte[] joints = slice(at + 8, 3), weights = slice(at + 12, 3);
                int sum = 0;
                for (int k = 0; k < Math.max(1,influences); k++) {
                    if ((joints[k] & 255) >= jointCount) throw new AssetFormatException("Metal vertex joint outside its local palette");
                    sum += weights[k] & 255;
                }
                if (influences >= 2 && sum != 256) throw new AssetFormatException("Metal weights do not sum to256");
                vertices.add(new MetalVertex((short)readShort(blob,at),(short)readShort(blob,at+2),
                        (short)readShort(blob,at+4),readByte(blob,at+6),readByte(blob,at+7),influences,joints,weights));
            }
            int list = readInt(blob,entry), listSize = readUnsignedShort(blob,entry+4);
            var unpacks = VifCommandList.read(slice(list,listSize*16)).unpacks();
            byte[] indices = unpacks.get(0).data();
            if (indices[3] != 0) throw new AssetFormatException("Unsupported metal index header padding");
            byte[] materials = unpacks.size() == 2 ? unpacks.get(1).data() : new byte[0];
            int blocks = metadata.materials().size(), secret = 0, nextBlock = 0;
            boolean ended = false;
            List<MetalCorner> corners = new ArrayList<>();
            for (int i = 4; i < indices.length; i++) {
                int value = indices[i] & 255, vertex;
                boolean noKick;
                if (value == 0) {
                    if (secret > blocks) throw new AssetFormatException("Metal stream exhausted secret indices");
                    int actual = secret == 0 ? indices[2] & 255 : materials[(secret-1)*16+12] & 255;
                    secret++;
                    if (actual == 0) { ended = true; break; }
                    if (nextBlock >= blocks) throw new AssetFormatException("Metal stream switched without an AD-GIF block");
                    boundPacket = packet; boundBlock = nextBlock++;
                    vertex = actual & 127; noKick = true;
                } else {
                    vertex = value & 127; noKick = (value & 128) != 0;
                }
                if (vertex < 1 || vertex > count || boundPacket < 0)
                    throw new AssetFormatException("Metal corner has invalid vertex or missing initial binding");
                corners.add(new MetalCorner(vertex-1,noKick,boundPacket,boundBlock));
            }
            if (!ended || nextBlock != blocks || corners.size() < 3)
                throw new AssetFormatException("Metal stream has missing terminator, trailer or unused materials");
            for (int i = corners.size()-3; i < corners.size(); i++)
                if (corners.get(i).vertex() != 0 || corners.get(i).noKick())
                    throw new AssetFormatException("Unsupported metal pipeline trailer");
            packets.add(new MetalPacket(packet,vertices,corners.subList(0,corners.size()-3)));
        }
        return List.copyOf(packets);
    }

    /**
     * Reads the skeleton of the class.
     *
     * <p>The matrices of the skeleton are the inverse bind matrices of the joints. The game reads
     * the rows of the matrices as the columns of the matrix it works with, and the translation of
     * a joint is in the packed units of the model.
     *
     * @return the skeleton
     * @throws AssetFormatException when the class does not follow the expected layout
     */
    public Skeleton readSkeleton() throws AssetFormatException {
        if (jointCount == 0) {
            throw new AssetFormatException("The moby class has no joints.");
        }
        if (skeletonOffset <= 0 || commonTransOffset <= 0) {
            throw new AssetFormatException("The moby class has no skeleton.");
        }
        if (skeletonOffset + (long) jointCount * 0x40 > blob.length
                || commonTransOffset + (long) jointCount * 16 > blob.length) {
            throw new AssetFormatException("The skeleton of the moby class lies outside of the "
                    + "class.");
        }
        float[] inverseBind = new float[jointCount * 16];
        int[] parents = new int[jointCount];
        float[] rest = new float[jointCount * 3];
        for (int joint = 0; joint < jointCount; joint++) {
            int matrix = skeletonOffset + joint * 0x40;
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) {
                    // The game keeps the rows of its matrices, and the row 0 of a matrix is the
                    // image of its first axis. The matrix of the game of blocks holds the
                    // columns, so the rows of the disc are read the other way around.
                    inverseBind[joint * 16 + column * 4 + row] =
                            readFloat(blob, matrix + column * 16 + row * 4);
                }
                inverseBind[joint * 16 + column * 4 + 3] = 0.0f;
            }
            for (int row = 0; row < 3; row++) {
                inverseBind[joint * 16 + 3 * 4 + row] =
                        readFloat(blob, matrix + 3 * 16 + row * 4) / 1024.0f;
            }
            inverseBind[joint * 16 + 15] = 1.0f;

            rest[joint * 3] = readFloat(blob, commonTransOffset + joint * 16) / 1024.0f;
            rest[joint * 3 + 1] = readFloat(blob, commonTransOffset + joint * 16 + 4) / 1024.0f;
            rest[joint * 3 + 2] = readFloat(blob, commonTransOffset + joint * 16 + 8) / 1024.0f;
            int word = readInt(blob, commonTransOffset + joint * 16 + 12);
            if (word == 0) {
                parents[joint] = -1;
            } else if (word >= 0x70000000 && (word - 0x70000000) % 0x40 == 0
                    && (word - 0x70000000) / 0x40 < joint) {
                parents[joint] = (word - 0x70000000) / 0x40;
            } else {
                throw new AssetFormatException("A joint of the moby class names a parent that "
                        + "does not exist.");
            }
        }
        return new Skeleton(inverseBind, parents, rest);
    }

    private void readList(int first, int count, boolean metal, List<Float> positions,
                          List<Float> normals, List<Float> uvs, List<Integer> triangles,
                          List<Integer> materials, List<Integer> joints, List<Float> weights)
            throws AssetFormatException {
        Skin[] slots = new Skin[VU0_SLOTS];
        Vertex[] cache = new Vertex[VERTEX_CACHE_SIZE];
        int texture = -1;

        for (int packet = 0; packet < count; packet++) {
            int entry = packetTable + (first + packet) * PACKET_ENTRY_SIZE;
            int listOffset = readInt(blob, entry);
            int listSize = readUnsignedShort(blob, entry + 4);
            int vertexOffset = readInt(blob, entry + 8);
            int vertexDataSize = blob[entry + 0x0C] & 0xFF;
            int transferCount = blob[entry + 0x0F] & 0xFF;
            if (listOffset <= 0 || listSize <= 0
                    || listOffset + (long) listSize * 16 > blob.length) {
                throw new AssetFormatException("The command list of a packet of the moby class "
                        + "lies outside of the class.");
            }
            if (vertexOffset <= 0 || vertexOffset + (long) vertexDataSize * 16 > blob.length) {
                throw new AssetFormatException("The vertices of a packet of the moby class lie "
                        + "outside of the class.");
            }

            VifCommandList commands = VifCommandList.read(slice(listOffset, listSize * 16));
            List<VifCommandList.Command> unpacks = commands.unpacks();
            byte[] textureCoordinates = null;
            List<Integer> textureIndices = new ArrayList<>();
            List<Integer> secretIndices = new ArrayList<>();

            int unpackIndex = 0;
            if (!metal) {
                if (unpacks.size() < 2) {
                    throw new AssetFormatException("A packet of the moby class has fewer than two "
                            + "unpacks.");
                }
                VifCommandList.Command coordinates = unpacks.get(unpackIndex++);
                if (coordinates.components() != 1 || coordinates.width() != 1) {
                    throw new AssetFormatException("The texture coordinates of a packet of the "
                            + "moby class are not stored as they should be.");
                }
                textureCoordinates = coordinates.data();
            } else if (unpacks.isEmpty()) {
                throw new AssetFormatException("A packet of the moby class has no unpack.");
            }

            VifCommandList.Command indexUnpack = unpacks.get(unpackIndex++);
            if (indexUnpack.components() != 3 || indexUnpack.width() != 2) {
                throw new AssetFormatException("The indices of a packet of the moby class are not "
                        + "stored as they should be.");
            }
            int indexBytesLength = indexUnpack.count() * 4;
            if (indexBytesLength > indexUnpack.data().length) {
                throw new AssetFormatException("The indices of a packet of the moby class are "
                        + "shorter than they should be.");
            }
            byte[] indexBytes = new byte[indexBytesLength - 4];
            System.arraycopy(indexUnpack.data(), 4, indexBytes, 0, indexBytes.length);
            if (indexUnpack.data().length >= 3) {
                secretIndices.add(indexUnpack.data()[2] & 0xFF);
            }

            if (unpackIndex < unpacks.size()) {
                VifCommandList.Command materialsUnpack = unpacks.get(unpackIndex);
                if (materialsUnpack.components() != 3 || materialsUnpack.width() != 0) {
                    throw new AssetFormatException("The materials of a packet of the moby class "
                            + "are not stored as they should be.");
                }
                int blocks = materialsUnpack.count() / 4;
                for (int block = 0; block < blocks; block++) {
                    int at = block * 0x40;
                    if (at + 0x40 > materialsUnpack.data().length) {
                        throw new AssetFormatException("The materials of a packet of the moby "
                                + "class are shorter than they should be.");
                    }
                    textureIndices.add(readInt(materialsUnpack.data(), at + 0x20));
                    secretIndices.add(materialsUnpack.data()[block * 0x10 + 0x0C] & 0xFF);
                }
            }

            int header = vertexOffset;
            int matrixTransfers = readInt(blob, header);
            int twoWay = readInt(blob, header + 0x04);
            int threeWay = readInt(blob, header + 0x08);
            int main = readInt(blob, header + 0x0C);
            int duplicates = readInt(blob, header + 0x10);
            int transferVertices = readInt(blob, header + 0x14);
            int vertexTableOffset = readInt(blob, header + 0x18);
            int multiplierOffset = readInt(blob, header + 0x1C);
            int inFile = twoWay + threeWay + main;
            if (transferVertices != inFile + duplicates || transferVertices != transferCount) {
                throw new AssetFormatException("The vertices of a packet of the moby class do not "
                        + "add up.");
            }
            if (multiplierOffset < vertexTableOffset) {
                throw new AssetFormatException("The table of the vertices of a packet of the moby "
                        + "class lies outside of the class.");
            }
            int epilogue = (multiplierOffset - vertexTableOffset) / VERTEX_SIZE - inFile;
            if (epilogue < 1 || epilogue > 6) {
                throw new AssetFormatException("A packet of the moby class has " + epilogue
                        + " trailing vertices.");
            }

            int transfersAt = header + VERTEX_TABLE_HEADER_SIZE;
            if (transfersAt + (long) matrixTransfers * 2 > blob.length) {
                throw new AssetFormatException("The matrices of a packet of the moby class lie "
                        + "outside of the class.");
            }
            for (int transfer = 0; transfer < matrixTransfers; transfer++) {
                int joint = blob[transfersAt + transfer * 2] & 0xFF;
                int address = blob[transfersAt + transfer * 2 + 1] & 0xFF;
                store(slots, address, Skin.single(joint));
            }

            int duplicatesAt = transfersAt + matrixTransfers * 2;
            if (duplicatesAt % 4 != 0) {
                duplicatesAt += 2;
            }
            if (duplicatesAt % 8 != 0) {
                duplicatesAt += 4;
            }
            if (duplicatesAt + (long) duplicates * 2 > blob.length) {
                throw new AssetFormatException("The repeated vertices of a packet of the moby "
                        + "class lie outside of the class.");
            }

            int recordsAt = header + vertexTableOffset;
            if (recordsAt + (long) (inFile + epilogue) * VERTEX_SIZE > blob.length) {
                throw new AssetFormatException("The vertices of a packet of the moby class lie "
                        + "outside of the class.");
            }
            byte[] records = slice(recordsAt, (inFile + epilogue) * VERTEX_SIZE);
            int[] ids = cacheIds(records, inFile);

            List<Vertex> vertices = new ArrayList<>();
            for (int index = 0; index < inFile; index++) {
                int at = index * VERTEX_SIZE;
                Vertex vertex = new Vertex();
                if (index < twoWay) {
                    vertex.skin = twoWayVertex(slots, records, at);
                } else if (index < twoWay + threeWay) {
                    vertex.skin = threeWayVertex(slots, records, at);
                } else {
                    vertex.skin = singleVertex(slots, records, at);
                }
                vertex.position[0] = readShort(records, at + 0x0A);
                vertex.position[1] = readShort(records, at + 0x0C);
                vertex.position[2] = readShort(records, at + 0x0E);
                float[] normal = normal(readByte(records, at + 8), readByte(records, at + 9));
                vertex.normal[0] = normal[0];
                vertex.normal[1] = normal[1];
                vertex.normal[2] = normal[2];
                vertices.add(vertex);
                cache[ids[index] & 0x1FF] = vertex;
            }

            for (int index = 0; index < duplicates; index++) {
                int entryId = readUnsignedShort(blob, duplicatesAt + index * 2);
                int slot = (entryId >> 7) & 0x1FF;
                if (cache[slot] == null) {
                    throw new AssetFormatException("A repeated vertex of a packet of the moby class "
                            + "names a vertex that was never written.");
                }
                vertices.add(cache[slot].copy());
            }

            int vertexTotal = vertices.size();
            if (textureCoordinates == null || textureCoordinates.length < vertexTotal * 4) {
                throw new AssetFormatException("The texture coordinates of a packet of the moby "
                        + "class are shorter than its vertices.");
            }

            List<int[]> pushed = new ArrayList<>();
            boolean ended = false;
            int secretIndex = 0;
            int materialIndex = 0;
            for (byte value : indexBytes) {
                int index;
                boolean noKick;
                if (value == 0) {
                    if (secretIndex >= secretIndices.size()) {
                        throw new AssetFormatException("A packet of the moby class runs out of the "
                                + "hidden indices of its materials.");
                    }
                    int secret = secretIndices.get(secretIndex++);
                    if (secret == 0) {
                        ended = true;
                        break;
                    }
                    if (materialIndex >= textureIndices.size()) {
                        throw new AssetFormatException("A packet of the moby class switches its "
                                + "material without a material block.");
                    }
                    texture = textureIndices.get(materialIndex++);
                    index = secret & 0x7F;
                    noKick = true;
                } else {
                    index = value & 0x7F;
                    noKick = (value & 0x80) != 0;
                }
                if (index <= 0 || index > vertexTotal) {
                    throw new AssetFormatException("An index of a packet of the moby class names a "
                            + "vertex that does not exist.");
                }
                pushed.add(new int[] { index - 1, noKick ? 1 : 0, texture });
            }
            if (!ended) {
                throw new AssetFormatException("The indices of a packet of the moby class are not "
                        + "terminated.");
            }
            if (pushed.size() < 3) {
                throw new AssetFormatException("A packet of the moby class has fewer than three "
                        + "indices.");
            }
            int usable = pushed.size() - 3;
            // The vertices of the packets of a class are collected in one list, but the indices
            // of a packet name the vertices of that packet alone. The indices are therefore moved
            // by the number of the vertices of the packets that came before it.
            int base = positions.size() / 3;
            for (int index = 2; index < usable; index++) {
                if (pushed.get(index)[1] != 0) {
                    continue;
                }
                triangles.add(base + pushed.get(index - 2)[0]);
                triangles.add(base + pushed.get(index - 1)[0]);
                triangles.add(base + pushed.get(index)[0]);
                materials.add(pushed.get(index)[2]);
            }

            for (int index = 0; index < vertexTotal; index++) {
                Vertex vertex = vertices.get(index);
                positions.add(vertex.position[0] * scale / 1024.0f);
                positions.add(vertex.position[1] * scale / 1024.0f);
                positions.add(vertex.position[2] * scale / 1024.0f);
                normals.add(vertex.normal[0]);
                normals.add(vertex.normal[1]);
                normals.add(vertex.normal[2]);
                uvs.add(readShort(textureCoordinates, index * 4) / 4096.0f);
                uvs.add(readShort(textureCoordinates, index * 4 + 2) / 4096.0f);
                Skin skin = vertex.skin;
                joints.add(skin.count > 0 ? skin.joints[0] : 0);
                joints.add(skin.count > 1 ? skin.joints[1] : 0);
                joints.add(skin.count > 2 ? skin.joints[2] : 0);
                weights.add(skin.count > 0 ? skin.weights[0] : 0.0f);
                weights.add(skin.count > 1 ? skin.weights[1] : 0.0f);
                weights.add(skin.count > 2 ? skin.weights[2] : 0.0f);
            }
        }
    }

    private static Skin twoWayVertex(Skin[] slots, byte[] records, int at)
            throws AssetFormatException {
        store(slots, readByte(records, at + 6), Skin.single(readByte(records, at + 1) >> 1));
        Skin first = loadJoint(slots, readByte(records, at + 2));
        Skin second = loadJoint(slots, readByte(records, at + 3));
        int weightFirst = readByte(records, at + 4);
        int weightSecond = readByte(records, at + 5);
        if (weightFirst + weightSecond != 256) {
            throw new AssetFormatException("A vertex of the moby class has weights that do not "
                    + "add up.");
        }
        Skin skin = new Skin();
        skin.count = 2;
        skin.joints[0] = first.joints[0];
        skin.joints[1] = second.joints[0];
        skin.weights[0] = weightFirst / 256.0f;
        skin.weights[1] = weightSecond / 256.0f;
        store(slots, readByte(records, at + 7), skin);
        return skin;
    }

    private static Skin threeWayVertex(Skin[] slots, byte[] records, int at)
            throws AssetFormatException {
        Skin first = loadJoint(slots, readByte(records, at + 2));
        Skin second = loadJoint(slots, readByte(records, at + 3));
        Skin third = loadJoint(slots, readByte(records, at + 1) & 0xFE);
        int weightFirst = readByte(records, at + 4);
        int weightSecond = readByte(records, at + 5);
        int weightThird = readByte(records, at + 6);
        if (weightFirst + weightSecond + weightThird != 256) {
            throw new AssetFormatException("A vertex of the moby class has weights that do not "
                    + "add up.");
        }
        Skin skin = new Skin();
        skin.count = 3;
        skin.joints[0] = first.joints[0];
        skin.joints[1] = second.joints[0];
        skin.joints[2] = third.joints[0];
        skin.weights[0] = weightFirst / 256.0f;
        skin.weights[1] = weightSecond / 256.0f;
        skin.weights[2] = weightThird / 256.0f;
        store(slots, readByte(records, at + 7), skin);
        return skin;
    }

    private static Skin singleVertex(Skin[] slots, byte[] records, int at)
            throws AssetFormatException {
        store(slots, readByte(records, at + 3), Skin.single(readByte(records, at + 1) >> 1));
        return load(slots, readByte(records, at + 2));
    }

    private static void store(Skin[] slots, int address, Skin skin) throws AssetFormatException {
        if (address % 4 != 0 || address >= VU0_SLOTS) {
            throw new AssetFormatException("A vertex of the moby class writes to a place of the "
                    + "memory of the vector unit that is not aligned.");
        }
        slots[address] = skin;
    }

    private static Skin load(Skin[] slots, int address) throws AssetFormatException {
        if (address % 4 != 0 || address >= 0xF4 || address >= VU0_SLOTS) {
            throw new AssetFormatException("A vertex of the moby class reads from a place of the "
                    + "memory of the vector unit that cannot be read.");
        }
        Skin skin = slots[address];
        if (skin == null) {
            throw new AssetFormatException("A vertex of the moby class reads from a place of the "
                    + "memory of the vector unit that was never written.");
        }
        return skin;
    }

    private static Skin loadJoint(Skin[] slots, int address) throws AssetFormatException {
        Skin skin = load(slots, address);
        if (skin.count != 1) {
            throw new AssetFormatException("A vertex of the moby class blends a matrix that is a "
                    + "blend of other matrices.");
        }
        return skin;
    }

    private static int[] cacheIds(byte[] records, int inFile) throws AssetFormatException {
        int count = records.length / VERTEX_SIZE;
        int[] ids = new int[inFile];
        for (int index = 0; index < inFile; index++) {
            int source = index + 7;
            if (source < count) {
                ids[index] = readUnsignedShort(records, source * VERTEX_SIZE) & 0x1FF;
            } else {
                int at = (count - 1) * VERTEX_SIZE + 4 + (index - (count - 7)) * 2;
                if (at + 2 > records.length) {
                    throw new AssetFormatException("The vertices of a packet of the moby class do "
                            + "not hold enough numbers of the cache of the vertices.");
                }
                ids[index] = readUnsignedShort(records, at) & 0x1FF;
            }
        }
        return ids;
    }

    private static float[] normal(int azimuth, int elevation) {
        double angle = azimuth * (Math.PI / 128.0);
        double tilt = elevation * (Math.PI / 128.0);
        return new float[] { (float) (Math.cos(angle) * Math.cos(tilt)),
                (float) (Math.sin(angle) * Math.cos(tilt)), (float) Math.sin(tilt) };
    }

    private byte[] slice(int offset, int length) throws AssetFormatException {
        if (offset < 0 || length < 0 || offset > blob.length - length) {
            throw new AssetFormatException("A part of the moby class lies outside of the class.");
        }
        byte[] result = new byte[length];
        System.arraycopy(blob, offset, result, 0, length);
        return result;
    }

    private static float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static int[] toInts(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static int readByte(byte[] data, int offset) {
        return data[offset] & 0xFF;
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
