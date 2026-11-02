package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * The writer and the reader of the cache file.
 *
 * <p>The file starts with a header of 64 bytes, followed by a table that names the sections of the
 * file, followed by the sections themselves. Every integer is stored in the little endian order.
 *
 * <pre>
 * 0x00  4 bytes  the magic "RACM"
 * 0x04  2 bytes  the version of the format
 * 0x06  2 bytes  the size of the header
 * 0x08  4 bytes  the version of the schema of the assets
 * 0x0C  4 bytes  the flags of the file, zero
 * 0x10  4 bytes  the number of the bytes of the identity of the disc
 * 0x14  4 bytes  the number of the sections
 * 0x18  4 bytes  the offset of the table of the sections
 * 0x1C  4 bytes  the length of the table of the sections
 * 0x20  8 bytes  the length of the payload
 * 0x28  4 bytes  the checksum of the payload
 * 0x2C  4 bytes  the checksum of the header
 * 0x30 16 bytes  reserved, zero
 * </pre>
 *
 * <p>A section of the table names the kind of the section, where it lies in the file, how long it
 * is and the checksum of its bytes. A file is only used when its header, its table, the checksum of
 * its payload and the checksum of every one of its sections agree, and when the assets in it can
 * be read.
 */
public final class Rac1CacheCodec {

    /** The magic that every cache file starts with. */
    public static final byte[] MAGIC = { 'R', 'A', 'C', 'M' };

    /** The version of the format of the cache file. */
    public static final int FORMAT_VERSION = 1;

    /** The size of the header of a cache file in bytes. */
    public static final int HEADER_SIZE = 64;

    /** The size of an entry of the table of the sections in bytes. */
    public static final int SECTION_ENTRY_SIZE = 32;

    /** The kind of the section that holds the static asset. */
    public static final int SECTION_STATIC = 1;

    /** The kind of the section that holds the animated asset. */
    public static final int SECTION_ANIMATED = 2;

