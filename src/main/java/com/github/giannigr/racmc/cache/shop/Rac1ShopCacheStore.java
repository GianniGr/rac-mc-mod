package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopAssets;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

/** Local-only, unique verified staging and atomic replace; failed staging is retained. */
public final class Rac1ShopCacheStore {
    private final Path root;
    public Rac1ShopCacheStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Path path(String sha1, int language) {
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}") || !java.util.Set.of(0, 2, 3, 4, 5).contains(language))
            throw new IllegalArgumentException("Invalid shop cache identity");
        return root.resolve(sha1).resolve("shop").resolve(Rac1ShopCacheCodec.SCHEMA + "-"
                + Rac1ShopCacheCodec.CONVERTER).resolve("language-" + language + ".racshop");
    }

    public Optional<Rac1ShopAssets> read(String sha1, int language) throws IOException {
        Path path = path(sha1, language);
        if (!Files.exists(path)) return Optional.empty();
        if (Files.size(path) > Rac1ShopCacheCodec.MAX_BYTES) throw new IOException("Shop sidecar too large");
        try (var in = Files.newInputStream(path)) {
            return Optional.of(Rac1ShopCacheCodec.read(in.readNBytes(Rac1ShopCacheCodec.MAX_BYTES + 1), sha1, language));
        }
    }

    public void write(Rac1ShopAssets assets) throws IOException {
        Path target = path(assets.discSha1(), assets.language());
        byte[] bytes = Rac1ShopCacheCodec.write(assets);
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempFile(target.getParent(), "shop-staging-", ".racshop");
        Files.write(staging, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        Rac1ShopCacheCodec.read(Files.readAllBytes(staging), assets.discSha1(), assets.language());
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
