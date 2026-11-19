package com.github.giannigr.racmc.asset.clank;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;
import java.nio.file.Path;

/** Revalidates the complete retail image before reading Novalis; no image/cache upload. */
public final class Rac1ClankImporter {
    private Rac1ClankImporter() {}
    /** Optional Hydro-only lazy read under the same complete retail validation, not an aerial cache migration. */
    public static Rac1HydroAsset readHydro(Path image)
            throws IOException,DiscFormatException,WadFormatException,AssetFormatException {
        var validator=new Rac1DiscValidator();
        try(var selected=validator.inspect(image)) {
            if(!selected.isSupported())throw new IOException("Hydro requires a validated retail disc");
            var revision=selected.result().revision();
            var profile=validator.catalog().profiles().stream().filter(p->p.revision()==revision).findFirst().orElseThrow();
            return new Rac1HydroAsset(Rac1DiscIdentity.key(revision,profile),
                    Rac1AssetExtractor.readAnimatedAsset(Rac1LevelAssets.load(selected.image(),1),609));
        }
    }

    public static Rac1ClankAssets read(Path image)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        var validator = new Rac1DiscValidator();
        try (var selected = validator.inspect(image)) {
            if (!selected.isSupported()) throw new IOException("Clank requires a validated retail disc");
            var revision = selected.result().revision();
            var profile = validator.catalog().profiles().stream()
                    .filter(p -> p.revision() == revision).findFirst().orElseThrow();
            var level = Rac1LevelAssets.load(selected.image(), 1);
            return new Rac1ClankAssets(Rac1DiscIdentity.key(revision, profile),
                    Rac1AssetExtractor.readAnimatedAsset(level, 601),
                    Rac1AssetExtractor.readAnimatedAsset(level, 607),
                    Rac1AssetExtractor.readAnimatedAsset(level, 608));
        }
    }
}
