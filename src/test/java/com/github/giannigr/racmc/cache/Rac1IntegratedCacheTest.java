package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Rac1IntegratedCacheTest {
    @TempDir Path directory;

    @Test
    void combinedSectionsRoundtripAndReload() throws Exception {
        var original = SyntheticAssets.completeContent("combined");
        var decoded = Rac1CacheCodec.read(
                Rac1CacheCodec.write(original, Rac1CacheStore.SCHEMA_VERSION),
                "combined", Rac1CacheStore.SCHEMA_VERSION);
        Rac1AssetCacheManager.requireAssets(decoded);
        assertNotEquals(Rac1CacheCodec.SECTION_VENDOR, Rac1CacheCodec.SECTION_HUD);
        assertNotNull(decoded.vendor());
        assertEquals(original.crates().keySet(), decoded.crates().keySet());
        var store = new Rac1CacheStore(directory);
        store.store("combined", decoded);
        for (int reload = 0; reload < 2; reload++) {
            try (var manager = new Rac1AssetCacheManager(store, new Rac1DiscValidator())) {
                manager.start(directory.resolve("missing.iso"), "combined");
                waitFor(manager);
                assertTrue(manager.isReady(), manager.error().orElse(""));
                Rac1AssetCacheManager.requireAssets(manager.content().orElseThrow());
            }
        }
    }

    @Test
    void everyMissingRequiredAssetRejectsCurrentCacheAndAttemptsRebuild() throws Exception {
        var complete = SyntheticAssets.completeContent("combined");
        var missing = new java.util.ArrayList<Rac1CacheContent>();
        missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                complete.blaster(), complete.handJoint(), complete.crates(), complete.hud()));
        missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                complete.blaster(), complete.handJoint(), complete.crates(), complete.vendor()));
        for (String key : complete.crates().keySet()) {
            var assets = new LinkedHashMap<>(complete.crates());
            assets.remove(key);
            missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                    complete.blaster(), complete.handJoint(), assets, complete.vendor(), complete.hud()));
        }
        var store = new Rac1CacheStore(directory);
        for (var candidate : missing) {
            assertThrows(java.io.IOException.class, () -> Rac1AssetCacheManager.requireAssets(candidate));
            store.store("combined", candidate); // structurally valid, current schema/converter
            try (var manager = new Rac1AssetCacheManager(store, new Rac1DiscValidator())) {
                manager.start(directory.resolve("missing.iso"), "combined");
                waitFor(manager);
                assertEquals(Rac1CacheState.FAILED, manager.state());
                assertFalse(manager.isReady());
                assertTrue(manager.content().isEmpty());
                assertTrue(manager.step().contains("disc image"), "Must attempt rebuild, not use partial cache");
            }
        }
    }

    private static void waitFor(Rac1AssetCacheManager manager) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (manager.state() != Rac1CacheState.READY && manager.state() != Rac1CacheState.FAILED) {
            if (System.nanoTime() > deadline) fail("Cache manager timed out");
            Thread.sleep(10);
        }
    }
}
