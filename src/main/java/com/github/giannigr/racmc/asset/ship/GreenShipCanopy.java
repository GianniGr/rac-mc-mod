package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Exact class-531 Novalis canopy slices for verified NTSC data; no PAL relocation guess. */
public final class GreenShipCanopy {
    private static final long DATA_HEADER = 3_876_693_432L;
    private static final int DATA_VA = 0x166480;
    private static final int DATA_BYTES = 677_456;
    private static final int OVERLAY_ENTRY = 0x259c40;
    private static final int HEADER_BYTES = 16;
    private static final int POINTS_VA = 0x1bf3c0;
    private static final int QUADS_VA = 0x1bf630;
    private static final int NORMALS_VA = 0x1bf7b0;
    private static final int POINT_COUNT = 39;
    private static final int QUAD_COUNT = 24;
    private static final int RECORD_BYTES = 16;
    private static final int TINT_GS = 0x30807060;

    private GreenShipCanopy() {}

    public record Vector(float x, float y, float z) {}
    public record Quad(int a, int b, int c, int d) {}
    public record Geometry(List<Vector> points, List<Quad> quads, List<Vector> normals, int tintGs) {}

    /** Caller supplies an existing generation-owned lease. PAL performs no canopy reads. */
    public static Optional<Geometry> load(Rac1MediaLease lease)
            throws IOException, AssetFormatException {
        lease.checkValid();
        var result = readVerified(lease);
        lease.checkValid();
        return result;
    }

    /** Pure read contract for the lease consumer and bounded verifier. No media discovery here. */
    static Optional<Geometry> readVerified(Rac1DiscImage image)
            throws IOException, AssetFormatException {
        if (image.revision() != Rac1DiscRevision.NTSC_U_V1_00) {
            return Optional.empty();
        }
        long dataBase = DATA_HEADER + HEADER_BYTES;
        if (dataBase > image.imageSizeBytes() || DATA_BYTES > image.imageSizeBytes() - dataBase) {
            throw new AssetFormatException("NTSC canopy .data section falls outside the image.");
        }
        var header = bytes(image, DATA_HEADER, HEADER_BYTES);
        if (header.getInt() != DATA_VA || header.getInt() != DATA_BYTES
                || header.getInt() != 1 || header.getInt() != OVERLAY_ENTRY) {
            throw new AssetFormatException("NTSC canopy .data section does not match its file-backed map.");
        }
        // Counts and tint are pinned from the separately approved NTSC selectors. Do not reread them.
        var points = vectors(section(image, POINTS_VA, POINT_COUNT), POINT_COUNT, "points");
        var quads = quads(section(image, QUADS_VA, QUAD_COUNT));
        var normals = vectors(section(image, NORMALS_VA, POINT_COUNT), POINT_COUNT, "normals");
        return Optional.of(new Geometry(points, quads, normals, TINT_GS));
    }

    private static ByteBuffer section(Rac1DiscImage image, int address, int count)
            throws IOException, AssetFormatException {
        int relative = address - DATA_VA;
        int length = count * RECORD_BYTES;
        if (count < 1 || count > 256 || (address & 15) != 0 || relative < 0
                || relative > DATA_BYTES - length) {
            throw new AssetFormatException("NTSC canopy record bounds are invalid.");
        }
        return bytes(image, DATA_HEADER + HEADER_BYTES + relative, length);
    }

    private static ByteBuffer bytes(Rac1DiscImage image, long offset, int length)
            throws IOException, AssetFormatException {
        byte[] bytes = image.readBytes(offset, length);
        if (bytes.length != length) {
            throw new AssetFormatException("Short NTSC canopy slice.");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static List<Vector> vectors(ByteBuffer data, int count, String field)
            throws AssetFormatException {
        var vectors = new ArrayList<Vector>(count);
        for (int i = 0; i < count; i++) {
            float x = data.getFloat(), y = data.getFloat(), z = data.getFloat(), w = data.getFloat();
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || w != 1f) {
                throw new AssetFormatException("Invalid NTSC canopy " + field + " vector " + i + ".");
            }
            vectors.add(new Vector(x, y, z));
        }
        return List.copyOf(vectors);
    }

    private static List<Quad> quads(ByteBuffer data) throws AssetFormatException {
        var quads = new ArrayList<Quad>(QUAD_COUNT);
        for (int i = 0; i < QUAD_COUNT; i++) {
            int at = i * RECORD_BYTES;
            quads.add(new Quad(index(data, at), index(data, at + 4), index(data, at + 8), index(data, at + 12)));
        }
        return List.copyOf(quads);
    }

    private static int index(ByteBuffer data, int at) throws AssetFormatException {
        int index = Short.toUnsignedInt(data.getShort(at));
        if (index >= POINT_COUNT) {
            throw new AssetFormatException("NTSC canopy quad references an unknown point.");
        }
        return index;
    }
}
