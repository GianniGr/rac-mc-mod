package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused checks for the place where the cache of the assets is kept.
 *
 * <p>The checks use assets that were built by hand. No data of the game is used anywhere.
 */
class Rac1CacheStoreTest {

    /** The identity of the disc the checks use. */
    private static final String KEY = "NTSC-U v1.00|SCUS_971.99|test";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A cache that was written can be read back")
    void aCacheCanBeReadBack() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);

        store.store(KEY, SyntheticAssets.content(KEY));
        Optional<Rac1CacheContent> content = store.load(KEY);

        assertTrue(content.isPresent(), "The cache has to be there");
        assertEquals(KEY, content.get().discKey());
        assertEquals(4, content.get().staticAsset().mesh().vertexCount());
    }

    @Test
    @DisplayName("There is no cache before one was written")
    void thereIsNoCacheBeforeOneWasWritten() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);

        assertTrue(store.load(KEY).isEmpty(), "There must not be a cache before one was written");
    }

    @Test
    @DisplayName("A cache that was written leaves no file of a half written cache behind")
    void aWrittenCacheLeavesNoStagingFileBehind() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);

        store.store(KEY, SyntheticAssets.content(KEY));

        try (var files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".staging")),
                    "The file of a half written cache has to be gone");
        }
    }

    @Test
    @DisplayName("A cache whose bytes were changed is refused")
    void aDamagedCacheIsRefused() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));
        Path file = store.fileFor(KEY);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 20] ^= 0x33;
        Files.write(file, bytes);

        assertThrows(IOException.class, () -> store.load(KEY));
    }

    @Test
    @DisplayName("A cache of another disc is not used")
    void aCacheOfAnotherDiscIsNotUsed() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));

        assertTrue(store.load("PAL v2.00|SCES_509.16|other").isEmpty(),
                "The cache of another disc must not be used");
    }

    @Test
    @DisplayName("A cache can be thrown away")
    void aCacheCanBeThrownAway() throws IOException {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);
        store.store(KEY, SyntheticAssets.content(KEY));

        store.discard(KEY);

        assertFalse(Files.exists(store.fileFor(KEY)), "The cache has to be gone");
        assertTrue(store.load(KEY).isEmpty());
    }

    @Test
    @DisplayName("Every disc gets a file of its own")
    void everyDiscGetsAFileOfItsOwn() {
        Rac1CacheStore store = new Rac1CacheStore(tempDir);

        assertFalse(store.fileFor("one").equals(store.fileFor("other")),
                "Two discs must not share a file");
        assertTrue(store.fileFor("one").getFileName().toString().endsWith(".racm"));
    }
}
