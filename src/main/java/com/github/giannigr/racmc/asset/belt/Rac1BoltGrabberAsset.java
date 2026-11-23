package com.github.giannigr.racmc.asset.belt;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.Rac1LevelHeader;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;

/** Worn extra only. No pickup mesh fallback, level scan, path-open, or output files. */
public final class Rac1BoltGrabberAsset {
    public static final int CLASS = 618;
    public static final int NOVALIS = 1;
    private static final int MAX_CLASS_BYTES = 256 * 1024;

    private Rac1BoltGrabberAsset() {}

    public static Rac1AnimatedAsset read(Rac1MediaLease image)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        image.checkValid();
        var sector = Rac1DiscTables.read(image).levelSector(NOVALIS);
        if (sector <= 0 || Rac1LevelHeader.read(image, sector).id() != NOVALIS)
            throw new AssetFormatException("Expected Novalis L01 for the worn Bolt Grabber");
        var level = Rac1LevelAssets.load(image, NOVALIS);
        var entry = level.coreIndex().mobyClass(CLASS);
        int size = entry == null ? 0 : level.coreIndex().blockSize(entry.offset());
        if (size <= 0 || size > MAX_CLASS_BYTES)
            throw new AssetFormatException("Missing or out-of-bounds worn Bolt Grabber class 618");
        var model = Rac1AssetExtractor.readAnimatedAsset(level, CLASS);
        // Pinned by both supported retail revisions. Never substitute the pickup or an untested rig.
        if (model.classNumber() != CLASS || model.skeleton().jointCount() != 5
                || model.mesh().vertexCount() != 616 || model.mesh().triangleCount() != 496
                || model.textures().length != 1 || model.animations().length != 1
                || !"sequence_0".equals(model.animations()[0].name()) || model.animations()[0].keyCount() != 1)
            throw new AssetFormatException("Unexpected worn Bolt Grabber class 618 mesh, material or static pose");
        image.checkValid();
        return model;
    }
}
