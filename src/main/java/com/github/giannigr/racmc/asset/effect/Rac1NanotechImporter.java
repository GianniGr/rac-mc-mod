package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import java.io.IOException;
import java.util.*;
import java.security.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** Curated level1 revision-specific absolute overlay references verified by R09.
 * No GP guesses, program execution, missing meshes or extraction of original controller code. */
public final class Rac1NanotechImporter {
    private Rac1NanotechImporter() {}
    private static final int[] LENGTHS = {4640, 2320, 1160, 1536, 256, 256, 32, 12};
    private static final int[] NTSC = {0x1fbe20, 0x1fd040, 0x1fd950, 0x1fdde0, 0x1fe3e0, 0x1fe4e0, 0x1fe5e0, 0x161e60};
    private static final int[] PAL = {0x1fc0e0, 0x1fd300, 0x1fdc10, 0x1fe0a0, 0x1fe6a0, 0x1fe7a0, 0x1fe8a0, 0x161ef0};
    private static final String[] HASHES = {
            "d7de0bb8b147d1445ee2efd13bc2050ac89450c681228bd32cdb5bdb6cd214f5",
            "0219cbba0b4e77438c33131b6232bb756f496b46f4b2ace9291d582a12e8c9e3",
            "357d1e7f41ae964f6a04d6250b1d738fb0ac3a5f4cef4c0cc9f7b805b8eb1913",
            "5cad1af2504101b24e1b260398ede777b7c68f99b54692e7e63519c8d3aacfc7",
            "5e1208c491ba649e78c57ce397eb8a53032579c7a6fa677a41c8f2df50f48f95",
            "a86546e50c1656e16d4af46dcc0425ccfbbf78b36026cb60a3195fc35d943453",
            "6d393bf799abce5f21c38500f916d075404119fadf5383f3af73732107cf0e08"};
    public static NanotechTables read(Rac1DiscImage image) throws IOException, DiscFormatException, AssetFormatException {
        return parse(overlay(image), image.revision());
    }
    private static byte[] overlay(Rac1DiscImage image) throws IOException, DiscFormatException, AssetFormatException {
        var tables = Rac1DiscTables.read(image);
        var container = Rac1LevelData.read(image, Rac1LevelHeader.read(image, tables.levelSector(1)));
        byte[] header = image.readBytes(container.base(), Rac1LevelData.HEADER_SIZE);
        int offset = word(header, 0), length = word(header, 4);
        if (offset < Rac1LevelData.HEADER_SIZE || length < 16 || length > 8 * 1024 * 1024
                || (long) offset + length > container.coreIndexOffset())
            throw new AssetFormatException("Nanotech overlay outside bounded code region");
        return image.readBytes(container.base() + offset, length);
    }
    private record Section(int address, int start, int length) {}
    public static NanotechTables parse(byte[] bytes, Rac1DiscRevision revision) throws AssetFormatException {
        byte[][] data = parseTables(bytes, revision == Rac1DiscRevision.NTSC_U_V1_00 ? NTSC : PAL, LENGTHS, HASHES);
        try {
            return new NanotechTables(NanotechTables.vectors(data[0], 290), NanotechTables.floats(data[1]),
                    NanotechTables.words(data[2], false), NanotechTables.vectors(data[3], 96),
                    NanotechTables.words(data[4], true), NanotechTables.vectors(data[5], 16),
                    NanotechTables.words(data[6], true), NanotechTables.floats(data[7]));
        } catch (IllegalArgumentException invalid) {
            throw new AssetFormatException("Invalid nanotech geometry", invalid);
        }
    }
    /** Bounded shared overlay-table seam; caller supplies independently verified profile references/digests. */
    public static byte[][] readVerifiedTables(Rac1DiscImage image, int[] addresses, int[] lengths, String[] hashes)
            throws IOException, DiscFormatException, AssetFormatException {
        return parseTables(overlay(image), addresses, lengths, hashes);
    }
    private static byte[][] parseTables(byte[] bytes, int[] addresses, int[] lengths, String[] hashes)
            throws AssetFormatException {
        if (bytes.length > 8 * 1024 * 1024) throw new AssetFormatException("Oversized overlay");
        List<Section> sections = new ArrayList<>();
        int at = 0, entry = 0;
        while (at <= bytes.length - 16 && sections.size() < 64) {
            int address = word(bytes, at), length = word(bytes, at + 4), currentEntry = word(bytes, at + 12);
            if (!sections.isEmpty() && currentEntry != entry) break; // Original overlay terminator.
            if (sections.isEmpty()) entry = currentEntry;
            if (address < 0 || length < 0 || address > Integer.MAX_VALUE - length)
                throw new AssetFormatException("Invalid overlay virtual extent");
            range(bytes, at + 16, length);
            for (Section section : sections)
                if ((long) address < (long) section.address() + section.length()
                        && (long) section.address() < (long) address + length)
                    throw new AssetFormatException("Overlapping overlay sections");
            sections.add(new Section(address, at + 16, length));
            at += 16 + length;
        }
        byte[][] data = new byte[addresses.length][];
        for (int id = 0; id < data.length; id++) {
            int address = addresses[id], length = lengths[id];
            Section found = null;
            for (Section section : sections)
                if (address >= section.address() && (long) address + length <= (long) section.address() + section.length())
                    found = section;
            if (found == null) throw new AssetFormatException("Required nanotech table absent");
            int start = found.start() + address - found.address();
            data[id] = Arrays.copyOfRange(bytes, start, start + length);
            if (id < hashes.length && !hash(data[id]).equals(hashes[id]))
                throw new AssetFormatException("Effect overlay source table digest mismatch");
        }
        return data;
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
