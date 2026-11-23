package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import java.io.IOException;

/** Off-thread loader for a caller-owned lease. Reads only the ship's TOC, header, and main slices. */
public final class GreenShipHullLoader {
    private GreenShipHullLoader() {}

    public static GreenShipContainer.Hull load(Rac1MediaLease lease)
            throws IOException, DiscFormatException, AssetFormatException {
        lease.checkValid();
        var hull = GreenShipToc.readHull(lease);
        var header = lease.readBytes(hull.byteOffset(), GreenShipContainer.HEADER_BYTES);
        var layout = GreenShipContainer.layout(header, hull.byteLength());
        var classBytes = read(lease, hull.byteOffset(), layout.hullClass());
        var textureBytes = read(lease, hull.byteOffset(), layout.hullTexture());
        var result = GreenShipContainer.decode(classBytes, textureBytes);
        lease.checkValid();
        return result;
    }

    private static byte[] read(Rac1MediaLease lease, long base, GreenShipContainer.Slice slice)
            throws IOException {
        return lease.readBytes(base + slice.offset(), slice.length());
    }
}
