package com.github.giannigr.racmc.cache.effect;

import com.github.giannigr.racmc.asset.effect.EffectBanks;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

/** Single-writer effects namespace, unique staging + verified atomic publication.
 * Failed staging is retained for diagnosis; last-good target is never deleted. */
public final class Rac1EffectCacheStore {
    private final Path root;
    public Rac1EffectCacheStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Path path(String sha1, int level) {
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}") || level < 0 || level > 18)
            throw new IllegalArgumentException("Invalid effect profile");
        return root.resolve(sha1).resolve("effects").resolve(Integer.toString(Rac1EffectCacheCodec.SCHEMA))
                .resolve(Integer.toString(Rac1EffectCacheCodec.CONVERTER))
                .resolve("level-" + level + ".racefx");
    }

    public Optional<EffectBanks> read(String sha1, int level) throws IOException {
        Path file = path(sha1, level);
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > Rac1EffectCacheCodec.MAX_FILE_BYTES)
            throw new IOException("Effects sidecar too large");
        try (var input = Files.newInputStream(file)) {
            return Optional.of(Rac1EffectCacheCodec.read(input.readNBytes(
                    Rac1EffectCacheCodec.MAX_FILE_BYTES + 1), sha1, level));
        }
    }

    public void write(EffectBanks banks) throws IOException {
        String sha1 = banks.particles().source().discSha1();
        int level = banks.particles().source().sourceLevel();
        Path target = path(sha1, level);
        Files.createDirectories(target.getParent());
        byte[] bytes = Rac1EffectCacheCodec.write(banks);
        Path staging = Files.createTempFile(target.getParent(), "effect-staging-", ".racefx");
        Files.write(staging, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        Rac1EffectCacheCodec.read(Files.readAllBytes(staging), sha1, level);
        // No non-atomic fallback: unsupported filesystems fail, preserving the last good file.
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
