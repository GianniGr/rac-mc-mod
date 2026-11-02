package com.github.giannigr.racmc.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The place where the cache of the assets is kept.
 *
 * <p>The cache is written into a file of the cache directory of the mod. A new cache is written
 * into a staging file first, checked by reading it back, and only then moved onto the file of the
 * cache. A game that is stopped while the cache is written therefore never sees a cache that holds
 * only half of the assets.
 */
public final class Rac1CacheStore {

    /** The version of the schema of the assets. */
    public static final int SCHEMA_VERSION = 3;

    /** The name of the file of a cache that is being written. */
    private static final String STAGING_SUFFIX = ".staging";

    private final Path directory;

    /**
     * Creates the place where the cache is kept.
     *
     * @param directory the directory of the cache
     */
    public Rac1CacheStore(Path directory) {
        this.directory = directory;
    }

    /**
     * The directory of the cache.
     *
     * @return the directory
     */
    public Path directory() {
        return directory;
    }

    /**
     * The file that holds the cache of the given disc.
     *
     * @param discKey the identity of the disc
     * @return the file
     */
    public Path fileFor(String discKey) {
        return directory.resolve("racm-" + digest(discKey) + ".racm");
    }

    /**
     * Writes the content of a cache.
     *
     * <p>The content is written into a staging file, read back and checked. Only a cache that
     * passes the check is moved onto the file of the cache.
     *
     * @param discKey the identity of the disc the assets come from
     * @param content the content of the cache
     * @throws IOException when the cache cannot be written
     */
    public void store(String discKey, Rac1CacheContent content) throws IOException {
        Files.createDirectories(directory);
        byte[] file = Rac1CacheCodec.write(content, SCHEMA_VERSION);
        Path target = fileFor(discKey);
        Path staging = directory.resolve(target.getFileName().toString() + STAGING_SUFFIX);
        Files.write(staging, file);
        try {
            // The cache is only used when it can be read back.
            Rac1CacheCodec.read(Files.readAllBytes(staging), discKey, SCHEMA_VERSION);
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /**
     * Reads the cache of the given disc.
     *
     * @param discKey the identity of the disc the assets have to come from
     * @return the content of the cache, or an empty optional when there is no cache
     * @throws IOException when the cache is there but cannot be used
     */
    public java.util.Optional<Rac1CacheContent> load(String discKey) throws IOException {
        Path target = fileFor(discKey);
        if (!Files.isRegularFile(target)) {
            return java.util.Optional.empty();
        }
        byte[] file = Files.readAllBytes(target);
        return java.util.Optional.of(Rac1CacheCodec.read(file, discKey, SCHEMA_VERSION));
    }

    /**
     * Removes the cache of the given disc.
     *
     * @param discKey the identity of the disc
     * @throws IOException when the cache cannot be removed
     */
    public void discard(String discKey) throws IOException {
        Files.deleteIfExists(fileFor(discKey));
    }

    /**
     * The digest of a text, in lower case hexadecimal.
     *
     * @param text the text
     * @return the digest
     */
    public static String digest(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime",
                    exception);
        }
    }
}
