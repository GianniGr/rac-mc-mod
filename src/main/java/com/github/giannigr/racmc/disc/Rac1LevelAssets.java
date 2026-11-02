package com.github.giannigr.racmc.disc;

import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.wad.WadDecoder;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;

/**
 * The data of a level of the game.
 *
 * <p>The class loads everything that is needed to read the assets of one level: the core index,
 * the decompressed core data that holds the classes, the geometry and the pixels, and the image of
 * the memory of the graphics chip that holds the palettes.
 *
 * <p>Nothing of this is written to the disk. The data is read out of the disc image and stays in
 * the memory of the game.
 */
public final class Rac1LevelAssets {

    /** The number of levels of the game. */
    public static final int LEVEL_COUNT = Rac1DiscTables.LEVEL_COUNT;

    private final int level;
    private final Rac1CoreIndex coreIndex;
    private final byte[] coreData;
    private final byte[] gsRam;

    private Rac1LevelAssets(int level, Rac1CoreIndex coreIndex, byte[] coreData, byte[] gsRam) {
        this.level = level;
        this.coreIndex = coreIndex;
        this.coreData = coreData;
        this.gsRam = gsRam;
    }

    /**
     * Loads the data of a level.
     *
     * @param image the image to read
     * @param level the number of the level
     * @return the data of the level
     * @throws IOException when the image cannot be read
     * @throws DiscFormatException when the tables of the level do not follow the expected layout
     * @throws WadFormatException when the core data of the level is damaged
     */
    public static Rac1LevelAssets load(Rac1DiscImage image, int level)
            throws IOException, DiscFormatException, WadFormatException {
        if (level < 0 || level >= LEVEL_COUNT) {
            throw new DiscFormatException("The level " + level + " does not exist.");
        }
        Rac1DiscTables tables = Rac1DiscTables.read(image);
        long sector = tables.levelSector(level);
        if (sector < 0) {
            throw new DiscFormatException("The table of contents of the disc does not name a "
                    + "header for the level " + level + ".");
        }
        Rac1LevelHeader header = Rac1LevelHeader.read(image, sector);
        Rac1LevelData container = Rac1LevelData.read(image, header);

        byte[] indexBytes = image.readBytes(container.base() + container.coreIndexOffset(),
                container.coreIndexSize());
        Rac1CoreIndex coreIndex = Rac1CoreIndex.parse(indexBytes);

        byte[] compressed = image.readBytes(container.base() + container.coreDataOffset(),
                container.coreDataSize());
        byte[] coreData = WadDecoder.decode(compressed, coreIndex.decompressedSize());

        byte[] gsRam = container.gsRamSize() > 0
                ? image.readBytes(container.base() + container.gsRamOffset(), container.gsRamSize())
                : new byte[0];

        return new Rac1LevelAssets(level, coreIndex, coreData, gsRam);
    }

    /**
     * The number of the level.
     *
     * @return the number
     */
    public int level() {
        return level;
    }

    /**
     * The core index of the level.
     *
     * @return the core index
     */
    public Rac1CoreIndex coreIndex() {
        return coreIndex;
    }

    /**
     * The decompressed core data of the level.
     *
     * @return the bytes
     */
    public byte[] coreData() {
        return coreData;
    }

    /**
     * The image of the memory of the graphics chip of the level.
     *
     * @return the bytes
     */
    public byte[] gsRam() {
        return gsRam;
    }

    /**
     * The bytes of the blob of a class, cut out of the core data.
     *
     * <p>A class blob has no size of its own: it ends at the start of the next block of the core
     * data.
     *
     * @param offset the offset of the blob in the core data
     * @return the bytes of the blob
     * @throws DiscFormatException when the offset lies outside of the core data
     */
    public byte[] classBlob(int offset) throws DiscFormatException {
        if (offset <= 0 || offset >= coreData.length) {
            throw new DiscFormatException("A class blob at " + offset
                    + " lies outside of the core data of the level.");
        }
        int size = coreIndex.blockSize(offset);
        if (size <= 0 || offset + size > coreData.length) {
            throw new DiscFormatException("The class blob at " + offset
                    + " does not fit into the core data of the level.");
        }
        byte[] blob = new byte[size];
        System.arraycopy(coreData, offset, blob, 0, size);
        return blob;
    }

    /**
     * The bytes of the class of a gadget of the level.
     *
     * <p>The gadgets of the game are not part of the core data like the other classes: every
     * gadget is a compressed stream of its own. The reader cuts the stream out of the core data and
     * decodes it, so that the result is the same class blob as the one of the other classes.
     *
     * @param classNumber the number of the class of the gadget
     * @return the bytes of the class of the gadget
     * @throws DiscFormatException when the level has no such gadget or when its stream is damaged
     * @throws WadFormatException when the compressed stream of the gadget is damaged
     */
    public byte[] gadgetClass(int classNumber) throws DiscFormatException, WadFormatException {
        Rac1GadgetEntry entry = coreIndex.gadget(classNumber);
        if (entry == null) {
            throw new DiscFormatException("The level has no gadget of the class " + classNumber
                    + ".");
        }
        int offset = entry.offset();
        int size = entry.compressedSize();
        if (offset <= 0 || size <= 0 || offset > coreData.length - size) {
            throw new DiscFormatException("The compressed class of the gadget " + classNumber
                    + " lies outside of the core data of the level.");
        }
        byte[] compressed = new byte[size];
        System.arraycopy(coreData, offset, compressed, 0, size);
        return WadDecoder.decode(compressed, -1);
    }
}
