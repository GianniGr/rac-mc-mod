package com.github.giannigr.racmc.disc;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The core index of a level.
 *
 * <p>The core index is the map of the core data of a level. It names the tables of the classes and
 * of the textures, the base of the pixels and the size of the decompressed core data. Two address
 * spaces meet in it: the offsets of the tables are relative to the start of the core index, the
 * offsets of the data are relative to the start of the decompressed core data.
 *
 * <p>The core data has no table of its own: a block of it ends at the smallest start that is known
 * above its own. The known starts are the blocks of the header, the class blobs, the animation
 * sequences of the hero and the gadgets.
 *
 * <p>The layout was measured by the ReRAC project (ISC); see
 * {@code docs/formats/wad_layouts_rac1.md} of that project.
 */
public final class Rac1CoreIndex {

    /** The size of the header of the core index in bytes. */
    public static final int HEADER_SIZE = 0xBC;

    private final byte[] index;
    private final List<Rac1ClassEntry> mobyClasses;
    private final List<Rac1ClassEntry> tieClasses;
    private final List<Rac1GadgetEntry> gadgets;
    private final List<Rac1TextureEntry> mobyTextures;
    private final List<Rac1TextureEntry> tieTextures;
    private final int texturesBase;
    private final int decompressedSize;
    private final int[] ratchetSequences;
    private final TreeSet<Integer> boundaries;

    private Rac1CoreIndex(byte[] index, List<Rac1ClassEntry> mobyClasses,
                          List<Rac1ClassEntry> tieClasses, List<Rac1GadgetEntry> gadgets,
                          List<Rac1TextureEntry> mobyTextures,
                          List<Rac1TextureEntry> tieTextures, int texturesBase, int decompressedSize,
                          int[] ratchetSequences, TreeSet<Integer> boundaries) {
        this.index = index;
        this.mobyClasses = mobyClasses;
        this.tieClasses = tieClasses;
        this.gadgets = gadgets;
        this.mobyTextures = mobyTextures;
        this.tieTextures = tieTextures;
        this.texturesBase = texturesBase;
        this.decompressedSize = decompressedSize;
        this.ratchetSequences = ratchetSequences;
        this.boundaries = boundaries;
    }

    /**
     * Reads the core index out of the bytes that were read from it.
     *
     * @param index the bytes of the core index
     * @return the core index
     * @throws DiscFormatException when the core index does not follow the expected layout
     */
    public static Rac1CoreIndex parse(byte[] index) throws DiscFormatException {
        if (index.length < HEADER_SIZE) {
            throw new DiscFormatException("The core index is only " + index.length
                    + " bytes long.");
        }
        int decompressedSize = DiscBytes.i32(index, 0x8C);
        if (decompressedSize <= 0) {
            throw new DiscFormatException("The core index does not declare the size of the core "
                    + "data.");
        }

        List<Rac1ClassEntry> mobyClasses = classes(index, 0x18);
        List<Rac1ClassEntry> tieClasses = classes(index, 0x20);
        List<Rac1GadgetEntry> gadgets = gadgets(index);
        List<Rac1TextureEntry> mobyTextures = textures(index, 0x38);
        List<Rac1TextureEntry> tieTextures = textures(index, 0x40);

        int[] ratchetSequences = new int[256];
        int sequenceTable = DiscBytes.i32(index, 0x78);
        if (sequenceTable > 0) {
            if (!DiscBytes.inBounds(index, sequenceTable, 256 * 4)) {
                throw new DiscFormatException("The table of the animation sequences of the hero "
                        + "points outside of the core index.");
            }
            for (int slot = 0; slot < 256; slot++) {
                ratchetSequences[slot] = DiscBytes.i32(index, sequenceTable + slot * 4);
            }
        }

        TreeSet<Integer> boundaries = new TreeSet<>();
        add(boundaries, DiscBytes.i32(index, 0x08));
        add(boundaries, DiscBytes.i32(index, 0x0C));
        add(boundaries, DiscBytes.i32(index, 0x10));
        add(boundaries, DiscBytes.i32(index, 0x14));
        add(boundaries, DiscBytes.i32(index, 0x60));
        add(boundaries, DiscBytes.i32(index, 0x64));
        add(boundaries, DiscBytes.i32(index, 0x68));
        for (Rac1ClassEntry entry : mobyClasses) {
            add(boundaries, entry.offset());
        }
        for (Rac1ClassEntry entry : tieClasses) {
            add(boundaries, entry.offset());
        }
        for (int offset : ratchetSequences) {
            add(boundaries, offset);
        }
        boundaries.add(decompressedSize);

        return new Rac1CoreIndex(index, mobyClasses, tieClasses, gadgets, mobyTextures, tieTextures,
                DiscBytes.i32(index, 0x60), decompressedSize, ratchetSequences, boundaries);
    }

    private static List<Rac1GadgetEntry> gadgets(byte[] index) throws DiscFormatException {
        int count = DiscBytes.i32(index, 0x80);
        int offset = DiscBytes.i32(index, 0x84);
        List<Rac1GadgetEntry> entries = new ArrayList<>();
        if (count <= 0 || offset <= 0) {
            return entries;
        }
        if (count > 1000 || !DiscBytes.inBounds(index, offset, count * Rac1GadgetEntry.SIZE)) {
            throw new DiscFormatException("The table of the gadgets of the core index points "
                    + "outside of it.");
        }
        for (int entry = 0; entry < count; entry++) {
            int at = offset + entry * Rac1GadgetEntry.SIZE;
            entries.add(new Rac1GadgetEntry(DiscBytes.i32(index, at), DiscBytes.i32(index, at + 4),
                    DiscBytes.i32(index, at + 8)));
        }
        return entries;
    }

