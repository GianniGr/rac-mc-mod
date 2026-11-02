package com.github.giannigr.racmc.iso;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds synthetic ISO 9660 images for the tests.
 *
 * <p>The images are built from scratch, so that no game data is needed to test the validator. The
 * builder can also produce deliberately broken images: a wrong descriptor, a wrong logical block
 * size, a malformed directory record, a directory that points outside of the image or a file name
 * that could be used for a path traversal.
 */
final class SyntheticIso {

    /** The size of a logical block, in bytes. */
    static final int SECTOR = 2048;

    /** The sector the primary volume descriptor is placed at. */
    static final int PRIMARY_VOLUME_DESCRIPTOR_SECTOR = 16;

    /** The sector the root directory is placed at. */
    static final int ROOT_DIRECTORY_SECTOR = 17;

    private String volumeIdentifier = "RATCHETANDCLANK";
    private String standardIdentifier = "CD001";
    private int volumeDescriptorType = 1;
    private int volumeDescriptorVersion = 1;
    private int logicalBlockSize = SECTOR;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private byte[] rootDirectoryOverride;
    private byte[] rootRecordOverride;

    private SyntheticIso() {
    }

    /**
     * Creates a builder for a synthetic image.
     *
     * @return the builder
     */
    static SyntheticIso create() {
        return new SyntheticIso();
    }

    SyntheticIso volumeIdentifier(String identifier) {
        this.volumeIdentifier = identifier;
        return this;
    }

    SyntheticIso standardIdentifier(String identifier) {
        this.standardIdentifier = identifier;
        return this;
    }

    SyntheticIso volumeDescriptorType(int type) {
        this.volumeDescriptorType = type;
        return this;
    }

    SyntheticIso volumeDescriptorVersion(int version) {
        this.volumeDescriptorVersion = version;
        return this;
    }

    SyntheticIso logicalBlockSize(int blockSize) {
        this.logicalBlockSize = blockSize;
        return this;
    }

    /**
     * Adds a file to the root directory of the image.
     *
     * @param name the name of the file
     * @param content the content of the file
     * @return the builder
     */
    SyntheticIso file(String name, byte[] content) {
        files.put(name, content);
        return this;
    }

    /**
     * Adds a file to the root directory of the image.
     *
     * @param name the name of the file
     * @param content the content of the file, as text
     * @return the builder
     */
    SyntheticIso file(String name, String content) {
        return file(name, content.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Replaces the content of the root directory with raw bytes.
     *
     * @param directory the raw content of the root directory
     * @return the builder
     */
    SyntheticIso rawRootDirectory(byte[] directory) {
        this.rootDirectoryOverride = directory;
        return this;
    }

    /**
     * Replaces the root directory record of the primary volume descriptor with raw bytes.
     *
     * @param record the raw root directory record
     * @return the builder
     */
    SyntheticIso rawRootRecord(byte[] record) {
        this.rootRecordOverride = record;
        return this;
    }

    /**
     * Builds the image.
     *
     * @return the bytes of the image
     */
    byte[] build() {
        Map<String, int[]> placements = new LinkedHashMap<>();
        int lba = ROOT_DIRECTORY_SECTOR + 1;

        byte[] directory = rootDirectoryOverride != null ? rootDirectoryOverride : buildRootDirectory(placements);
        int directorySectors = Math.max(1, (directory.length + SECTOR - 1) / SECTOR);
        lba = ROOT_DIRECTORY_SECTOR + directorySectors;

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            int sectors = Math.max(1, (entry.getValue().length + SECTOR - 1) / SECTOR);
            placements.put(entry.getKey(), new int[] {lba, entry.getValue().length});
            lba += sectors;
        }

        directory = rootDirectoryOverride != null ? directory : buildRootDirectory(placements);
        int totalSectors = lba;
        byte[] image = new byte[totalSectors * SECTOR];

        System.arraycopy(directory, 0, image, ROOT_DIRECTORY_SECTOR * SECTOR, directory.length);

        int sector = ROOT_DIRECTORY_SECTOR + directorySectors;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            byte[] content = entry.getValue();
            System.arraycopy(content, 0, image, sector * SECTOR, content.length);
            sector += Math.max(1, (content.length + SECTOR - 1) / SECTOR);
        }

        byte[] descriptor = buildPrimaryVolumeDescriptor(directory.length);
        System.arraycopy(descriptor, 0, image, PRIMARY_VOLUME_DESCRIPTOR_SECTOR * SECTOR, descriptor.length);
        return image;
    }

