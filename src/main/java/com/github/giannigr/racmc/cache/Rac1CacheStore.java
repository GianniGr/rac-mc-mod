package com.github.giannigr.racmc.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The place where the cache of the assets is kept.
 *
 * <p>The cache is written into a file of the directory of the cache, which the player can change
 * with {@link Rac1CacheLocation}. A new cache is written into a staging file first, checked by
 * reading it back, and only then moved onto the file of the cache. A game that is stopped while
 * the cache is written therefore never sees a cache that holds only half of the assets, and the
 * cache that was there before stays usable.
 *
 * <p>A store that was built from a directory that cannot be used never falls back to another
 * directory: every read and every write reports the reason instead, so the game stays closed
 * rather than working with a cache of a place the player did not ask for.
 */
public final class Rac1CacheStore {

    /** The version of the schema of the assets. */
    public static final int SCHEMA_VERSION = 3;

    /** The name of the file of a cache that is being written. */
    private static final String STAGING_SUFFIX = ".staging";

    /** The prefix of the files of caches of this mod. */
    private static final String FILE_PREFIX = "racm-";

    /**
     * The name of a staging file of this mod, and nothing else.
     *
     * <p>The name is the one the mod itself builds: the prefix, the digest of the identity of
     * the disc in lower case hexadecimal and the suffix of a cache file. A file of the player
     * whose name only looks like that one is never a staging file of this mod.
     */
    private static final Pattern OWNED_STAGING_NAME =
            Pattern.compile("racm-[0-9a-f]{64}\\.racm\\.staging");

    private final Path directory;

    /** The reason why the directory cannot be used, or {@code null} when it can be used. */
    private final String failure;

    /**
     * Creates the place where the cache is kept.
     *
     * @param directory the directory of the cache
     */
    public Rac1CacheStore(Path directory) {
        this(directory, null);
    }

    private Rac1CacheStore(Path directory, String failure) {
        this.directory = directory;
        this.failure = failure;
    }

    /**
     * Creates a place that cannot be used.
     *
     * <p>The cache manager reports the reason and keeps the game closed. The store does not fall
     * back to the standard directory, because a cache of a place the player did not ask for would
     * be a silent change of the configuration.
     *
     * @param failure the reason why the directory cannot be used
     * @return the store
     */
    public static Rac1CacheStore unusable(String failure) {
        Objects.requireNonNull(failure, "failure");
        return new Rac1CacheStore(null, failure);
    }

    /**
     * Whether the directory of the store can be used.
     *
     * @return {@code true} when reads and writes can go to the directory
     */
    public boolean isUsable() {
        return failure == null;
    }

    /**
     * The directory of the cache.
     *
     * @return the directory, or {@code null} when the store cannot be used
     */
    public Path directory() {
        return directory;
    }

    /**
     * The file that holds the cache of the given disc.
     *
     * @param discKey the identity of the disc
     * @return the file
     * @throws IllegalStateException when the store cannot be used
     */
    public Path fileFor(String discKey) {
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        return directory.resolve(FILE_PREFIX + digest(discKey) + ".racm");
    }

    /**
     * Checks that the cache can be written and read in the directory.
     *
     * <p>The check runs before every build of a cache as well, because the directory can become
     * unusable after the game was started. A directory that cannot be used is reported and is
     * never replaced by another one.
     *
     * @throws IOException when the directory cannot be used
     */
    public void verifyUsable() throws IOException {
        checkDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw unusableDirectory("\"" + directory + "\" could not be created ("
                    + describe(exception) + ")");
        }
        if (!Files.isWritable(directory)) {
            throw unusableDirectory("\"" + directory + "\" cannot be written to");
        }
    }

    /**
     * Checks that the directory is there and is a directory, without creating it.
     *
     * <p>A read must not create anything, so this is the check a read uses. The full check that
     * also creates the directory is {@link #verifyUsable()}.
     *
     * @throws IOException when the directory cannot be used
     */
    private void checkDirectory() throws IOException {
        if (failure != null) {
            throw new IOException(failure);
        }
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw unusableDirectory("\"" + directory + "\" is a file and not a directory");
        }
    }

    /**
     * The failure that reports a directory that cannot be used.
     *
     * @param reason what is wrong with it
     * @return the failure
     */
    private static IOException unusableDirectory(String reason) {
        return new IOException("The cache directory cannot be used: " + reason + ". The game "
                + "stays closed until it can be used; fix the directory or change the key "
                + "\"directory\" in the cache configuration of the mod and start the game again.");
    }

    /**
     * Writes the content of a cache.
     *
     * <p>The content is written into a staging file, read back and checked. Only a cache that
     * passes the check is moved onto the file of the cache. The staging files of earlier runs
     * that were interrupted are removed first; they are never read.
     *
     * @param discKey the identity of the disc the assets come from
     * @param content the content of the cache
     * @throws IOException when the cache cannot be written
     */
    public void store(String discKey, Rac1CacheContent content) throws IOException {
        verifyUsable();
        Files.createDirectories(directory);
        byte[] file = Rac1CacheCodec.write(content, SCHEMA_VERSION);
        Path target = fileFor(discKey);
        Path staging = stagingFor(target);
        deleteOwnedStaging();
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
     * @throws IOException when the store cannot be used, or when the cache is there but cannot be
     *         used
     */
    public Optional<Rac1CacheContent> load(String discKey) throws IOException {
        checkDirectory();
        Path target = fileFor(discKey);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        byte[] file = Files.readAllBytes(target);
        return Optional.of(Rac1CacheCodec.read(file, discKey, SCHEMA_VERSION));
    }

    /**
     * Removes the cache of the given disc.
     *
     * @param discKey the identity of the disc
     * @throws IOException when the store cannot be used or when the cache cannot be removed
     */
    public void discard(String discKey) throws IOException {
        checkDirectory();
        Files.deleteIfExists(fileFor(discKey));
    }

    /**
     * The file that a cache of the given disc is written into before it is moved.
     *
     * @param target the file of the cache
     * @return the staging file
     */
    private static Path stagingFor(Path target) {
        return target.resolveSibling(target.getFileName().toString() + STAGING_SUFFIX);
    }

    /**
     * Removes the staging files that an interrupted run left behind.
     *
     * <p>Only the staging files of this mod are removed, and only the ones whose name is exactly
     * the name this mod builds: {@code racm-} and the digest of the identity of a disc and
     * {@code .racm.staging}. No cache file, no file of the player and no file that only looks
     * like a staging file is touched. A leftover file is never read, so a directory that cannot
     * be listed does not stop the cache from being written.
     */
    private void deleteOwnedStaging() {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Rac1CacheStore::isOwnedStaging).forEach(Rac1CacheStore::deleteQuietly);
        } catch (IOException exception) {
            // The cleanup is a convenience; see the comment of the method.
        }
    }

    /**
     * Whether the entry is a staging file of a cache of this mod.
     *
     * <p>The name has to be the name this mod builds and nothing else, and the entry has to be
     * a plain file: a directory and a link that points somewhere else are never removed, so
     * nothing of the player is touched by accident.
     *
     * @param path the entry
     * @return {@code true} for a regular file named {@code racm-<64 hex digits>.racm.staging}
     */
    private static boolean isOwnedStaging(Path path) {
        Path name = path.getFileName();
        if (name == null || !OWNED_STAGING_NAME.matcher(name.toString()).matches()) {
            return false;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Removes one staging file without letting a failure of it stop the write.
     *
     * @param path the file
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // A file that cannot be removed is never read, so it does not stop the write.
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
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
