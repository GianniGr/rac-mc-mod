package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1SourceTiming;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.Rac1VendorAsset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 0x0C  4 bytes  the version of the converter that wrote the file
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
 *
 * <h2>The manifest of the cache</h2>
 *
 * <p>The header of the file is the manifest of the cache: it names the magic, the format, the
 * schema of the assets, the converter that wrote the file, the identity of the disc and the table
 * of the sections with their checksums. Everything a reader needs to decide whether the file can
 * be used is in the file itself, so the cache needs no separate manifest file.
 *
 * <h2>The three versions and when they change</h2>
 *
 * <p>The file carries three different versions, and each of them is checked on its own:
 *
 * <ul>
 *   <li>{@link #FORMAT_VERSION} is the layout of the container - the header, the table of the
 *       sections and the meaning of the words of the header. It is bumped when a file of the old
 *       layout cannot be read any more. It is {@code 2}: the word at {@code 0x0C} used to be the
 *       flags of the file and now holds the version of the converter.</li>
 *   <li>{@link Rac1CacheStore#SCHEMA_VERSION} is the layout of the bytes inside the sections -
 *       what the converter writes for the scenery, the hero and the Blaster. It is bumped when the
 *       encoding of the assets changes, for example when a field is added to an asset.</li>
 *   <li>{@link #CONVERTER_VERSION} is the identity of the converter that wrote the file. It is
 *       bumped when the conversion itself changes in a way that makes an older result
 *       unacceptable, even though the format and the schema of the file are unchanged - a
 *       different level of detail, a different change of the axes or a different choice of the
 *       classes to convert. It is not a copy of the schema version: a file can hold the current
 *       schema and still have been written by an older converter.</li>
 * </ul>
 *
 * <p>The versions live in the header of the cache file, at the offsets above. That is where the
 * manifest of the cache is; there is no second file that carries them. A file whose format,
 * schema or converter version is not the current one is refused with a message that names the
 * version that was found, and the manager of the cache builds the file again instead of using it.
 */
public final class Rac1CacheCodec {
    /**
     * Wire layout of complete static/animated model payloads exposed by this codec.
     * Independent of RACM catalog, converter and typed sidecar envelope versions.
     * Metadata-only source APIs and validation tightening do not change this identifier.
     */
    public static final int MODEL_ENCODING_VERSION = 1;

    /** The magic that every cache file starts with. */
    public static final byte[] MAGIC = { 'R', 'A', 'C', 'M' };

    /** The version of the format of the cache file. */
    public static final int FORMAT_VERSION = 2;

    /** The version of the converter that writes cache files. */
    public static final int CONVERTER_VERSION = 7;

    /** The offset of the version of the converter in the header. */
    private static final int CONVERTER_OFFSET = 12;

    /** The size of the header of a cache file in bytes. */
    public static final int HEADER_SIZE = 64;

    /** The size of an entry of the table of the sections in bytes. */
    public static final int SECTION_ENTRY_SIZE = 32;

    /** The kind of the section that holds the static asset. */
    public static final int SECTION_STATIC = 1;

    /** The kind of the section that holds the animated asset. */
    public static final int SECTION_ANIMATED = 2;

    /** The kind of the section that holds the weapon. */
    public static final int SECTION_BLASTER = 3;

    /** The kind of the section that holds the crates. */
    public static final int SECTION_CRATES = 4;
    public static final int SECTION_VENDOR = 5;
    public static final int SECTION_HUD = 6;

    /** Gadget 71, distinct from vendor 5 and HUD 6. Sound/effects use separate sidecars. */
    public static final int SECTION_WRENCH = 71;

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
        return checksum(data, 0, data.length);
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
     * <p>The header of the file carries the format, the schema of the assets and the version of
     * the converter, so the file says on its own what it needs to be read again.
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
        byte[] blasterSection = writeBlaster(content);
        byte[] cratesSection = writeCrates(content);
        byte[] vendorSection = writeVendor(content.vendor());
        byte[] hudSection = Rac1HudCacheCodec.write(content.hud());
        byte[] wrenchSection = content.wrench() == null ? new byte[0]
                : writeAnimatedAsset(content.wrench());

        int sectionCount = 7;
        int tableLength = sectionCount * SECTION_ENTRY_SIZE;
        int tableOffset = HEADER_SIZE;
        int keyOffset = tableOffset + tableLength;
        int staticOffset = keyOffset + discKey.length;
        int animatedOffset = staticOffset + staticSection.length;
        int blasterOffset = animatedOffset + animatedSection.length;
        int cratesOffset = blasterOffset + blasterSection.length;
        int vendorOffset = cratesOffset + cratesSection.length;
        int hudOffset = vendorOffset + vendorSection.length;
        int wrenchOffset = hudOffset + hudSection.length;
        int payloadOffset = keyOffset;
        int payloadLength = discKey.length + staticSection.length + animatedSection.length
                + blasterSection.length + cratesSection.length + vendorSection.length
                + hudSection.length + wrenchSection.length;

        byte[] file = new byte[payloadOffset + payloadLength];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        writeShort(file, 4, FORMAT_VERSION);
        writeShort(file, 6, HEADER_SIZE);
        writeInt(file, 8, schemaVersion);
        writeInt(file, CONVERTER_OFFSET, CONVERTER_VERSION);
        writeInt(file, 16, discKey.length);
        writeInt(file, 20, sectionCount);
        writeInt(file, 24, tableOffset);
        writeInt(file, 28, tableLength);
        writeLong(file, 32, payloadLength);
        System.arraycopy(discKey, 0, file, keyOffset, discKey.length);
        System.arraycopy(staticSection, 0, file, staticOffset, staticSection.length);
        System.arraycopy(animatedSection, 0, file, animatedOffset, animatedSection.length);
        System.arraycopy(blasterSection, 0, file, blasterOffset, blasterSection.length);
        System.arraycopy(cratesSection, 0, file, cratesOffset, cratesSection.length);
        System.arraycopy(vendorSection, 0, file, vendorOffset, vendorSection.length);
        System.arraycopy(hudSection, 0, file, hudOffset, hudSection.length);
        System.arraycopy(wrenchSection, 0, file, wrenchOffset, wrenchSection.length);
        writeInt(file, 40, checksum(file, payloadOffset, payloadLength));
        writeInt(file, 44, checksum(file, 0, 44));

        writeSection(file, tableOffset, SECTION_STATIC, staticOffset, staticSection);
        writeSection(file, tableOffset + SECTION_ENTRY_SIZE, SECTION_ANIMATED, animatedOffset,
                animatedSection);
        writeSection(file, tableOffset + 2 * SECTION_ENTRY_SIZE, SECTION_BLASTER, blasterOffset,
                blasterSection);
        writeSection(file, tableOffset + 3 * SECTION_ENTRY_SIZE, SECTION_CRATES, cratesOffset,
                cratesSection);
        writeSection(file, tableOffset + 4 * SECTION_ENTRY_SIZE, SECTION_VENDOR, vendorOffset,
                vendorSection);
        writeSection(file, tableOffset + 5 * SECTION_ENTRY_SIZE, SECTION_HUD, hudOffset, hudSection);
        writeSection(file, tableOffset + 6 * SECTION_ENTRY_SIZE, SECTION_WRENCH, wrenchOffset,
                wrenchSection);
        return file;
    }

    /**
     * Reads the content of a cache out of a file.
     *
     * <p>A file of another format, of another schema or of another converter is refused with a
     * message that names the version that was found. The caller builds the cache again in that
     * case; a file of an older version is therefore never used and never crashes the read.
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
        int converterVersion = readInt(file, CONVERTER_OFFSET);
        if (converterVersion != CONVERTER_VERSION) {
            throw new IOException("The cache file was written by the converter version "
                    + converterVersion + " instead of " + CONVERTER_VERSION + ".");
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
        if (keyLength < 0 || keyLength > 4096 || sectionCount < 0 || sectionCount > 16
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
        byte[] blasterSection = null;
        byte[] cratesSection = null;
        byte[] vendorSection = null;
        byte[] hudSection = null;
        byte[] wrenchSection = null;
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
            } else if (kind == SECTION_BLASTER) {
                blasterSection = section;
            } else if (kind == SECTION_CRATES) {
                cratesSection = section;
            } else if (kind == SECTION_VENDOR) {
                if (vendorSection != null) {
                    throw new IOException("The cache file holds duplicate vendor sections.");
                }
                vendorSection = section;
            } else if (kind == SECTION_HUD) {
                if (hudSection != null) throw new IOException("Duplicate HUD cache section");
                hudSection = section;
            } else if (kind == SECTION_WRENCH) {
                if (wrenchSection != null) {
                    throw new IOException("The cache has duplicate wrench sections.");
                }
                wrenchSection = section;
            }
        }
        if (staticSection == null || animatedSection == null || blasterSection == null
                || cratesSection == null || vendorSection == null || hudSection == null
                || wrenchSection == null) {
            throw new IOException("The cache file does not hold all of the assets.");
        }
        int handJoint = readInt(blasterSection, 0);
        Rac1CacheContent content = new Rac1CacheContent(discKey, readStatic(staticSection),
                readAnimated(animatedSection), readBlaster(blasterSection), handJoint,
                readCrates(cratesSection), readVendor(vendorSection), Rac1HudCacheCodec.read(hudSection),
                wrenchSection.length == 0 ? null : readAnimatedAsset(wrenchSection));
        if (content.wrench() != null && content.wrench().classNumber() != 71) {
            throw new IOException("The wrench section does not hold gadget 71.");
        }
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
        return writeStaticAsset(content.staticAsset());
    }

    private static byte[] writeStaticAsset(Rac1StaticAsset asset) throws IOException {
        Rac1Bytes.Writer out = new Rac1Bytes.Writer();
        Rac1Mesh mesh = asset.mesh();
        byte[] name = asset.name().getBytes(StandardCharsets.UTF_8);
        out.putInt(asset.classNumber());
        out.putInt(name.length);
        out.putBytes(name);
        out.putInt(mesh.vertexCount());
        out.putInt(mesh.triangleCount());
        out.putInt(asset.textures().length);
        putFloats(out, mesh.positions());
        putFloats(out, mesh.normals());
        putFloats(out, mesh.uvs());
        putInts(out, mesh.indices());
        putInts(out, mesh.materials());
        putTextures(out, asset.textures());
        return out.toByteArray();
    }

    private static Rac1StaticAsset readStatic(byte[] section) throws IOException {
        Rac1Bytes.Reader in = new Rac1Bytes.Reader(section);
        int classNumber = in.getInt();
        int nameLength = in.getInt();
        if (nameLength < 0 || nameLength > 1024) {
            throw new IOException("The name of an asset of the cache file is implausible.");
        }
        byte[] name = new byte[nameLength];
        in.getBytes(name);
        int vertexCount = in.getInt();
        int triangleCount = in.getInt();
        int textureCount = in.getInt();
        if (vertexCount < 0 || triangleCount < 0 || textureCount < 0 || vertexCount > 1 << 20
                || triangleCount > 1 << 20 || textureCount > 64) {
            throw new IOException("The static asset of the cache file has implausible sizes.");
        }
        float[] positions = getFloats(in, vertexCount * 3);
        float[] normals = getFloats(in, vertexCount * 3);
        float[] uvs = getFloats(in, vertexCount * 2);
        int[] indices = getInts(in, triangleCount * 3);
        int[] materials = getInts(in, triangleCount);
        Rac1Texture[] textures = getTextures(in, textureCount);
        if (in.remaining() != 0) throw new IOException("Trailing static model data.");
        Rac1Mesh mesh = new Rac1Mesh(positions, normals, uvs, indices, materials);
        return new Rac1StaticAsset(classNumber, new String(name, StandardCharsets.UTF_8), mesh,
                textures);
    }

    private static byte[] writeAnimated(Rac1CacheContent content) throws IOException {
        return writeAnimatedAsset(content.animatedAsset());
    }

    private static byte[] writeVendor(Rac1VendorAsset vendor) throws IOException {
        Rac1Bytes.Writer out = new Rac1Bytes.Writer();
        // Empty is supported for synthetic fixtures; the production builder always imports both.
        out.putBoolean(vendor != null);
        if (vendor != null) {
            byte[] body = writeAnimatedAsset(vendor.body());
            byte[] hologram = writeAnimatedAsset(vendor.hologram());
            out.putInt(body.length);
            out.putBytes(body);
            out.putInt(hologram.length);
            out.putBytes(hologram);
        }
        return out.toByteArray();
    }

    private static Rac1VendorAsset readVendor(byte[] section) throws IOException {
        Rac1Bytes.Reader in = new Rac1Bytes.Reader(section);
        if (!in.getBoolean()) {
            if (in.remaining() != 0) {
                throw new IOException("An empty vendor section has trailing data.");
            }
            return null;
        }
        Rac1AnimatedAsset[] pair = new Rac1AnimatedAsset[2];
        for (int index = 0; index < pair.length; index++) {
            int length = in.getInt();
            if (length <= 0 || length > in.remaining()) {
                throw new IOException("A vendor asset does not fit into its cache section.");
            }
            byte[] bytes = new byte[length];
            in.getBytes(bytes);
            pair[index] = readAnimatedAsset(bytes);
        }
        if (in.remaining() != 0) {
            throw new IOException("The vendor section has trailing data.");
        }
        try {
            return new Rac1VendorAsset(pair[0], pair[1]);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The cache holds an invalid vendor pair.", exception);
        }
    }

    /**
     * Writes the crates of the cache into a section of their own.
     *
     * <p>The crates are written in the order they were converted, and every crate is written
     * with its key, so that the reader can find a crate by its key again.
     *
     * @param content the content of the cache
     * @return the bytes of the section
     */
    private static byte[] writeCrates(Rac1CacheContent content) {
        Rac1Bytes.Writer out = new Rac1Bytes.Writer();
        out.putInt(content.crates().size());
        for (Rac1CrateAsset crate : content.crates().values()) {
            byte[] key = crate.key().getBytes(StandardCharsets.UTF_8);
            Rac1Mesh mesh = crate.mesh();
            out.putInt(key.length);
            out.putBytes(key);
            out.putInt(crate.classNumber());
            out.putInt(mesh.vertexCount());
            out.putInt(mesh.triangleCount());
            out.putInt(crate.textures().length);
            putFloats(out, mesh.positions());
            putFloats(out, mesh.normals());
            putFloats(out, mesh.uvs());
            putInts(out, mesh.indices());
            putInts(out, mesh.materials());
            putTextures(out, crate.textures());
        }
        return out.toByteArray();
    }

    /**
     * Reads the crates of the cache out of the section that holds them.
     *
     * @param section the bytes of the section
     * @return the crates, by the key they are known by
     * @throws IOException when the section is damaged
     */
    private static Map<String, Rac1CrateAsset> readCrates(byte[] section) throws IOException {
        Rac1Bytes.Reader in = new Rac1Bytes.Reader(section);
        int count = in.getInt();
        if (count < 0 || count > 64) {
            throw new IOException("The cache file holds an implausible number of crates.");
        }
        Map<String, Rac1CrateAsset> crates = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            int keyLength = in.getInt();
            if (keyLength < 0 || keyLength > 1024) {
                throw new IOException("The key of a crate of the cache file is implausible.");
            }
            byte[] key = new byte[keyLength];
            in.getBytes(key);
            int classNumber = in.getInt();
            int vertexCount = in.getInt();
            int triangleCount = in.getInt();
            int textureCount = in.getInt();
            if (vertexCount < 0 || triangleCount < 0 || textureCount < 0
                    || vertexCount > 1 << 20 || triangleCount > 1 << 20 || textureCount > 64) {
                throw new IOException("A crate of the cache file has implausible sizes.");
            }
            float[] positions = getFloats(in, vertexCount * 3);
            float[] normals = getFloats(in, vertexCount * 3);
            float[] uvs = getFloats(in, vertexCount * 2);
            int[] indices = getInts(in, triangleCount * 3);
            int[] materials = getInts(in, triangleCount);
            Rac1Texture[] textures = getTextures(in, textureCount);
            Rac1Mesh mesh = new Rac1Mesh(positions, normals, uvs, indices, materials);
            String name = new String(key, StandardCharsets.UTF_8);
            crates.put(name, new Rac1CrateAsset(name, classNumber, mesh, textures));
        }
        return crates;
    }

    private static byte[] writeBlaster(Rac1CacheContent content) throws IOException {
        Rac1Bytes.Writer out = new Rac1Bytes.Writer();
        out.putInt(content.handJoint());
        out.putBytes(writeAnimatedAsset(content.blaster()));
        return out.toByteArray();
    }

    private static byte[] writeAnimatedAsset(Rac1AnimatedAsset asset) throws IOException {
        Rac1Bytes.Writer out = new Rac1Bytes.Writer();
        Rac1SkinnedMesh mesh = asset.mesh();
        Rac1Skeleton skeleton = asset.skeleton();
        byte[] name = asset.name().getBytes(StandardCharsets.UTF_8);
        out.putInt(asset.classNumber());
        out.putInt(name.length);
        out.putBytes(name);
        out.putInt(mesh.vertexCount());
        out.putInt(mesh.triangleCount());
        out.putInt(skeleton.jointCount());
        out.putInt(asset.animations().length);
        out.putInt(asset.textures().length);
        putFloats(out, mesh.positions());
        putFloats(out, mesh.normals());
        putFloats(out, mesh.uvs());
        putInts(out, mesh.indices());
        putInts(out, mesh.materials());
        putInts(out, mesh.joints());
        putFloats(out, mesh.weights());
        putFloats(out, skeleton.inverseBind());
        putFloats(out, skeleton.restTranslations());
        putInts(out, skeleton.parents());
        out.putInt(asset.attachmentChainCount());
        for (int list = 0; list < asset.attachmentChainCount(); list++) {
            int[] chain = asset.attachmentChain(list);
            out.putInt(chain.length);
            putInts(out, chain);
        }
        for (Rac1Animation animation : asset.animations()) {
            byte[] sequenceName = animation.name().getBytes(StandardCharsets.UTF_8);
            out.putInt(sequenceName.length);
            out.putBytes(sequenceName);
            out.putInt(animation.keyCount());
            out.putBoolean(animation.loop());
            putFloats(out, animation.times());
            putFloats(out, animation.rotations());
            int translationCount = animation.translationKeys().length;
            out.putInt(translationCount);
            for (int index = 0; index < translationCount; index++) {
                out.putInt(animation.translationKeys()[index]);
                out.putInt(animation.translationJoints()[index]);
                putFloats(out, new float[] { animation.translationValues()[index * 3],
                        animation.translationValues()[index * 3 + 1],
                        animation.translationValues()[index * 3 + 2] });
            }
            int scaleCount = animation.scaleKeys().length;
            out.putInt(scaleCount);
            for (int index = 0; index < scaleCount; index++) {
                out.putInt(animation.scaleKeys()[index]);
                out.putInt(animation.scaleJoints()[index]);
                putFloats(out, new float[] { animation.scaleValues()[index * 3],
                        animation.scaleValues()[index * 3 + 1],
                        animation.scaleValues()[index * 3 + 2] });
                out.putBoolean(animation.scaleInherited()[index]);
            }
            Rac1SourceTiming timing = animation.sourceTiming();
            out.putInt(timing.sequenceRateBits());
            out.putInt(timing.headerTimes16().length);
            putInts(out, timing.headerTimes16());
            putInts(out, timing.keyRateBits());
            putFloats(out, timing.sourceSeconds());
            out.putInt(timing.sampledSourceKeys().length);
            putInts(out, timing.sampledSourceKeys());
        }
        putTextures(out, asset.textures());
        return out.toByteArray();
    }

    /** Reusable section payloads, not RACM containers. No format/version or valid byte-layout change. */
    public static final int MAX_MODEL_BYTES = 64 * 1024 * 1024;

    public static byte[] encodeStaticModel(Rac1StaticAsset asset) throws IOException {
        modelName(asset.classNumber(), asset.name());
        var m = asset.mesh();
        long size = validateMesh(m.positions(), m.normals(), m.uvs(), m.indices(), m.materials(), asset.textures());
        modelSize(size + 1056);
        return boundedModel(writeStaticAsset(asset));
    }
    public static Rac1StaticAsset decodeStaticModel(byte[] payload) throws IOException {
        boundedModel(payload);
        var asset = readStatic(payload);
        // Validate without reserializing or allocating another model payload.
        modelName(asset.classNumber(), asset.name());
        var m = asset.mesh();
        validateMesh(m.positions(), m.normals(), m.uvs(), m.indices(), m.materials(), asset.textures());
        return asset;
    }
    public static byte[] encodeAnimatedModel(Rac1AnimatedAsset asset) throws IOException {
        validateAnimatedModel(asset);
        return boundedModel(writeAnimatedAsset(asset));
    }
    public static Rac1AnimatedAsset decodeAnimatedModel(byte[] payload) throws IOException {
        boundedModel(payload);
        var asset = readAnimatedAsset(payload);
        validateAnimatedModel(asset);
        return asset;
    }

    private static byte[] boundedModel(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_MODEL_BYTES)
            throw new IOException("Invalid bounded model payload size.");
        return bytes;
    }
    private static void modelSize(long bytes) throws IOException {
        if (bytes > MAX_MODEL_BYTES) throw new IOException("Model exceeds bounded payload size.");
    }
    private static void modelName(int id, String name) throws IOException {
        if (id < 0 || id > 65535 || name == null || name.getBytes(StandardCharsets.UTF_8).length > 1024)
            throw new IOException("Invalid model identity/name.");
    }
    private static void finite(float[] values) throws IOException {
        for (float v : values) if (!Float.isFinite(v)) throw new IOException("Nonfinite model value.");
    }
    private static long validateMesh(float[] positions, float[] normals, float[] uvs,
                                     int[] indices, int[] materials, Rac1Texture[] textures) throws IOException {
        int vertices = positions.length / 3, triangles = indices.length / 3;
        if (positions.length % 3 != 0 || indices.length % 3 != 0 || vertices > 1 << 20
                || triangles > 1 << 20 || normals.length != positions.length || uvs.length != vertices * 2
                || materials.length != triangles || textures.length > 64)
            throw new IOException("Invalid model mesh array/count bounds.");
        finite(positions); finite(normals); finite(uvs);
        for (int index : indices) if (index < 0 || index >= vertices) throw new IOException("Invalid model vertex index.");
        for (int material : materials) if (material < -1 || material >= textures.length)
            throw new IOException("Invalid model material reference.");
        long size = ((long) positions.length + normals.length + uvs.length + indices.length + materials.length) * 4;
        for (var texture : textures) {
            if (texture.width() < 1 || texture.height() < 1 || texture.width() > 4096 || texture.height() > 4096
                    || texture.pixels().length != (long) texture.width() * texture.height() * 4)
                throw new IOException("Invalid model texture extent.");
            size += 8L + texture.pixels().length;
        }
        modelSize(size);
        return size;
    }
    private static void validateAnimatedModel(Rac1AnimatedAsset asset) throws IOException {
        modelName(asset.classNumber(), asset.name());
        var mesh = asset.mesh(); var skeleton = asset.skeleton();
        int joints = skeleton.jointCount();
        long size = validateMesh(mesh.positions(), mesh.normals(), mesh.uvs(), mesh.indices(), mesh.materials(), asset.textures()) + 1056;
        if (joints < 1 || joints > 4096 || skeleton.inverseBind().length != joints * 16
                || skeleton.restTranslations().length != joints * 3 || mesh.joints().length != mesh.vertexCount() * 3
                || mesh.weights().length != mesh.joints().length || asset.animations().length > 4096
                || asset.attachmentChainCount() > 1024)
            throw new IOException("Invalid model skeleton/skinning bounds.");
        finite(skeleton.inverseBind()); finite(skeleton.restTranslations()); finite(mesh.weights());
        for (int i = 0; i < joints; i++) if (skeleton.parents()[i] < -1 || skeleton.parents()[i] >= i)
            throw new IOException("Invalid model skeleton parent order.");
        for (int i = 0; i < mesh.weights().length; i++)
            if (mesh.weights()[i] < 0 || (mesh.weights()[i] != 0 && (mesh.joints()[i] < 0 || mesh.joints()[i] >= joints)))
                throw new IOException("Invalid model skin influence.");
        size += ((long) mesh.joints().length + mesh.weights().length + joints * 20L) * 4;
        for (int i = 0; i < asset.attachmentChainCount(); i++) {
            int[] chain = asset.attachmentChain(i);
            if (chain.length > 256) throw new IOException("Invalid model attachment count.");
            for (int joint : chain) if (joint < 0 || joint >= joints) throw new IOException("Invalid model attachment.");
            size += 4L + chain.length * 4L;
        }
        for (var a : asset.animations()) {
            modelName(0, a.name());
            int keys = a.keyCount(), translations = a.translationKeys().length, scales = a.scaleKeys().length;
            if (keys < 1 || keys > 1 << 16 || a.rotations().length != (long) keys * joints * 4
                    || translations > 1 << 20 || scales > 1 << 20
                    || a.translationJoints().length != translations || a.translationValues().length != translations * 3
                    || a.scaleJoints().length != scales || a.scaleValues().length != scales * 3
                    || a.scaleInherited().length != scales)
                throw new IOException("Invalid model animation array/count bounds.");
            finite(a.times()); finite(a.rotations()); finite(a.translationValues()); finite(a.scaleValues());
            for (int i = 0; i < keys; i++) if (a.times()[i] < 0 || (i > 0 && a.times()[i] < a.times()[i - 1]))
                throw new IOException("Invalid model animation time order.");
            validateChannels(a.translationKeys(), a.translationJoints(), keys, joints);
            validateChannels(a.scaleKeys(), a.scaleJoints(), keys, joints);
            size += 1100L + keys * 4L + a.rotations().length * 4L + translations * 20L + scales * 21L
                    + a.sourceTiming().headerTimes16().length * 12L + a.sourceTiming().sampledSourceKeys().length * 4L;
            modelSize(size);
        }
        modelSize(size);
    }
    private static void validateChannels(int[] keys, int[] joints, int keyCount, int jointCount) throws IOException {
        for (int i = 0; i < keys.length; i++)
            if (keys[i] < 0 || keys[i] >= keyCount || joints[i] < 0 || joints[i] >= jointCount)
                throw new IOException("Invalid model animation channel reference.");
    }

    private static Rac1AnimatedAsset readBlaster(byte[] section) throws IOException {
        if (section.length < 4) {
            throw new IOException("The weapon of the cache file is damaged.");
        }
        byte[] asset = new byte[section.length - 4];
        System.arraycopy(section, 4, asset, 0, asset.length);
        return readAnimatedAsset(asset);
    }

    private static Rac1AnimatedAsset readAnimated(byte[] section) throws IOException {
        return readAnimatedAsset(section);
    }

    private static Rac1AnimatedAsset readAnimatedAsset(byte[] section) throws IOException {
        Rac1Bytes.Reader in = new Rac1Bytes.Reader(section);
        int classNumber = in.getInt();
        int assetNameLength = in.getInt();
        if (assetNameLength < 0 || assetNameLength > 1024) {
            throw new IOException("The name of an asset of the cache file is implausible.");
        }
        byte[] assetName = new byte[assetNameLength];
        in.getBytes(assetName);
        int vertexCount = in.getInt();
        int triangleCount = in.getInt();
        int jointCount = in.getInt();
        int sequenceCount = in.getInt();
        int textureCount = in.getInt();
        if (vertexCount < 0 || triangleCount < 0 || jointCount < 0 || sequenceCount < 0
                || textureCount < 0 || vertexCount > 1 << 20 || triangleCount > 1 << 20
                || jointCount > 4096 || sequenceCount > 4096 || textureCount > 64) {
            throw new IOException("The animated asset of the cache file has implausible sizes.");
        }
        float[] positions = getFloats(in, vertexCount * 3);
        float[] normals = getFloats(in, vertexCount * 3);
        float[] uvs = getFloats(in, vertexCount * 2);
        int[] indices = getInts(in, triangleCount * 3);
        int[] materials = getInts(in, triangleCount);
        int[] joints = getInts(in, vertexCount * 3);
        float[] weights = getFloats(in, vertexCount * 3);
        float[] inverseBind = getFloats(in, jointCount * 16);
        float[] restTranslations = getFloats(in, jointCount * 3);
        int[] parents = getInts(in, jointCount);
        int chainCount = in.getInt();
        if (chainCount < 0 || chainCount > 1024) {
            throw new IOException("Invalid attachment list count.");
        }
        int[][] chains = new int[chainCount][];
        for (int list = 0; list < chainCount; list++) {
            int count = in.getInt();
            if (count < 0 || count > 256) {
                throw new IOException("Invalid attachment chain length.");
            }
            chains[list] = getInts(in, count);
            for (int joint : chains[list]) {
                if (joint < 0 || joint >= jointCount) {
                    throw new IOException("Invalid attachment joint.");
                }
            }
        }
        Rac1Animation[] animations = new Rac1Animation[sequenceCount];
        for (int index = 0; index < sequenceCount; index++) {
            int nameLength = in.getInt();
            if (nameLength < 0 || nameLength > 1024) {
                throw new IOException("The name of an animation of the cache file is implausible.");
            }
            byte[] name = new byte[nameLength];
            in.getBytes(name);
            int keyCount = in.getInt();
            boolean loop = in.getBoolean();
            if (keyCount < 0 || keyCount > 1 << 16) {
                throw new IOException("An animation of the cache file has an implausible number of "
                        + "keys.");
            }
            float[] times = getFloats(in, keyCount);
            if ((long) keyCount * jointCount * 4 > in.remaining()) {
                throw new IOException("An animation of the cache file has too many keys.");
            }
            float[] rotations = getFloats(in, keyCount * jointCount * 4);
            int translationCount = in.getInt();
            if (translationCount < 0 || translationCount > 1 << 20
                    || (long) translationCount * 12 > in.remaining()) {
                throw new IOException("An animation of the cache file has an implausible number of "
                        + "translations.");
            }
            int[] translationKeys = new int[translationCount];
            int[] translationJoints = new int[translationCount];
            float[] translationValues = new float[translationCount * 3];
            for (int record = 0; record < translationCount; record++) {
                translationKeys[record] = in.getInt();
                translationJoints[record] = in.getInt();
                translationValues[record * 3] = in.getFloat();
                translationValues[record * 3 + 1] = in.getFloat();
                translationValues[record * 3 + 2] = in.getFloat();
            }
            int scaleCount = in.getInt();
            if (scaleCount < 0 || scaleCount > 1 << 20
                    || (long) scaleCount * 13 > in.remaining()) {
                throw new IOException("An animation of the cache file has an implausible number of "
                        + "scales.");
            }
            int[] scaleKeys = new int[scaleCount];
            int[] scaleJoints = new int[scaleCount];
            float[] scaleValues = new float[scaleCount * 3];
            boolean[] scaleInherited = new boolean[scaleCount];
            for (int record = 0; record < scaleCount; record++) {
                scaleKeys[record] = in.getInt();
                scaleJoints[record] = in.getInt();
                scaleValues[record * 3] = in.getFloat();
                scaleValues[record * 3 + 1] = in.getFloat();
                scaleValues[record * 3 + 2] = in.getFloat();
                scaleInherited[record] = in.getBoolean();
            }
            int sequenceRateBits = in.getInt();
            int sourceCount = in.getInt();
            if (sourceCount < 0 || sourceCount > 255) {
                throw new IOException("Invalid original key count.");
            }
            int[] headerTimes16 = getInts(in, sourceCount);
            int[] keyRateBits = getInts(in, sourceCount);
            float[] sourceSeconds = getFloats(in, sourceCount);
            int mappingCount = in.getInt();
            if (mappingCount < 0 || mappingCount > 256
                    || (sourceCount > 0 && mappingCount != keyCount)
                    || (sourceCount == 0 && mappingCount != 0)) {
                throw new IOException("Invalid original/sampled key mapping count.");
            }
            int[] sampledSourceKeys = getInts(in, mappingCount);
            Rac1SourceTiming sourceTiming;
            try {
                sourceTiming = new Rac1SourceTiming(sequenceRateBits, headerTimes16,
                        keyRateBits, sourceSeconds, sampledSourceKeys);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid original timing metadata.", invalid);
            }
            animations[index] = new Rac1Animation(new String(name, StandardCharsets.UTF_8), times,
                    loop, rotations, translationKeys, translationJoints, translationValues,
                    scaleKeys, scaleJoints, scaleValues, scaleInherited, sourceTiming);
        }
        Rac1Texture[] textures = getTextures(in, textureCount);
        if (in.remaining() != 0) throw new IOException("Trailing animated model data.");
        Rac1SkinnedMesh mesh = new Rac1SkinnedMesh(positions, normals, uvs, indices, materials,
                joints, weights);
        Rac1Skeleton skeleton = new Rac1Skeleton(inverseBind, parents, restTranslations);
        return new Rac1AnimatedAsset(classNumber, new String(assetName, StandardCharsets.UTF_8), mesh,
                skeleton, animations, textures, chains);
    }

    private static void putTextures(Rac1Bytes.Writer out, Rac1Texture[] textures) {
        for (Rac1Texture texture : textures) {
            out.putInt(texture.width());
            out.putInt(texture.height());
            out.putBytes(texture.pixels());
        }
    }

    private static Rac1Texture[] getTextures(Rac1Bytes.Reader in, int count) throws IOException {
        Rac1Texture[] textures = new Rac1Texture[count];
        for (int index = 0; index < count; index++) {
            int width = in.getInt();
            int height = in.getInt();
            if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                throw new IOException("A texture of the cache file has an implausible size.");
            }
            if ((long) width * height * 4 > in.remaining()) {
                throw new IOException("A texture of the cache file does not fit into the file.");
            }
            byte[] pixels = new byte[width * height * 4];
            in.getBytes(pixels);
            textures[index] = new Rac1Texture(width, height, pixels);
        }
        return textures;
    }

    private static void putFloats(Rac1Bytes.Writer out, float[] values) {
        for (float value : values) {
            out.putFloat(value);
        }
    }

    private static void putInts(Rac1Bytes.Writer out, int[] values) {
        for (int value : values) {
            out.putInt(value);
        }
    }

    private static float[] getFloats(Rac1Bytes.Reader in, int count) throws IOException {
        if (count < 0 || (long) count * 4 > in.remaining()) {
            throw new IOException("The cache file does not hold the numbers that were read.");
        }
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = in.getFloat();
        }
        return values;
    }

    private static int[] getInts(Rac1Bytes.Reader in, int count) throws IOException {
        if (count < 0 || (long) count * 4 > in.remaining()) {
            throw new IOException("The cache file does not hold the numbers that were read.");
        }
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = in.getInt();
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
