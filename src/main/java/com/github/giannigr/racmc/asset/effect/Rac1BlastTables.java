package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.iso.*;
import java.io.IOException;

/** Original table values stay client-local, not embedded as geometry fixtures in Git. */
public record Rac1BlastTables(float[] debrisOffsets, float[] debrisRotations, int[] sparkA, int[] sparkB) {
    public static Rac1BlastTables read(Rac1DiscImage image) throws IOException, AssetFormatException, DiscFormatException {
        boolean ntsc = image.revision() == Rac1DiscRevision.NTSC_U_V1_00;
        byte[][] tables = Rac1NanotechImporter.readVerifiedTables(image,
                ntsc ? new int[]{0x1deb90, 0x1debf0, 0x20b0b0, 0x20b0c8}
                        : new int[]{0x1dee60, 0x1deec0, 0x20bb10, 0x20bb28},
                new int[]{64, 64, 24, 24}, new String[]{
                        "ad52d0909f202513351b4941d092679af7b4ede8857e5bedfeb377b9a3bace44",
                        "ac8af6dfe4cd99975ced96db558b36edde68b172b6c9a6623475e19d4e411ce2",
                        "e019c6a0bb2d2d7e0bc2021f13e1035bcdba1383d48a89e3da9518a52721ede4",
                        "92071b73ac6c6d5be115568043cb2c5f5d4ca394584c3bdc2359cbcb6e85f103"});
        return new Rac1BlastTables(NanotechTables.vectors(tables[0], 4), NanotechTables.vectors(tables[1], 4),
                NanotechTables.words(tables[2], false), NanotechTables.words(tables[3], false));
    }
    public Rac1BlastTables {
        debrisOffsets = debrisOffsets.clone(); debrisRotations = debrisRotations.clone();
        sparkA = sparkA.clone(); sparkB = sparkB.clone();
        if (debrisOffsets.length != 12 || debrisRotations.length != 12 || sparkA.length != 6 || sparkB.length != 6)
            throw new IllegalArgumentException("Invalid blast tables");
        for (float[] values : new float[][]{debrisOffsets, debrisRotations})
            for (float value : values)
                if (!Float.isFinite(value) || Math.abs(value) > 16) throw new IllegalArgumentException("Invalid debris pose");
    }
}
