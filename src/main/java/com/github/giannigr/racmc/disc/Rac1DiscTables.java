package com.github.giannigr.racmc.disc;

import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The table of contents of a Ratchet &amp; Clank disc.
 *
 * <p>The file system of the disc holds little more than the boot executable, so the game reads
 * its data by absolute sector. The table of contents at sector 1500 is the map of the disc: it
 * is a flat table of sector ranges that names the level table and the other global data of the
 * game.
 *
 * <p>The table is 0x2960 bytes long and starts with two numbers: the version of the table and its
 * size. The level table is the last part of it, at offset 0x28C8, and holds one sector range per
 * level of the game.
 *
 * <p>The layout was measured by the ReRAC project (ISC) and by the OpenRAC project; see
 * {@code docs/formats/disc_layout.md} of the ReRAC project.
 */
public final class Rac1DiscTables {

    /** The sector the table of contents of the disc starts at. */
    public static final int TOC_SECTOR = 1500;

    /** The size of the table of contents in bytes. */
    public static final int TOC_SIZE = 0x2960;

    /** The offset of the level table inside the table of contents. */
    public static final int LEVEL_TABLE_OFFSET = 0x28C8;

    /** The number of levels of the game. */
    public static final int LEVEL_COUNT = 19;

    /** The size of a sector of the disc in bytes. */
    public static final int SECTOR_SIZE = 2048;

    private final byte[] table;
    private final List<Long> levelSectors;

    private Rac1DiscTables(byte[] table, List<Long> levelSectors) {
        this.table = table;
        this.levelSectors = levelSectors;
    }

    /**
     * Reads the table of contents of an image.
     *
     * @param image the image to read
     * @return the table of contents
     * @throws IOException when the image cannot be read
     * @throws DiscFormatException when the table does not follow the expected layout
     */
    public static Rac1DiscTables read(Rac1DiscImage image) throws IOException, DiscFormatException {
        byte[] table = image.readSectors(TOC_SECTOR, 6);
        return of(table);
    }

    /**
     * Reads the table of contents out of the bytes that were read from it.
     *
     * @param table the bytes of the table of contents
     * @return the table of contents
     * @throws DiscFormatException when the table does not follow the expected layout
     */
    public static Rac1DiscTables of(byte[] table) throws DiscFormatException {
        if (table.length < TOC_SIZE) {
            throw new DiscFormatException("The table of contents of the disc is only "
                    + table.length + " bytes long.");
        }
        int version = DiscBytes.i32(table, 0);
        if (version != 1) {
            throw new DiscFormatException("The table of contents of the disc has the version "
                    + version + " instead of 1.");
        }
        int size = DiscBytes.i32(table, 4);
        if (size != TOC_SIZE) {
            throw new DiscFormatException("The table of contents of the disc declares " + size
                    + " bytes instead of " + TOC_SIZE + ".");
        }
        List<Long> levels = new ArrayList<>(LEVEL_COUNT);
        for (int index = 0; index < LEVEL_COUNT; index++) {
            int offset = LEVEL_TABLE_OFFSET + index * 8;
            int sector = DiscBytes.i32(table, offset);
            if (sector < 0) {
                throw new DiscFormatException("The level table of the disc names a negative "
                        + "sector for the level " + index + ".");
            }
            levels.add(sector == 0 ? null : (long) sector);
        }
        return new Rac1DiscTables(table, levels);
    }

    /**
     * The bytes of the table of contents.
     *
     * @return the bytes
     */
    public byte[] bytes() {
        return table;
    }

    /**
     * The sector the header of a level starts at.
     *
     * @param level the number of the level
     * @return the sector, or {@code -1} when the slot of the level is empty
     */
    public long levelSector(int level) {
        if (level < 0 || level >= levelSectors.size()) {
            return -1L;
        }
        Long sector = levelSectors.get(level);
        return sector == null ? -1L : sector;
    }

    /**
     * The number of levels that the table of contents names.
     *
     * @return the number of levels
     */
    public int levelCount() {
        return levelSectors.size();
    }
}
