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
        return new Mesh(toArray(positions), toArray(normals), toArray(uvs), toInts(triangles),
                toInts(materials), toInts(joints), toArray(weights));
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
                    // The game reads the rows of the matrix as the columns of the matrix it
                    // works with.
                    inverseBind[joint * 16 + column * 4 + row] =
                            readFloat(blob, matrix + row * 16 + column * 4);
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
            for (int index = 2; index < usable; index++) {
                if (pushed.get(index)[1] != 0) {
                    continue;
                }
                triangles.add(pushed.get(index - 2)[0]);
                triangles.add(pushed.get(index - 1)[0]);
                triangles.add(pushed.get(index)[0]);
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
