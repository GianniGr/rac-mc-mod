package com.github.giannigr.racmc.disc;

import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;

/**
 * The header of a level of the game.
 *
 * <p>Every level of the disc has one header. It is 0x2434 bytes long, starts with the number of
 * the level and its size, and names the sectors that hold the data of the level: the container of
 * the level data, the gameplay file and the occlusion data.
 *
 * <p>The layout was measured by the ReRAC project (ISC); see
 * {@code docs/formats/disc_layout.md} of that project.
 */
public final class Rac1LevelHeader {

    /** The size of the header of a level in bytes. */
    public static final int HEADER_SIZE = 0x2434;

    /** The offset of the container of the level data inside the header. */
    private static final int DATA_OFFSET = 0x08;

    private final int id;
    private final long dataSector;
    private final int dataSectors;

    private Rac1LevelHeader(int id, long dataSector, int dataSectors) {
        this.id = id;
        this.dataSector = dataSector;
        this.dataSectors = dataSectors;
    }

    /**
     * Reads the header of a level.
     *
     * @param image the image to read
     * @param sector the sector the header starts at
     * @return the header
     * @throws IOException when the image cannot be read
     * @throws DiscFormatException when the header does not follow the expected layout
     */
    public static Rac1LevelHeader read(Rac1DiscImage image, long sector)
            throws IOException, DiscFormatException {
        byte[] header = image.readSectors(sector, 5);
        return of(header);
    }

    /**
     * Reads the header of a level out of the bytes that were read from it.
     *
     * @param header the bytes of the header
     * @return the header
     * @throws DiscFormatException when the header does not follow the expected layout
     */
    public static Rac1LevelHeader of(byte[] header) throws DiscFormatException {
        if (header.length < HEADER_SIZE) {
            throw new DiscFormatException("The header of the level is only " + header.length
                    + " bytes long.");
        }
        int size = DiscBytes.i32(header, 0x04);
        if (size != HEADER_SIZE) {
            throw new DiscFormatException("The header of the level declares " + size
                    + " bytes instead of " + HEADER_SIZE + ".");
        }
        int id = DiscBytes.i32(header, 0x00);
        int dataSector = DiscBytes.i32(header, DATA_OFFSET);
        int dataSectors = DiscBytes.i32(header, DATA_OFFSET + 4);
        if (dataSector <= 0 || dataSectors <= 0) {
            throw new DiscFormatException("The header of the level " + id
                    + " does not name the sectors of its data.");
        }
        return new Rac1LevelHeader(id, dataSector, dataSectors);
    }

    /**
     * The number of the level.
     *
     * @return the number
     */
    public int id() {
        return id;
    }

    /**
     * The sector the container of the level data starts at.
     *
     * @return the sector
     */
    public long dataSector() {
        return dataSector;
    }

    /**
     * The number of sectors of the container of the level data.
     *
     * @return the number of sectors
     */
    public int dataSectors() {
        return dataSectors;
    }
}
