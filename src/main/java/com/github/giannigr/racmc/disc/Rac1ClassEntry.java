package com.github.giannigr.racmc.disc;

/**
 * An entry of the class table of a level.
 *
 * <p>The table of the moby classes and the table of the tie classes of a level have the same
 * layout: the offset of the class blob in the core data of the level, the number of the class, two
 * unknown words and sixteen bytes that name the textures of the class.
 */
public final class Rac1ClassEntry {

    /** The size of an entry in bytes. */
    public static final int SIZE = 0x20;

    private final int offset;
    private final int classNumber;
    private final byte[] textureSlots;

    /**
     * Creates an entry.
     *
     * @param offset the offset of the class blob in the core data
     * @param classNumber the number of the class
     * @param textureSlots the sixteen bytes that name the textures of the class
     */
    Rac1ClassEntry(int offset, int classNumber, byte[] textureSlots) {
        this.offset = offset;
        this.classNumber = classNumber;
        this.textureSlots = textureSlots;
    }

    /**
     * The offset of the class blob in the core data of the level.
     *
     * @return the offset in bytes, or {@code 0} when the class has no blob
     */
    public int offset() {
        return offset;
    }

    /**
     * The number of the class.
     *
     * @return the number
     */
    public int classNumber() {
        return classNumber;
    }

    /**
     * The bytes that name the textures of the class.
     *
     * <p>The list ends at the first {@code 0xFF}, so a class uses at most fifteen textures.
     *
     * @return the sixteen bytes of the list
     */
    public byte[] textureSlots() {
        return textureSlots;
    }

    /**
     * The number of textures the class uses.
     *
     * @return the number of textures
     */
    public int textureCount() {
        int count = 0;
        while (count < textureSlots.length && (textureSlots[count] & 0xFF) != 0xFF) {
            count++;
        }
        return count;
    }
}