    private byte[] buildRootDirectory(Map<String, int[]> placements) {
        List<byte[]> identifiers = new ArrayList<>();
        identifiers.add(new byte[] {0x00});
        identifiers.add(new byte[] {0x01});
        for (String name : files.keySet()) {
            identifiers.add((name + ";1").getBytes(StandardCharsets.ISO_8859_1));
        }

        int size = 0;
        for (byte[] identifier : identifiers) {
            size += recordLength(identifier);
        }

        byte[] directory = new byte[size];
        int position = 0;
        position = writeRecord(directory, position, ROOT_DIRECTORY_SECTOR, size, 0x02, identifiers.get(0));
        position = writeRecord(directory, position, ROOT_DIRECTORY_SECTOR, size, 0x02, identifiers.get(1));
        int index = 2;
        for (Map.Entry<String, int[]> entry : placements.entrySet()) {
            int[] placement = entry.getValue();
            position = writeRecord(directory, position, placement[0], placement[1], 0x00, identifiers.get(index));
            index++;
        }
        return directory;
    }

    private byte[] buildPrimaryVolumeDescriptor(int directorySize) {
        byte[] descriptor = new byte[SECTOR];
        descriptor[0] = (byte) volumeDescriptorType;
        byte[] identifier = standardIdentifier.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(identifier, 0, descriptor, 1, Math.min(5, identifier.length));
        descriptor[6] = (byte) volumeDescriptorVersion;

        byte[] volume = volumeIdentifier.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(volume, 0, descriptor, 40, Math.min(32, volume.length));

        writeUInt16LittleEndian(descriptor, 128, logicalBlockSize);

        byte[] rootRecord = rootRecordOverride != null
                ? rootRecordOverride
                : record(ROOT_DIRECTORY_SECTOR, directorySize, 0x02, new byte[] {0x00});
        System.arraycopy(rootRecord, 0, descriptor, 156, rootRecord.length);
        return descriptor;
    }

    /**
     * Builds a single directory record.
     *
     * @param lba the extent of the record
     * @param size the size of the record in bytes
     * @param flags the flags of the record
     * @param identifier the identifier of the record
     * @return the record
     */
    static byte[] record(int lba, int size, int flags, byte[] identifier) {
        byte[] buffer = new byte[recordLength(identifier)];
        buffer[0] = (byte) buffer.length;
        writeUInt32LittleEndian(buffer, 2, lba);
        writeUInt32LittleEndian(buffer, 10, size);
        buffer[25] = (byte) flags;
        buffer[32] = (byte) identifier.length;
        System.arraycopy(identifier, 0, buffer, 33, identifier.length);
        return buffer;
    }

    private static int recordLength(byte[] identifier) {
        int length = 33 + identifier.length;
        return length % 2 == 0 ? length : length + 1;
    }

    private static int writeRecord(byte[] directory, int position, int lba, int size, int flags,
                                   byte[] identifier) {
        byte[] record = record(lba, size, flags, identifier);
        System.arraycopy(record, 0, directory, position, record.length);
        return position + record.length;
    }

    private static void writeUInt16LittleEndian(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeUInt32LittleEndian(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /**
     * Builds the content of a {@code SYSTEM.CNF} file.
     *
     * @param bootExecutable the name of the boot executable, or {@code null} to leave it out
     * @param version the revision of the disc, or {@code null} to leave it out
     * @param videoMode the video mode of the disc, or {@code null} to leave it out
     * @return the content of the file
     */
    static byte[] systemCnf(String bootExecutable, String version, String videoMode) {
        StringBuilder builder = new StringBuilder();
        if (bootExecutable != null) {
            builder.append("BOOT2 = cdrom0:\\").append(bootExecutable).append(";1\r\n");
        }
        if (version != null) {
            builder.append("VER = ").append(version).append("\r\n");
        }
        if (videoMode != null) {
            builder.append("VMODE = ").append(videoMode).append("\r\n");
        }
        builder.append("\r\n");
        return builder.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Builds the content of a file that starts with the ELF magic.
     *
     * @param size the size of the file
     * @return the content of the file
     */
    static byte[] elf(int size) {
        byte[] content = new byte[size];
        content[0] = 0x7F;
        content[1] = 'E';
        content[2] = 'L';
        content[3] = 'F';
        for (int index = 4; index < size; index++) {
            content[index] = (byte) index;
        }
        return content;
    }
}