    private static void add(TreeSet<Integer> boundaries, int offset) {
        if (offset > 0) {
            boundaries.add(offset);
        }
    }

    private static List<Rac1ClassEntry> classes(byte[] index, int field) throws DiscFormatException {
        int count = DiscBytes.i32(index, field);
        int offset = DiscBytes.i32(index, field + 4);
        List<Rac1ClassEntry> entries = new ArrayList<>();
        if (count <= 0 || offset <= 0) {
            return entries;
        }
        if (count > 100_000 || !DiscBytes.inBounds(index, offset, count * Rac1ClassEntry.SIZE)) {
            throw new DiscFormatException("A class table of the core index points outside of it.");
        }
        for (int entry = 0; entry < count; entry++) {
            int base = offset + entry * Rac1ClassEntry.SIZE;
            byte[] slots = new byte[16];
            System.arraycopy(index, base + 0x10, slots, 0, 16);
            entries.add(new Rac1ClassEntry(DiscBytes.i32(index, base),
                    DiscBytes.i32(index, base + 4), slots));
        }
        return entries;
    }

    private static List<Rac1TextureEntry> textures(byte[] index, int field) throws DiscFormatException {
        int count = DiscBytes.i32(index, field);
        int offset = DiscBytes.i32(index, field + 4);
        List<Rac1TextureEntry> entries = new ArrayList<>();
        if (count <= 0 || offset <= 0) {
            return entries;
        }
        if (count > 100_000 || !DiscBytes.inBounds(index, offset, count * Rac1TextureEntry.SIZE)) {
            throw new DiscFormatException("A texture table of the core index points outside of it.");
        }
        for (int entry = 0; entry < count; entry++) {
            int base = offset + entry * Rac1TextureEntry.SIZE;
            entries.add(new Rac1TextureEntry(DiscBytes.i32(index, base),
                    DiscBytes.i16(index, base + 4), DiscBytes.i16(index, base + 6),
                    DiscBytes.i16(index, base + 8), DiscBytes.i16(index, base + 10),
                    DiscBytes.i16(index, base + 12)));
        }
        return entries;
    }

    /**
     * The number of bytes a block of the core data that starts at the given offset holds.
     *
     * @param offset the start of the block
     * @return the size of the block in bytes
     */
    public int blockSize(int offset) {
        Integer next = boundaries.higher(offset);
        int end = next == null ? decompressedSize : next;
        return Math.max(0, end - offset);
    }

    /**
     * The entry of the table of the moby classes with the given number.
     *
     * @param classNumber the number of the class
     * @return the entry, or {@code null} when the level has no such class
     */
    public Rac1ClassEntry mobyClass(int classNumber) {
        for (Rac1ClassEntry entry : mobyClasses) {
            if (entry.classNumber() == classNumber) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The entry of the table of the tie classes with the given number.
     *
     * @param classNumber the number of the class
     * @return the entry, or {@code null} when the level has no such class
     */
    public Rac1ClassEntry tieClass(int classNumber) {
        for (Rac1ClassEntry entry : tieClasses) {
            if (entry.classNumber() == classNumber) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The entries of the table of the moby classes of the level.
     *
     * @return the entries
     */
    public List<Rac1ClassEntry> mobyClasses() {
        return mobyClasses;
    }

    /**
     * The entries of the table of the tie classes of the level.
     *
     * @return the entries
     */
    public List<Rac1ClassEntry> tieClasses() {
        return tieClasses;
    }

    /**
     * The entry of the table of the gadgets with the given number.
     *
     * @param classNumber the number of the class of the gadget
     * @return the entry, or {@code null} when the level has no such gadget
     */
    public Rac1GadgetEntry gadget(int classNumber) {
        for (Rac1GadgetEntry entry : gadgets) {
            if (entry.classNumber() == classNumber) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The entries of the table of the gadgets of the level.
     *
     * @return the entries
     */
    public List<Rac1GadgetEntry> gadgets() {
        return gadgets;
    }

    /**
     * The textures of the moby classes of the level.
     *
     * @return the entries of the table
     */
    public List<Rac1TextureEntry> mobyTextures() {
        return mobyTextures;
    }

    /**
     * The textures of the tie classes of the level.
     *
     * @return the entries of the table
     */
    public List<Rac1TextureEntry> tieTextures() {
        return tieTextures;
    }

    /**
     * The offset of the pixels of the textures in the core data.
     *
     * @return the offset in bytes
     */
    public int texturesBase() {
        return texturesBase;
    }

    /**
     * The size of the decompressed core data.
     *
     * @return the size in bytes
     */
    public int decompressedSize() {
        return decompressedSize;
    }

    /**
     * The offsets of the animation sequences of the hero in the core data.
     *
     * <p>The hero has no sequences of his own: his sequences live in the core data of the level
     * and the core index holds 256 offsets that point at them.
     *
     * @return the 256 offsets, zero for the slots that are not used
     */
    public int[] ratchetSequences() {
        return ratchetSequences.clone();
    }

    /**
     * The bytes of the core index.
     *
     * @return the bytes
     */
    public byte[] bytes() {
        return index;
    }
}
