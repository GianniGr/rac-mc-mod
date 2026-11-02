package com.github.giannigr.racmc.iso;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A minimal reader for the ISO 9660 file system of a PlayStation 2 disc image.
 *
 * <p>Only the part of ISO 9660 that a Ratchet &amp; Clank disc needs is implemented: the primary
 * volume descriptor, the root directory record it carries and the directory records below it.
 * Everything is read with bounded, seeked reads, so an image of any size is never loaded into
 * memory as a whole.
 *
 * <p>The reader is deliberately strict. A record that does not follow the format raises an
 * {@link IsoFormatException} instead of being guessed at, because a damaged record would make the
 * rest of the image untrustworthy.
 */
final class Iso9660Tree {

    /** The size of a logical block of a PlayStation 2 disc, in bytes. */
    static final int SECTOR_SIZE = 2048;

    /** The sector the primary volume descriptor of an ISO 9660 image starts at. */
    static final int PRIMARY_VOLUME_DESCRIPTOR_SECTOR = 16;

    /** The offset of the logical block size inside the primary volume descriptor. */
    private static final int PVD_LOGICAL_BLOCK_SIZE_OFFSET = 128;

    /** The offset of the root directory record inside the primary volume descriptor. */
    private static final int PVD_ROOT_DIRECTORY_OFFSET = 156;

    /** The offset of the volume identifier inside the primary volume descriptor. */
    private static final int PVD_VOLUME_IDENTIFIER_OFFSET = 40;

    /** The length of the volume identifier inside the primary volume descriptor. */
    private static final int PVD_VOLUME_IDENTIFIER_LENGTH = 32;

    /** The largest directory that is read into memory, in bytes. */
    private static final int MAX_DIRECTORY_BYTES = 1 << 20;

    /** The largest number of records that is read from a single directory. */
    private static final int MAX_RECORDS_PER_DIRECTORY = 1000;

    /** The deepest directory nesting that is followed. */
    private static final int MAX_DIRECTORY_DEPTH = 8;

    /** The fixed part of a directory record, in bytes. */
    private static final int DIRECTORY_RECORD_HEADER = 33;

    /** The flag of a directory record that marks a directory. */
    private static final int FLAG_DIRECTORY = 0x02;

    /** The flag of a directory record that marks a file split over several extents. */
    private static final int FLAG_MULTI_EXTENT = 0x80;

    private final String volumeIdentifier;
    private final Map<String, Iso9660File> files;

    private Iso9660Tree(String volumeIdentifier, Map<String, Iso9660File> files) {
        this.volumeIdentifier = volumeIdentifier;
        this.files = Collections.unmodifiableMap(files);
    }

    /**
     * Reads the file system of an image.
     *
     * @param image the image to read, positioned anywhere
     * @param imageSize the size of the image in bytes
     * @return the parsed file system
     * @throws IsoFormatException when the image does not follow the expected structure
     * @throws IOException when the image cannot be read
     */
    static Iso9660Tree parse(RandomAccessFile image, long imageSize) throws IsoFormatException, IOException {
        byte[] descriptor = new byte[SECTOR_SIZE];
        long descriptorOffset = (long) PRIMARY_VOLUME_DESCRIPTOR_SECTOR * SECTOR_SIZE;
        if (imageSize < descriptorOffset + SECTOR_SIZE) {
            throw new IsoFormatException(IsoValidationStatus.NOT_AN_ISO,
                    "The file is too small to be an ISO 9660 image.");
        }
        readFully(image, descriptorOffset, descriptor, imageSize);

        if (descriptor[0] != 0x01) {
            throw new IsoFormatException(IsoValidationStatus.NOT_AN_ISO,
                    "The file does not start with an ISO 9660 primary volume descriptor.");
        }
        String standardIdentifier = new String(descriptor, 1, 5, StandardCharsets.ISO_8859_1);
        if (!"CD001".equals(standardIdentifier)) {
            throw new IsoFormatException(IsoValidationStatus.NOT_AN_ISO,
                    "The file does not start with an ISO 9660 primary volume descriptor.");
        }
        if (descriptor[6] != 0x01) {
            throw new IsoFormatException(IsoValidationStatus.NOT_AN_ISO,
                    "Only ISO 9660 images of version 1 are supported.");
        }
        int blockSize = readUInt16LittleEndian(descriptor, PVD_LOGICAL_BLOCK_SIZE_OFFSET);
        if (blockSize != SECTOR_SIZE) {
            throw new IsoFormatException(IsoValidationStatus.NOT_AN_ISO,
                    "Only ISO 9660 images with 2048 byte sectors are supported.");
        }

        String volumeIdentifier = new String(descriptor, PVD_VOLUME_IDENTIFIER_OFFSET,
                PVD_VOLUME_IDENTIFIER_LENGTH, StandardCharsets.ISO_8859_1).trim();

        byte[] rootRecord = new byte[DIRECTORY_RECORD_HEADER];
        System.arraycopy(descriptor, PVD_ROOT_DIRECTORY_OFFSET, rootRecord, 0, DIRECTORY_RECORD_HEADER);

        Map<String, Iso9660File> files = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        readDirectory(image, imageSize, rootRecord, "", 0, files);
        return new Iso9660Tree(volumeIdentifier, files);
    }

