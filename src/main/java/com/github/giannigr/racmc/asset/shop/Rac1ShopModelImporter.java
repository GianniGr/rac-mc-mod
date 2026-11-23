package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.effect.Rac1EffectMeshImporter;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.wad.*;
import java.io.IOException;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Reads only from the existing validated-disc abstraction; never opens an unvalidated path. */
public final class Rac1ShopModelImporter {
    private Rac1ShopModelImporter() {}

    public static Rac1ShopModels read(Rac1DiscImage image, Rac1LevelAssets level, String sha1)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        if (level.level() != 1) throw new AssetFormatException("Original shop source must be Novalis");
        byte[] toc = Rac1DiscTables.read(image).bytes();
        int sector = word(toc, 0x198), count = word(toc, 0x19c);
        if (sector <= 0 || count <= 0 || count > 4096)
            throw new AssetFormatException("Invalid external salesman animation range");
        byte[] sequences = WadDecoder.decode(image.readSectors(sector, count), -1);
        if (sequences.length > 8 * 1024 * 1024)
            throw new AssetFormatException("Oversized external salesman animations");
        try {
            return new Rac1ShopModels(Rac1AssetExtractor.readAnimatedAsset(level, 12, sequences),
                    Rac1AssetExtractor.readAnimatedAsset(level, 13),
                    Rac1AssetExtractor.readAnimatedAsset(level, 1137),
                    Rac1EffectMeshImporter.read(level, sha1, 1006));
        } catch (IllegalArgumentException failure) {
            throw new AssetFormatException("Incomplete original shop models: " + failure.getMessage(), failure);
        }
    }
}
