package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.wad.WadDecoder;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Targeted metadata for the green ship's two scene lumps; no camera, subtitles, or audio. */
public final class GreenShipScene {
    public static final int DIRECTORY_BYTES = 70 * 8;
    private static final int CHUNK_BASE = 0x800;
    private static final int HEADER_BYTES = 0x14;
    private static final int MAX_ACTORS = 256;
    // Largest allowed chunk zero from the four matched retail directories.
    private static final int MAX_FIRST_CHUNK_BYTES = 51_200;
    private static final int RATCHET = 0;
    private static final int SHIP_SLOT = 2;

    private GreenShipScene() {}

    public record Cast(int endTick, int shipSlot, int shipClass, int shipOffset,
                       int ratchetSlot, int ratchetOffset) {}

    public static List<GreenShipContainer.Slice> directory(byte[] bytes, long extentBytes)
            throws AssetFormatException {
        if (bytes.length != DIRECTORY_BYTES || extentBytes <= CHUNK_BASE
                || extentBytes > Integer.MAX_VALUE) {
            throw new AssetFormatException("Invalid green ship scene directory extent.");
        }
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        var chunks = new ArrayList<GreenShipContainer.Slice>();
        long previousEnd = CHUNK_BASE;
        while (records.hasRemaining()) {
            int relative = records.getInt();
            int length = records.getInt();
            if (length == 0) {
                break;
            }
            long start = CHUNK_BASE + (long) relative;
            if (relative < 0 || length < 16 || start < previousEnd
                    || start + length > extentBytes) {
                throw new AssetFormatException("Invalid green ship scene chunk " + chunks.size() + ".");
            }
            chunks.add(new GreenShipContainer.Slice((int) start, length));
            previousEnd = start + length;
        }
        if (chunks.isEmpty()) {
            throw new AssetFormatException("Green ship scene has no chunks.");
        }
        return List.copyOf(chunks);
    }

    /** Return the declared stored length before reading the rest of one selected chunk. */
    public static int storedFirstChunkBytes(byte[] header, int directoryBytes) throws AssetFormatException {
        if (header.length != WadDecoder.HEADER_SIZE || !WadDecoder.isWad(header)
                || directoryBytes < WadDecoder.HEADER_SIZE || directoryBytes > MAX_FIRST_CHUNK_BYTES) {
            throw new AssetFormatException("Invalid first green ship scene WAD header or cap.");
        }
        int stored = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(3);
        if (stored < WadDecoder.HEADER_SIZE || stored > directoryBytes) {
            throw new AssetFormatException("First green ship scene WAD exceeds its directory span.");
        }
        return stored;
    }

    /** Decode only chunk zero; never retain or parse its camera or subtitle blocks. */
    public static Cast firstCompressedCast(byte[] wad, int directoryBytes) throws AssetFormatException {
        byte[] header = Arrays.copyOf(wad, Math.min(wad.length, WadDecoder.HEADER_SIZE));
        if (storedFirstChunkBytes(header, directoryBytes) != wad.length) {
            throw new AssetFormatException("Expected only the declared green ship scene WAD bytes.");
        }
        // WAD packets output at most 291 bytes per four stored bytes. The 51,200-byte
        // input cap bounds decoded bytes below 3.73 MB even without a stored output size.
        try {
            return firstCast(WadDecoder.decode(wad));
        } catch (WadFormatException | IndexOutOfBoundsException badWad) {
            throw new AssetFormatException("Invalid first green ship scene WAD packet.", badWad);
        }
    }

    /** Read metadata from decompressed chunk zero only, not actor keys or camera data. */
    public static Cast firstCast(byte[] decoded) throws AssetFormatException {
        if (decoded.length < HEADER_BYTES) {
            throw new AssetFormatException("Truncated green ship scene chunk.");
        }
        ByteBuffer data = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
        int end = Short.toUnsignedInt(data.getShort(0));
        int actors = Short.toUnsignedInt(data.getShort(0xc));
        int firstActor = HEADER_BYTES + actors * 4;
        if (end == 0 || actors <= SHIP_SLOT || actors > MAX_ACTORS || firstActor > decoded.length - 16) {
            throw new AssetFormatException("Invalid green ship scene tick or actor count.");
        }
        int shipOffset = -1;
        int ratchetOffset = -1;
        for (int slot = 0; slot < actors; slot++) {
            int offset = data.getInt(HEADER_BYTES + slot * 4);
            if (offset < firstActor || offset > decoded.length - 16) {
                throw new AssetFormatException("Invalid green ship scene actor offset.");
            }
            int actorClass = data.getInt(offset);
            if (slot == 0) {
                if (actorClass != RATCHET) {
                    throw new AssetFormatException("Green ship scene actor 0 is not Ratchet.");
                }
                ratchetOffset = offset;
            } else if (slot == SHIP_SLOT) {
                if (actorClass != GreenShipToc.CLASS_NUMBER) {
                    throw new AssetFormatException("Green ship scene actor 2 is not class 531.");
                }
                shipOffset = offset;
            }
        }
        return new Cast(end, SHIP_SLOT, GreenShipToc.CLASS_NUMBER, shipOffset, 0, ratchetOffset);
    }
}
