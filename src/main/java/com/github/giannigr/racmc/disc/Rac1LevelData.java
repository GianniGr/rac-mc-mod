package com.github.giannigr.racmc.disc;

import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;

/**
 * The container of the data of a level.
 *
 * <p>The data of a level is one uncompressed container. Its header is 0x58 bytes long and holds
 * byte ranges that are relative to the start of the container: the code of the level, its sound
 * bank, its core index, the image of the memory of the graphics chip and the compressed core data
 * that holds the geometry, the classes and the pixels of the level.
 *
 * <p>The layout was measured by the ReRAC project (ISC); see
 * {@code docs/formats/wad_layouts_rac1.md} of that project.
 */
public final class Rac1LevelData {

    /** The size of the header of the container in bytes. */
    public static final int HEADER_SIZE = 0x58;

    /** The offset of the core index inside the header. */
    private static final int CORE_INDEX_OFFSET = 0x10;

    /** The offset of the image of the graphics memory inside the header. */
    private static final int GS_RAM_OFFSET = 0x18;

    /** The offset of the compressed core data inside the header. */
    private static final int CORE_DATA_OFFSET = 0x50;

    private final long base;
    private final int coreIndexOffset;
    private final int coreIndexSize;
    private final int gsRamOffset;
    private final int gsRamSize;
    private final int coreDataOffset;
    private final int coreDataSize;

    private Rac1LevelData(long base, int coreIndexOffset, int coreIndexSize, int gsRamOffset,
                          int gsRamSize, int coreDataOffset, int coreDataSize) {
        this.base = base;
        this.coreIndexOffset = coreIndexOffset;
        this.coreIndexSize = coreIndexSize;
        this.gsRamOffset = gsRamOffset;
        this.gsRamSize = gsRamSize;
        this.coreDataOffset = coreDataOffset;
        this.coreDataSize = coreDataSize;
    }

    /**
     * Reads the header of the container of a level.
     *
     * @param image the image to read
     * @param header the header of the level
     * @return the container
     * @throws IOException when the image cannot be read
     * @throws DiscFormatException when the container does not follow the expected layout
     */
    public static Rac1LevelData read(Rac1DiscImage image, Rac1LevelHeader header)
            throws IOException, DiscFormatException {
        long base = header.dataSector() * (long) Rac1DiscTables.SECTOR_SIZE;
        byte[] bytes = image.readBytes(base, HEADER_SIZE);
        return of(base, bytes);
    }

    /**
     * Reads the header of the container of a level out of the bytes that were read from it.
     *
     * @param base the offset of the container in the image
     * @param bytes the bytes of the header of the container
     * @return the container
     * @throws DiscFormatException when the container does not follow the expected layout
     */
    public static Rac1LevelData of(long base, byte[] bytes) throws DiscFormatException {
        if (bytes.length < HEADER_SIZE) {
            throw new DiscFormatException("The header of the level data is only " + bytes.length
                    + " bytes long.");
        }
        int coreIndexOffset = DiscBytes.i32(bytes, CORE_INDEX_OFFSET);
        int coreIndexSize = DiscBytes.i32(bytes, CORE_INDEX_OFFSET + 4);
        int gsRamOffset = DiscBytes.i32(bytes, GS_RAM_OFFSET);
        int gsRamSize = DiscBytes.i32(bytes, GS_RAM_OFFSET + 4);
        int coreDataOffset = DiscBytes.i32(bytes, CORE_DATA_OFFSET);
        int coreDataSize = DiscBytes.i32(bytes, CORE_DATA_OFFSET + 4);
        if (coreIndexOffset < 0 || coreIndexSize <= 0 || coreDataOffset < 0 || coreDataSize <= 0) {
            throw new DiscFormatException("The header of the level data does not name its core "
                    + "index or its core data.");
        }
        return new Rac1LevelData(base, coreIndexOffset, coreIndexSize, gsRamOffset, gsRamSize,
                coreDataOffset, coreDataSize);
    }

    /**
     * The offset of the container in the image.
     *
     * @return the offset in bytes
     */
    public long base() {
        return base;
    }

    /**
     * The offset of the core index in the container.
     *
     * @return the offset in bytes
     */
    public int coreIndexOffset() {
        return coreIndexOffset;
    }

    /**
     * The size of the core index.
     *
     * @return the size in bytes
     */
    public int coreIndexSize() {
        return coreIndexSize;
    }

    /**
     * The offset of the image of the graphics memory in the container.
     *
     * @return the offset in bytes
     */
    public int gsRamOffset() {
        return gsRamOffset;
    }

    /**
     * The size of the image of the graphics memory.
     *
     * @return the size in bytes
     */
    public int gsRamSize() {
        return gsRamSize;
    }

    /**
     * The offset of the compressed core data in the container.
     *
     * @return the offset in bytes
     */
    public int coreDataOffset() {
        return coreDataOffset;
    }

    /**
     * The size of the compressed core data.
     *
     * @return the size in bytes
     */
    public int coreDataSize() {
        return coreDataSize;
    }
}
