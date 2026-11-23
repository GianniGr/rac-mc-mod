package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopAssets;
import com.github.giannigr.racmc.asset.shop.Rac1ShopImporter;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.nio.file.Files;

/** Local sidecar preparation only; owns no configuration, media manager, world gate or GPU state. */
public final class Rac1ShopSelectedDiscLoader {
    private Rac1ShopSelectedDiscLoader() {}

    public static Rac1ShopAssets load(Rac1ShopCachePreparation.Selection selection) throws Exception {
        if (!Files.isDirectory(selection.cacheRoot()) || !Files.isWritable(selection.cacheRoot()))
            throw new IOException("The effective shop cache directory is no longer usable.");
        var validator = new Rac1DiscValidator();
        try (var outcome = validator.inspect(selection.discImage())) {
            if (!outcome.isSupported()) throw new IOException(outcome.result().message());
            var profile = validator.catalog().profiles().stream()
                    .filter(p -> p.revision() == outcome.result().revision()).findFirst()
                    .orElseThrow(() -> new IOException("Missing supported shop disc identity."));
            String actual = Rac1DiscIdentity.key(outcome.result().revision(),profile);
            if (!actual.equals(selection.mainDiscKey()))
                throw new IOException("The selected shop disc does not match the ready main cache.");
            return loadVerified(selection,outcome.image());
        }
    }
    /** Shared capability fast path. Preserve this sidecar loader's strict corruption diagnostics. */
    public static Rac1ShopAssets load(Rac1ShopCachePreparation.Selection selection,Rac1MediaLease lease) throws Exception {
        lease.checkValid();
        if (!lease.discKey().equals(selection.mainDiscKey()) || !lease.sourcePath().equals(selection.discImage()))
            throw new IOException("Shop sidecar selection does not match the verified lease");
        var result=loadVerified(selection,lease);
        lease.checkValid();
        return result;
    }
    private static Rac1ShopAssets loadVerified(Rac1ShopCachePreparation.Selection selection,Rac1DiscImage image) throws Exception {
            if (!Files.isDirectory(selection.cacheRoot()) || !Files.isWritable(selection.cacheRoot()))
                throw new IOException("The effective shop cache directory is no longer usable.");
            var store = new Rac1ShopCacheStore(selection.cacheRoot());
            // Corrupt/read-failed sidecars are preserved and reported, not silently overwritten.
            var cached = store.read(selection.sha1(),selection.language());
            if (cached.isPresent()) return cached.get();
            var level = Rac1LevelAssets.load(image,1);
            var imported = Rac1ShopImporter.read(image,level,selection.sha1(),selection.language());
            if (image instanceof Rac1MediaLease lease) lease.checkValid();
            store.write(imported);
            return imported;
    }
}