    /**
     * The volume identifier of the image, trimmed of its padding.
     *
     * @return the volume identifier, for example {@code RATCHETANDCLANK}
     */
    String volumeIdentifier() {
        return volumeIdentifier;
    }

    /**
     * Finds a file by its path inside the image.
     *
     * @param path the path of the file, for example {@code SYSTEM.CNF}
     * @return the file, or an empty optional when the image does not contain it
     */
    Optional<Iso9660File> find(String path) {
        return Optional.ofNullable(files.get(path));
    }

    /**
     * The paths of all files of the image, in the order they were read.
     *
     * @return the file paths
     */
    Map<String, Iso9660File> files() {
        return files;
    }

    private static void readDirectory(RandomAccessFile image,
                                      long imageSize,
                                      byte[] record,
                                      String parentPath,
                                      int depth,
                                      Map<String, Iso9660File> files) throws IsoFormatException, IOException {
        if (depth > MAX_DIRECTORY_DEPTH) {
            throw new IsoFormatException("The image nests its directories deeper than "
                    + MAX_DIRECTORY_DEPTH + " levels.");
        }
        long extentLba = readUInt32LittleEndian(record, 2);
        long dataLength = readUInt32LittleEndian(record, 10);
        int flags = record[25] & 0xFF;
        if ((flags & FLAG_MULTI_EXTENT) != 0) {
            throw new IsoFormatException("The image contains a file that is split over several extents.");
        }
        if (dataLength <= 0 || dataLength > MAX_DIRECTORY_BYTES) {
            throw new IsoFormatException("The image declares a directory of " + dataLength
                    + " bytes, which is outside the supported range.");
        }
        long offset = extentLba * SECTOR_SIZE;
        if (offset < 0 || offset > imageSize - dataLength) {
            throw new IsoFormatException("A directory of the image points outside of the image.");
        }

        byte[] data = new byte[(int) dataLength];
        readFully(image, offset, data, imageSize);

        int position = 0;
        int records = 0;
        while (position < data.length) {
            int recordLength = data[position] & 0xFF;
            if (recordLength == 0) {
                // The rest of the sector is padding, the next record starts in the next sector.
                int nextSector = position / SECTOR_SIZE + 1;
                position = nextSector * SECTOR_SIZE;
                continue;
            }
            if (recordLength < DIRECTORY_RECORD_HEADER || position + recordLength > data.length) {
                throw new IsoFormatException("The image contains a malformed directory record at byte "
                        + position + " of a directory.");
            }
            if (++records > MAX_RECORDS_PER_DIRECTORY) {
                throw new IsoFormatException("The image declares more than " + MAX_RECORDS_PER_DIRECTORY
                        + " records in a single directory.");
            }

            int identifierLength = data[position + 32] & 0xFF;
            if (identifierLength < 1 || identifierLength > recordLength - DIRECTORY_RECORD_HEADER) {
                throw new IsoFormatException("The image contains a directory record with an invalid name length.");
            }
            int identifierOffset = position + DIRECTORY_RECORD_HEADER;
            byte[] identifier = new byte[identifierLength];
            System.arraycopy(data, identifierOffset, identifier, 0, identifierLength);

            boolean self = identifierLength == 1 && identifier[0] == 0x00;
            boolean parent = identifierLength == 1 && identifier[0] == 0x01;
            if (!self && !parent) {
                String name = readName(identifier);
                String path = parentPath.isEmpty() ? name : parentPath + "/" + name;
                int recordFlags = data[position + 25] & 0xFF;
                if ((recordFlags & FLAG_MULTI_EXTENT) != 0) {
                    throw new IsoFormatException("The image contains a file that is split over several extents.");
                }
                if ((recordFlags & FLAG_DIRECTORY) != 0) {
                    byte[] childRecord = new byte[DIRECTORY_RECORD_HEADER];
                    System.arraycopy(data, position, childRecord, 0, DIRECTORY_RECORD_HEADER);
                    readDirectory(image, imageSize, childRecord, path, depth + 1, files);
                } else {
                    long childLba = readUInt32LittleEndian(data, position + 2);
                    long childSize = readUInt32LittleEndian(data, position + 10);
                    files.put(path, new Iso9660File(path, childLba, childSize));
                }
            }
            position += recordLength;
        }
    }

