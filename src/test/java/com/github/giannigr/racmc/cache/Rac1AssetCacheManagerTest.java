package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the manager of the cache of the assets.
 *
 * <p>The checks use assets that were built by hand and a disc image that does not exist. No data of
 * the game is used anywhere.
 */
class Rac1AssetCacheManagerTest {

    /** The identity of the disc the checks use. */
    private static final String KEY = "NTSC-U v1.00|SCUS_971.99|test";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A cache that holds both assets is used")
    void aCacheThatHoldsBothAssetsIsUsed() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.READY, manager.state(),
                    "The cache that is there has to be used");
            assertTrue(manager.isReady());
            assertTrue(manager.content().isPresent());
            assertEquals(4, manager.content().get().staticAsset().mesh().vertexCount());
            assertEquals(4, manager.content().get().animatedAsset().mesh().vertexCount());
        }
    }

    @Test
    @DisplayName("A cache that is damaged is not used")
    void aDamagedCacheIsNotUsed() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));
        Path file = store.fileFor(KEY);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 30] ^= 0x7F;
        Files.write(file, bytes);

        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state(),
                    "A damaged cache must not be used");
            assertFalse(manager.isReady());
            assertTrue(manager.error().isPresent(), "The failure has to be reported");
        }
    }

    @Test
    @DisplayName("A disc image that is missing is reported")
    void aMissingDiscImageIsReported() {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state());
            assertTrue(manager.error().isPresent(), "The failure has to be reported");
            assertFalse(manager.isReady());
        }
    }

    @Test
    @DisplayName("A disc image that is not a disc of the game is reported")
    void aDiscImageThatIsNotADiscIsReported() throws IOException {
        Path image = tempDir.resolve("broken.iso");
        byte[] content = new byte[4096];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) index;
        }
        Files.write(image, content);

        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(image, KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state());
            assertTrue(manager.error().isPresent(), "The failure has to be reported");
        }
    }

    @Test
    @DisplayName("A failure can be tried again")
    void aFailureCanBeTriedAgain() {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);
            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state());
            long builds = manager.builds();

            manager.retry();
            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state());
            assertEquals(builds + 1, manager.builds(), "The cache has to be built again");
        }
    }

    @Test
    @DisplayName("The manager reports the step it is at")
    void theManagerReportsItsStep() {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertTrue(manager.progress() >= 0 && manager.progress() <= 100,
                    "The progress has to be a share in percent");
        }
    }

    @Test
    @DisplayName("A cache directory that cannot be used is reported and never used")
    void aCacheDirectoryThatCannotBeUsedIsReported() {
        Rac1CacheStore store = Rac1CacheStore.unusable("The cache directory cannot be used: "
                + "\"file.iso\" is a file and not a directory. Change the key \"directory\" in "
                + "racmc-cache.properties and start the game again.");
        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.FAILED, manager.state(),
                    "The game has to stay closed when the directory cannot be used");
            assertFalse(manager.isReady(), "No cache may be ready of a place that cannot be used");
            assertTrue(manager.content().isEmpty(), "No content may come from such a place");
            String error = manager.error().orElse("");
            assertTrue(error.contains("cannot be used"),
                    "The failure has to say what is wrong: " + error);
            assertTrue(error.contains("start the game again"),
                    "The failure has to say what to do: " + error);
        }
    }

    @Test
    @DisplayName("A cache that a run left half written is not used and the cache that was there is")
    void aHalfWrittenFileIsNotUsed() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));
        Path staging = store.fileFor(KEY)
                .resolveSibling(store.fileFor(KEY).getFileName().toString() + ".staging");
        byte[] file = Rac1CacheCodec.write(SyntheticAssets.content(KEY),
                Rac1CacheStore.SCHEMA_VERSION);
        Files.write(staging, java.util.Arrays.copyOf(file, file.length / 2));

        try (Rac1AssetCacheManager manager = new Rac1AssetCacheManager(store,
                new Rac1DiscValidator())) {
            manager.start(tempDir.resolve("missing.iso"), KEY);

            waitFor(manager);
            assertEquals(Rac1CacheState.READY, manager.state(),
                    "The cache that was there before has to be used");
            assertTrue(manager.content().isPresent());
            assertEquals(4, manager.content().get().staticAsset().mesh().vertexCount());
        }
    }

    /**
     * Waits until the manager is done.
     */
    private static void waitFor(Rac1AssetCacheManager manager) {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            Rac1CacheState state = manager.state();
            if (state == Rac1CacheState.READY || state == Rac1CacheState.FAILED) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("The manager did not finish in time.");
    }
}
