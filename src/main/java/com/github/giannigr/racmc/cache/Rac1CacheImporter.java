package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Existing asset catalog conversion shared by client and headless server; caller owns the disc. */
public final class Rac1CacheImporter {
    private Rac1CacheImporter() { }

    public static Rac1CacheContent read(Rac1DiscImage disc, String key,
                                        BiConsumer<String, Integer> progress)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        progress.accept("The assets of the level are read.", 20);
        Rac1LevelAssets assets = Rac1LevelAssets.load(disc, Rac1AssetExtractor.LEVEL);
        progress.accept("The scenery is converted.", 40);
        var scenery = Rac1AssetExtractor.readStaticAsset(assets, Rac1AssetExtractor.STATIC_CLASS);
        progress.accept("The character is converted.", 60);
        var hero = Rac1AssetExtractor.readAnimatedAsset(assets, Rac1AssetExtractor.ANIMATED_CLASS);
        progress.accept("The weapons are converted.", 80);
        var blaster = Rac1AssetExtractor.readGadgetAsset(assets, Rac1AssetExtractor.BLASTER_CLASS);
        var wrench = Rac1AssetExtractor.readGadgetAsset(assets, Rac1AssetExtractor.WRENCH_CLASS);
        progress.accept("The crates are converted.", 90);
        Map<String, Rac1CrateAsset> crates = new LinkedHashMap<>();
        for (var definition : Rac1Crates.definitions()) {
            crates.put(definition.key(), Rac1AssetExtractor.readCrateAsset(assets, definition));
        }
        int[] hand = hero.attachmentChain(0);
        int joint = hand.length == 0 ? 0 : hand[hand.length - 1];
        return new Rac1CacheContent(key, scenery, hero, blaster, joint, crates, wrench);
    }
}