    private Rac1CacheCodec() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The checksum of bytes.
     *
     * @param data the bytes
     * @return the checksum
     */
    public static int checksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }

    /**
     * The checksum of bytes.
     *
     * @param data the bytes
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return the checksum
     */
    public static int checksum(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return (int) crc.getValue();
    }

    /**
     * Writes the content of a cache into a file.
     *
     * @param content the content of the cache
     * @param schemaVersion the version of the schema of the assets
     * @return the bytes of the file
     * @throws IOException when the content cannot be written
     */
    public static byte[] write(Rac1CacheContent content, int schemaVersion) throws IOException {
        byte[] discKey = content.discKey().getBytes(StandardCharsets.UTF_8);
        byte[] staticSection = writeStatic(content);
        byte[] animatedSection = writeAnimated(content);

        int sectionCount = 2;
        int tableLength = sectionCount * SECTION_ENTRY_SIZE;
        int tableOffset = HEADER_SIZE;
        int keyOffset = tableOffset + tableLength;
        int staticOffset = keyOffset + discKey.length;
        int payloadOffset = keyOffset;
        int payloadLength = discKey.length + staticSection.length + animatedSection.length;

        byte[] file = new byte[payloadOffset + payloadLength];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        writeShort(file, 4, FORMAT_VERSION);
        writeShort(file, 6, HEADER_SIZE);
        writeInt(file, 8, schemaVersion);
        writeInt(file, 12, 0);
        writeInt(file, 16, discKey.length);
        writeInt(file, 20, sectionCount);
        writeInt(file, 24, tableOffset);
        writeInt(file, 28, tableLength);
        writeLong(file, 32, payloadLength);
        System.arraycopy(discKey, 0, file, keyOffset, discKey.length);
        System.arraycopy(staticSection, 0, file, staticOffset, staticSection.length);
        System.arraycopy(animatedSection, 0, file, staticOffset + staticSection.length,
                animatedSection.length);
        writeInt(file, 40, checksum(file, payloadOffset, payloadLength));
        writeInt(file, 44, checksum(file, 0, 44));

        writeSection(file, tableOffset, SECTION_STATIC, staticOffset, staticSection);
        writeSection(file, tableOffset + SECTION_ENTRY_SIZE, SECTION_ANIMATED,
                staticOffset + staticSection.length, animatedSection);
        return file;
    }

    /**
     * Reads the content of a cache out of a file.
     *
     * @param file the bytes of the file
     * @param expectedKey the identity of the disc the assets have to come from
     * @param schemaVersion the version of the schema of the assets
     * @return the content of the cache
     * @throws IOException when the file is damaged or does not hold the expected assets
     */
    public static Rac1CacheContent read(byte[] file, String expectedKey, int schemaVersion)
            throws IOException {
        if (file.length < HEADER_SIZE) {
            throw new IOException("The cache file is too short to hold a header.");
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (file[index] != MAGIC[index]) {
                throw new IOException("The cache file does not start with the magic of the format.");
            }
        }
        int formatVersion = readShort(file, 4);
        if (formatVersion != FORMAT_VERSION) {
            throw new IOException("The cache file has the format version " + formatVersion
                    + " instead of " + FORMAT_VERSION + ".");
        }
        int headerSize = readShort(file, 6);
        if (headerSize != HEADER_SIZE) {
            throw new IOException("The cache file has a header of " + headerSize + " bytes.");
        }
        if (checksum(file, 0, 44) != readInt(file, 44)) {
            throw new IOException("The header of the cache file is damaged.");
        }
        int fileSchema = readInt(file, 8);
        if (fileSchema != schemaVersion) {
            throw new IOException("The cache file was written for the schema version " + fileSchema
                    + " instead of " + schemaVersion + ".");
        }
        int keyLength = readInt(file, 16);
        int sectionCount = readInt(file, 20);
        int tableOffset = readInt(file, 24);
        int tableLength = readInt(file, 28);
        long payloadLength = readLong(file, 32);
        if (keyLength < 0 || sectionCount < 0 || sectionCount > 16
                || tableOffset != HEADER_SIZE || tableLength != sectionCount * SECTION_ENTRY_SIZE
                || payloadLength < 0
                || (long) tableOffset + tableLength + payloadLength > file.length) {
            throw new IOException("The cache file has a table of sections that does not fit.");
        }
        int payloadOffset = tableOffset + tableLength;
        if (checksum(file, payloadOffset, (int) payloadLength) != readInt(file, 40)) {
            throw new IOException("The payload of the cache file is damaged.");
        }
        byte[] key = new byte[keyLength];
        if (keyLength > 0) {
            System.arraycopy(file, payloadOffset, key, 0, keyLength);
        }
        String discKey = new String(key, StandardCharsets.UTF_8);
        if (!discKey.equals(expectedKey)) {
            throw new IOException("The cache file was written for another disc.");
        }
        byte[] staticSection = null;
        byte[] animatedSection = null;
        for (int index = 0; index < sectionCount; index++) {
            int entry = tableOffset + index * SECTION_ENTRY_SIZE;
            int kind = readInt(file, entry);
            long offset = readLong(file, entry + 8);
            long length = readLong(file, entry + 16);
            int sectionChecksum = readInt(file, entry + 24);
            if (offset < 0 || length < 0 || offset > file.length - length) {
                throw new IOException("A section of the cache file lies outside of the file.");
            }
            byte[] section = new byte[(int) length];
            System.arraycopy(file, (int) offset, section, 0, (int) length);
            if (checksum(section) != sectionChecksum) {
                throw new IOException("A section of the cache file is damaged.");
            }
            if (kind == SECTION_STATIC) {
                staticSection = section;
            } else if (kind == SECTION_ANIMATED) {
                animatedSection = section;
            }
        }
        if (staticSection == null || animatedSection == null) {
            throw new IOException("The cache file does not hold both assets.");
        }
        Rac1CacheContent content = new Rac1CacheContent(discKey, readStatic(staticSection),
                readAnimated(animatedSection));
        return content;
    }

    private static void writeSection(byte[] file, int entry, int kind, int offset, byte[] section) {
        writeInt(file, entry, kind);
        writeInt(file, entry + 4, 0);
        writeLong(file, entry + 8, offset);
        writeLong(file, entry + 16, section.length);
        writeInt(file, entry + 24, checksum(section));
        writeInt(file, entry + 28, 0);
    }

    private static byte[] writeStatic(Rac1CacheContent content) throws IOException {
        com.github.giannigr.racmc.asset.Rac1StaticAsset asset = content.staticAsset();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        com.github.giannigr.racmc.asset.Rac1Mesh mesh = asset.mesh();
        out.writeInt(mesh.vertexCount());
        out.writeInt(mesh.triangleCount());
        out.writeInt(asset.textures().length);
        writeFloats(out, mesh.positions());
        writeFloats(out, mesh.normals());
        writeFloats(out, mesh.uvs());
        writeInts(out, mesh.indices());
        writeInts(out, mesh.materials());
        writeTextures(out, asset.textures());
        out.flush();
        return bytes.toByteArray();
    }

    private static Rac1StaticAsset readStatic(byte[] section) throws IOException {
        java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(section));
        int vertexCount = in.readInt();
        int triangleCount = in.readInt();
        int textureCount = in.readInt();
        if (vertexCount < 0 || triangleCount < 0 || textureCount < 0 || vertexCount > 1 << 20
                || triangleCount > 1 << 20 || textureCount > 64) {
            throw new IOException("The static asset of the cache file has implausible sizes.");
        }
        float[] positions = readFloats(in, vertexCount * 3);
        float[] normals = readFloats(in, vertexCount * 3);
        float[] uvs = readFloats(in, vertexCount * 2);
        int[] indices = readInts(in, triangleCount * 3);
        int[] materials = readInts(in, triangleCount);
        com.github.giannigr.racmc.asset.Rac1Texture[] textures = readTextures(in, textureCount);
        com.github.giannigr.racmc.asset.Rac1Mesh mesh = new com.github.giannigr.racmc.asset.Rac1Mesh(
                positions, normals, uvs, indices, materials);
        return new Rac1StaticAsset(0, "static", mesh, textures);
    }

    private static byte[] writeAnimated(Rac1CacheContent content) throws IOException {
        com.github.giannigr.racmc.asset.Rac1AnimatedAsset asset = content.animatedAsset();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        com.github.giannigr.racmc.asset.Rac1SkinnedMesh mesh = asset.mesh();
        com.github.giannigr.racmc.asset.Rac1Skeleton skeleton = asset.skeleton();
        out.writeInt(mesh.vertexCount());
        out.writeInt(mesh.triangleCount());
        out.writeInt(skeleton.jointCount());
        out.writeInt(asset.animations().length);
        out.writeInt(asset.textures().length);
        writeFloats(out, mesh.positions());
        writeFloats(out, mesh.normals());
        writeFloats(out, mesh.uvs());
        writeInts(out, mesh.indices());
        writeInts(out, mesh.materials());
        writeInts(out, mesh.joints());
        writeFloats(out, mesh.weights());
        writeFloats(out, skeleton.inverseBind());
        writeFloats(out, skeleton.restTranslations());
        writeInts(out, skeleton.parents());
        for (com.github.giannigr.racmc.asset.Rac1Animation animation : asset.animations()) {
            byte[] name = animation.name().getBytes(StandardCharsets.UTF_8);
            out.writeInt(name.length);
            out.write(name);
            out.writeInt(animation.keyCount());
            out.writeBoolean(animation.loop());
            writeFloats(out, animation.times());
            writeFloats(out, animation.rotations());
            int translationCount = animation.translationKeys().length;
            out.writeInt(translationCount);
            for (int index = 0; index < translationCount; index++) {
                out.writeInt(animation.translationKeys()[index]);
                out.writeInt(animation.translationJoints()[index]);
                writeFloats(out, new float[] { animation.translationValues()[index * 3],
                        animation.translationValues()[index * 3 + 1],
                        animation.translationValues()[index * 3 + 2] });
            }
            int scaleCount = animation.scaleKeys().length;
            out.writeInt(scaleCount);
            for (int index = 0; index < scaleCount; index++) {
                out.writeInt(animation.scaleKeys()[index]);
                out.writeInt(animation.scaleJoints()[index]);
                writeFloats(out, new float[] { animation.scaleValues()[index * 3],
                        animation.scaleValues()[index * 3 + 1],
                        animation.scaleValues()[index * 3 + 2] });
                out.writeBoolean(animation.scaleInherited()[index]);
            }
        }
        writeTextures(out, asset.textures());
        out.flush();
        return bytes.toByteArray();
    }

    private static Rac1AnimatedAsset readAnimated(byte[] section) throws IOException {
        java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(section));
        int vertexCount = in.readInt();
        int triangleCount = in.readInt();
        int jointCount = in.readInt();
        int sequenceCount = in.readInt();
        int textureCount = in.readInt();
        if (vertexCount < 0 || triangleCount < 0 || jointCount < 0 || sequenceCount < 0
                || textureCount < 0 || vertexCount > 1 << 20 || triangleCount > 1 << 20
                || jointCount > 4096 || sequenceCount > 4096 || textureCount > 64) {
            throw new IOException("The animated asset of the cache file has implausible sizes.");
        }
        float[] positions = readFloats(in, vertexCount * 3);
        float[] normals = readFloats(in, vertexCount * 3);
        float[] uvs = readFloats(in, vertexCount * 2);
        int[] indices = readInts(in, triangleCount * 3);
        int[] materials = readInts(in, triangleCount);
        int[] joints = readInts(in, vertexCount * 3);
        float[] weights = readFloats(in, vertexCount * 3);
        float[] inverseBind = readFloats(in, jointCount * 16);
        float[] restTranslations = readFloats(in, jointCount * 3);
        int[] parents = readInts(in, jointCount);
        com.github.giannigr.racmc.asset.Rac1Animation[] animations =
                new com.github.giannigr.racmc.asset.Rac1Animation[sequenceCount];
        for (int index = 0; index < sequenceCount; index++) {
            int nameLength = in.readInt();
            if (nameLength < 0 || nameLength > 1024) {
                throw new IOException("The name of an animation of the cache file is implausible.");
            }
            byte[] name = new byte[nameLength];
            in.readFully(name);
            int keyCount = in.readInt();
            boolean loop = in.readBoolean();
            if (keyCount < 0 || keyCount > 1 << 16) {
                throw new IOException("An animation of the cache file has an implausible number of "
                        + "keys.");
            }
            float[] times = readFloats(in, keyCount);
            if (keyCount * (long) jointCount * 4 > Integer.MAX_VALUE) {
                throw new IOException("An animation of the cache file has too many keys.");
            }
            float[] rotations = readFloats(in, keyCount * jointCount * 4);
            int translationCount = in.readInt();
            int[] translationKeys = new int[translationCount];
            int[] translationJoints = new int[translationCount];
            float[] translationValues = new float[translationCount * 3];
            for (int record = 0; record < translationCount; record++) {
                translationKeys[record] = in.readInt();
                translationJoints[record] = in.readInt();
                translationValues[record * 3] = in.readFloat();
                translationValues[record * 3 + 1] = in.readFloat();
                translationValues[record * 3 + 2] = in.readFloat();
            }
            int scaleCount = in.readInt();
            int[] scaleKeys = new int[scaleCount];
            int[] scaleJoints = new int[scaleCount];
            float[] scaleValues = new float[scaleCount * 3];
            boolean[] scaleInherited = new boolean[scaleCount];
            for (int record = 0; record < scaleCount; record++) {
                scaleKeys[record] = in.readInt();
                scaleJoints[record] = in.readInt();
                scaleValues[record * 3] = in.readFloat();
                scaleValues[record * 3 + 1] = in.readFloat();
                scaleValues[record * 3 + 2] = in.readFloat();
                scaleInherited[record] = in.readBoolean();
            }
            animations[index] = new com.github.giannigr.racmc.asset.Rac1Animation(
                    new String(name, StandardCharsets.UTF_8), times, loop, rotations,
                    translationKeys, translationJoints, translationValues, scaleKeys, scaleJoints,
                    scaleValues, scaleInherited);
        }
        com.github.giannigr.racmc.asset.Rac1Texture[] textures = readTextures(in, textureCount);
        com.github.giannigr.racmc.asset.Rac1SkinnedMesh mesh =
                new com.github.giannigr.racmc.asset.Rac1SkinnedMesh(positions, normals, uvs,
                        indices, materials, joints, weights);
        com.github.giannigr.racmc.asset.Rac1Skeleton skeleton =
                new com.github.giannigr.racmc.asset.Rac1Skeleton(inverseBind, parents,
                        restTranslations);
        return new com.github.giannigr.racmc.asset.Rac1AnimatedAsset(0, "animated", mesh, skeleton,
                animations, textures);
    }

    private static void writeTextures(DataOutputStream out,
                                      com.github.giannigr.racmc.asset.Rac1Texture[] textures)
            throws IOException {
        for (com.github.giannigr.racmc.asset.Rac1Texture texture : textures) {
            out.writeInt(texture.width());
            out.writeInt(texture.height());
            out.write(texture.pixels());
        }
    }

    private static com.github.giannigr.racmc.asset.Rac1Texture[] readTextures(
            java.io.DataInputStream in, int count) throws IOException {
        com.github.giannigr.racmc.asset.Rac1Texture[] textures =
                new com.github.giannigr.racmc.asset.Rac1Texture[count];
        for (int index = 0; index < count; index++) {
            int width = in.readInt();
            int height = in.readInt();
            if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                throw new IOException("A texture of the cache file has an implausible size.");
            }
            byte[] pixels = new byte[width * height * 4];
            in.readFully(pixels);
            textures[index] = new com.github.giannigr.racmc.asset.Rac1Texture(width, height, pixels);
        }
        return textures;
    }

    private static void writeFloats(DataOutputStream out, float[] values) throws IOException {
        for (float value : values) {
            out.writeFloat(value);
        }
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        for (int value : values) {
            out.writeInt(value);
        }
    }

    private static float[] readFloats(java.io.DataInputStream in, int count) throws IOException {
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = in.readFloat();
        }
        return values;
    }

    private static int[] readInts(java.io.DataInputStream in, int count) throws IOException {
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = in.readInt();
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

    private static void writeLong(byte[] data, int offset, long value) {
        for (int index = 0; index < 8; index++) {
            data[offset + index] = (byte) ((value >> (index * 8)) & 0xFF);
        }
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readLong(byte[] data, int offset) {
        long value = 0;
        for (int index = 7; index >= 0; index--) {
            value = (value << 8) | (data[offset + index] & 0xFFL);
        }
        return value;
    }
}