    /**
     * Reads the name of a directory record and checks that it is safe to use as a path segment.
     *
     * @param identifier the raw identifier of the record
     * @return the name without its version suffix
     */
    private static String readName(byte[] identifier) throws IsoFormatException {
        String raw = new String(identifier, StandardCharsets.ISO_8859_1);
        int version = raw.indexOf(';');
        if (version >= 0) {
            raw = raw.substring(0, version);
        }
        if (raw.isEmpty()) {
            throw new IsoFormatException("The image contains a directory record with an empty name.");
        }
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character < 0x20 || character == '/' || character == '\\' || character == ':') {
                throw new IsoFormatException(
                        "The image contains a directory record with an unusable name.");
            }
        }
        if (".".equals(raw) || "..".equals(raw)) {
            throw new IsoFormatException("The image contains a directory record with an unusable name.");
        }
        return raw;
    }

    /**
     * Reads bytes at an offset and fails when the image ends early.
     *
     * @param image the image to read
     * @param offset the offset to read at
     * @param target the buffer to fill
     * @param imageSize the size of the image in bytes
     * @throws IOException when the image cannot be read
     * @throws IsoFormatException when the range lies outside of the image
     */
    static void readFully(RandomAccessFile image, long offset, byte[] target, long imageSize)
            throws IOException, IsoFormatException {
        if (offset < 0 || offset > imageSize - target.length) {
            throw new IsoFormatException("A part of the image points outside of the image.");
        }
        image.seek(offset);
        image.readFully(target);
    }

    /**
     * Reads a little endian 16 bit number.
     *
     * @param data the buffer to read from
     * @param offset the offset to read at
     * @return the value
     */
    static int readUInt16LittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Reads a little endian 32 bit number as an unsigned value.
     *
     * @param data the buffer to read from
     * @param offset the offset to read at
     * @return the value
     */
    static long readUInt32LittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    /**
     * Whether the given volume identifier belongs to a Ratchet &amp; Clank disc.
     *
     * @param identifier the volume identifier of an image
     * @return {@code true} when the identifier names a Ratchet &amp; Clank disc
     */
    static boolean isRac1Volume(String identifier) {
        return identifier != null
                && Rac1DiscCatalog.RAC1_VOLUME_IDENTIFIER.equals(identifier.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * The files of the image, keyed by their path.
     *
     * @return an unmodifiable map of the files
     */
    Map<String, Iso9660File> orderedFiles() {
        return new LinkedHashMap<>(files);
    }
}
