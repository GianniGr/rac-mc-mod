package com.github.giannigr.racmc.asset.belt;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.Rac1LevelHeader;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;

/** Original Quartu pickup only, never the visual attached to Ratchet or Steve. */
public final class Rac1BoltGrabberPickupAsset {
    public static final int CLASS = 1388;
    public static final int QUARTU = 15;
    private static final int MAX_CLASS_BYTES = 256 * 1024;

    private Rac1BoltGrabberPickupAsset() {}

    public static Rac1StaticAsset read(Rac1MediaLease image)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        image.checkValid();
        var sector = Rac1DiscTables.read(image).levelSector(QUARTU);
        if (sector <= 0 || Rac1LevelHeader.read(image, sector).id() != QUARTU)
            throw new AssetFormatException("Expected Quartu L15 for the Bolt Grabber pickup");
        var level = Rac1LevelAssets.load(image, QUARTU);
        var entry = level.coreIndex().mobyClass(CLASS);
        int size = entry == null ? 0 : level.coreIndex().blockSize(entry.offset());
        if (size <= 0 || size > MAX_CLASS_BYTES)
            throw new AssetFormatException("Missing or out-of-bounds Bolt Grabber pickup class 1388");
        var model = Rac1AssetExtractor.readJointlessMoby(level, CLASS);
        if (model.classNumber() != CLASS || model.mesh().vertexCount() == 0
                || model.mesh().triangleCount() == 0 || model.textures().length == 0)
            throw new AssetFormatException("Unexpected Bolt Grabber pickup mesh");
        image.checkValid();
        return model;
    }
}
