package com.github.giannigr.racmc.disc;

/**
 * An entry of the table of the gadgets of a level.
 *
 * <p>The gadgets of the game are the weapons and the tools the hero carries. They are not part of
 * the core data of the level like the other classes: every gadget is a compressed stream of its
 * own in the core data, and the table of the gadgets names the stream of every gadget together
 * with the number of its class.
 *
 * <p>The weapons of the hero are not part of the class table of the level: the class table has an
 * entry for them, but without any geometry. The entry only names their textures.
 */
public final class Rac1GadgetEntry {

    /** The size of an entry in bytes. */
    public static final int SIZE = 0x10;

    private final int offset;
    private final int classNumber;
    private final int compressedSize;

    /**
     * Creates an entry.
     *
     * @param offset the offset of the compressed class in the core data of the level
     * @param classNumber the number of the class of the gadget
     * @param compressedSize the number of bytes of the compressed class
     */
    Rac1GadgetEntry(int offset, int classNumber, int compressedSize) {
        this.offset = offset;
        this.classNumber = classNumber;
        this.compressedSize = compressedSize;
    }

    /**
     * The offset of the compressed class in the core data of the level.
     *
     * @return the offset in bytes
     */
    public int offset() {
        return offset;
    }

    /**
     * The number of the class of the gadget.
     *
     * @return the number
     */
    public int classNumber() {
        return classNumber;
    }

    /**
     * The number of bytes of the compressed class.
     *
     * @return the number of bytes
     */
    public int compressedSize() {
        return compressedSize;
    }
}
