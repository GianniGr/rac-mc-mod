package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** The three retail TOC records for the green travel ship; never reads their payloads. */
public final class GreenShipToc {
    public static final int CLASS_NUMBER = 531;
    private static final int SHIP_BASE = 0x12c8;
    private static final int SCENE_BASE = 0x12e8;
    private static final int RECORD_BYTES = 8;
    private static final long TOC_BASE = Rac1DiscTables.TOC_SECTOR * (long) Rac1DiscTables.SECTOR_SIZE;

    private GreenShipToc() {}

    public record Extent(long byteOffset, long byteLength) {}
    public record Selection(Extent hull, Extent takeoff, Extent landing) {}

    /** Read only from a verified image or lease supplied by the caller. */
    public static Selection read(Rac1DiscImage image) throws IOException, DiscFormatException {
        int takeoff = switch (image.revision()) {
            case NTSC_U_V1_00 -> 1;
            case PAL_V2_00 -> 11;
        };
        return new Selection(readHull(image), read(image, SCENE_BASE, takeoff),
                read(image, SCENE_BASE, takeoff + 4));
    }

    /** The presentation loader needs no scene or audio data to render the hull. */
    public static Extent readHull(Rac1DiscImage image) throws IOException, DiscFormatException {
        return read(image, SHIP_BASE, 1);
    }

    private static Extent read(Rac1DiscImage image, int base, int slot)
            throws IOException, DiscFormatException {
        long record = TOC_BASE + base + (long) slot * RECORD_BYTES;
        byte[] bytes = image.readBytes(record, RECORD_BYTES);
        if (bytes.length != RECORD_BYTES) {
            throw new DiscFormatException("Truncated green ship TOC record.");
        }
        ByteBuffer fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long offset = Integer.toUnsignedLong(fields.getInt()) * Rac1DiscTables.SECTOR_SIZE;
        long length = Integer.toUnsignedLong(fields.getInt()) * Rac1DiscTables.SECTOR_SIZE;
        if (offset < TOC_BASE + Rac1DiscTables.TOC_SIZE || length == 0
                || offset > image.imageSizeBytes() || length > image.imageSizeBytes() - offset) {
            throw new DiscFormatException("Invalid green ship TOC extent at byte " + record + ".");
        }
        return new Extent(offset, length);
    }
}
