package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for a run of the import of the assets that is interrupted while it writes the
 * cache.
 *
 * <p>An interrupted run can leave a staging file behind: a file that holds half of a cache and
 * that is never promoted onto the file of the cache. The checks hold that such a file is never
 * used, that the cache that was there before stays usable, and that a new attempt recovers from
 * it.
 *
 * <p>The checks use temporary directories and assets that were built by hand. The half written
 * files are written by the checks themselves, exactly the way an interrupted run leaves them; no
 * file of a run is removed by hand outside of the product code.
 */
class Rac1CacheInterruptionTest {

    /** The identity of the disc the checks use. */
    private static final String KEY = "NTSC-U v1.00|SCUS_971.99|test";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A cache that was there stays usable when the next write is interrupted")
    void aCacheThatWasThereStaysUsableWhenTheNextWriteIsInterrupted() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));
        Path file = store.fileFor(KEY);
        Path staging = stagingOf(file);
        Files.write(staging, halfOfAWrite());

        assertTrue(store.load(KEY).isPresent(),
                "The cache that was there before has to stay usable");
        assertEquals(4, store.load(KEY).orElseThrow().staticAsset().mesh().vertexCount());
        assertTrue(Files.exists(staging), "The check must not remove the file of the other run");

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(store.load(KEY).isPresent(), "The new attempt has to succeed");
        assertFalse(Files.exists(staging), "The file of the half written cache has to be gone");
    }

    @Test
    @DisplayName("Half of a cache is never exposed as a cache")
    void halfOfACacheIsNeverExposed() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Files.write(stagingOf(store.fileFor(KEY)), halfOfAWrite());

        assertTrue(store.load(KEY).isEmpty(),
                "A file that was only half written must not be used as a cache");

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(store.load(KEY).isPresent(), "The new attempt has to succeed");
        assertFalse(Files.exists(stagingOf(store.fileFor(KEY))),
                "The file of the half written cache has to be gone");
    }

    @Test
    @DisplayName("A failure before the promotion does not leave a half cache behind")
    void aFailureBeforeThePromotionDoesNotLeaveAHalfCache() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Path file = store.fileFor(KEY);
        // The file of the cache cannot be replaced, so the write fails after the staging file
        // was read back and before it was moved onto the file of the cache.
        Files.createDirectories(file);

        assertThrows(IOException.class, () -> store.store(KEY, SyntheticAssets.content(KEY)));

        assertFalse(Files.exists(stagingOf(file)),
                "The staging file has to be gone even when the write failed");
        assertFalse(Files.isRegularFile(file), "No file of a cache may be written there");
        assertTrue(store.load(KEY).isEmpty(),
                "Half of a cache must not be exposed after a failed write");
    }

    @Test
    @DisplayName("The staging files of interrupted runs are removed when the cache is written "
            + "again")
    void theStagingFilesOfInterruptedRunsAreRemoved() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Path orphan = tempDir.resolve(stagingNameOf("another disc"));
        Files.write(orphan, halfOfAWrite());
        Path other = tempDir.resolve("notes.txt");
        Files.writeString(other, "of the player");

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(store.load(KEY).isPresent(), "The cache has to be written");
        assertFalse(Files.exists(orphan),
                "The staging file of an interrupted run of another disc has to be gone");
        assertTrue(Files.exists(other), "A file that is not a staging file has to stay");
    }

    @Test
    @DisplayName("A file of the player whose name only looks like a staging file stays")
    void aFileThatOnlyLooksLikeAStagingFileStays() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Path lookalike = tempDir.resolve("racm-orphan.racm.staging");
        Files.write(lookalike, halfOfAWrite());
        Path shortDigest = tempDir.resolve("racm-abc.racm.staging");
        Files.write(shortDigest, halfOfAWrite());
        Path upperCase = tempDir.resolve(stagingNameOf("another disc").toUpperCase(
                java.util.Locale.ROOT));
        Files.write(upperCase, halfOfAWrite());

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(Files.exists(lookalike),
                "A file of the player that only looks like a staging file has to stay");
        assertTrue(Files.exists(shortDigest),
                "A file whose name is not the name of this mod has to stay");
        assertTrue(Files.exists(upperCase),
                "The name of a staging file is built in lower case, so an upper case one is not "
                        + "one of this mod");
    }

    @Test
    @DisplayName("A directory whose name looks like a staging file stays")
    void aDirectoryThatLooksLikeAStagingFileStays() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Path directory = tempDir.resolve(stagingNameOf("another disc"));
        Files.createDirectories(directory);

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(Files.isDirectory(directory),
                "A directory of the player must not be removed by the cleanup");
    }

    @Test
    @DisplayName("A link whose name looks like a staging file stays")
    void aLinkThatLooksLikeAStagingFileStays() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        Path target = tempDir.resolve("notes.txt");
        Files.writeString(target, "of the player");
        Path link = tempDir.resolve(stagingNameOf("a third disc"));
        // A system that cannot build a link cannot show the case. The check is then aborted and
        // reported as skipped, so that it is never counted as a check that passed.
        Assumptions.assumeTrue(canBuildLink(link, target),
                "The system cannot build a symbolic link, so the case cannot be shown");

        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(Files.exists(link),
                "A link of the player must not be removed by the cleanup");
        assertTrue(Files.exists(target), "The file of the player must not be touched");
    }

    /**
     * The name of the staging file of a cache of the given disc, the way the mod builds it.
     */
    private static String stagingNameOf(String discKey) {
        return "racm-" + Rac1CacheStore.digest(discKey) + ".racm.staging";
    }

    /**
     * Builds a link, and holds whether the system was able to build one.
     */
    private static boolean canBuildLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            // A system without links cannot show the case; the other checks hold it anyway.
            return false;
        }
    }

    /**
     * The file a cache of the given file is written into before it is moved.
     */
    private static Path stagingOf(Path file) {
        return file.resolveSibling(file.getFileName().toString() + ".staging");
    }

    /**
     * The bytes an interrupted run leaves behind: the first half of a cache file.
     */
    private static byte[] halfOfAWrite() throws IOException {
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        byte[] half = new byte[file.length / 2];
        System.arraycopy(file, 0, half, 0, half.length);
        return half;
    }
}
