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
        assertEquals(71, decoded.wrench().classNumber());
        assertEquals(7, Rac1CacheStore.SCHEMA_VERSION);
        assertEquals(7, Rac1CacheCodec.CONVERTER_VERSION);
        assertEquals(7, java.util.Set.of(Rac1CacheCodec.SECTION_STATIC,
                Rac1CacheCodec.SECTION_ANIMATED, Rac1CacheCodec.SECTION_BLASTER,
                Rac1CacheCodec.SECTION_CRATES, Rac1CacheCodec.SECTION_VENDOR,
                Rac1CacheCodec.SECTION_HUD, Rac1CacheCodec.SECTION_WRENCH).size());
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
                complete.blaster(), complete.handJoint(), complete.crates(), null,
                complete.hud(), complete.wrench()));
        missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                complete.blaster(), complete.handJoint(), complete.crates(), complete.vendor(),
                com.github.giannigr.racmc.asset.hud.Rac1HudAssets.empty(), complete.wrench()));
        missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                complete.blaster(), complete.handJoint(), complete.crates(), complete.vendor(),
                complete.hud()));
        for (String key : complete.crates().keySet()) {
            var assets = new LinkedHashMap<>(complete.crates());
            assets.remove(key);
            missing.add(new Rac1CacheContent("combined", complete.staticAsset(), complete.animatedAsset(),
                    complete.blaster(), complete.handJoint(), assets, complete.vendor(), complete.hud(),
                    complete.wrench()));
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

    @Test
    void meshSoundAndEffectSidecarsCoexistAndReloadIndependently() throws Exception {
        String hash = com.github.giannigr.racmc.asset.effect.EffectBankTest.SHA1;
        var mesh = new Rac1CacheStore(directory);
        mesh.store("combined", SyntheticAssets.completeContent("combined"));
        var clips = new java.util.EnumMap<com.github.giannigr.racmc.sound.Rac1AudioEvent,
                com.github.giannigr.racmc.sound.Rac1AudioCatalog.Clip>(
                        com.github.giannigr.racmc.sound.Rac1AudioEvent.class);
        for (var event : com.github.giannigr.racmc.sound.Rac1AudioEvent.values()) {
            if (!event.importsSample()) continue;
            clips.put(event, new com.github.giannigr.racmc.sound.Rac1AudioCatalog.Clip(
                    new byte[56], 22050, new byte[32], new byte[12], new byte[40],
                    "a".repeat(64), "b".repeat(64), 1, -1, 28, false));
        }
        var sound = new com.github.giannigr.racmc.sound.Rac1SoundCache(directory);
        sound.write(hash, new com.github.giannigr.racmc.sound.Rac1AudioCatalog(clips));
        var effects = new com.github.giannigr.racmc.cache.effect.Rac1EffectCacheStore(directory);
        effects.write(com.github.giannigr.racmc.asset.effect.EffectBankTest.synthetic());
        for (int reload = 0; reload < 2; reload++) {
            Rac1AssetCacheManager.requireAssets(mesh.load("combined").orElseThrow());
            assertEquals(clips.size(), sound.read(hash).orElseThrow().clips().size());
            assertTrue(effects.read(hash, 1).isPresent());
        }
        // A corrupt optional sidecar must not corrupt or overwrite the other two namespaces.
        java.nio.file.Files.write(effects.path(hash, 1), new byte[16]);
        assertThrows(java.io.IOException.class, () -> effects.read(hash, 1));
        Rac1AssetCacheManager.requireAssets(mesh.load("combined").orElseThrow());
        assertTrue(sound.read(hash).isPresent());
    }

    @Test
    void mergedPayloadIdentitiesAreDistinct() {
        assertEquals(6, java.util.Set.of(
                com.github.giannigr.racmc.actions.WrenchActionNetwork.Intent.TYPE.id(),
                com.github.giannigr.racmc.actions.WrenchActionNetwork.Snapshot.TYPE.id(),
                com.github.giannigr.racmc.loot.Rac1BoltNetwork.Balance.TYPE.id(),
                com.github.giannigr.racmc.block.Rac1CrateKnockbackPayload.TYPE.id(),
                com.github.giannigr.racmc.sound.Rac1AudioSignal.TYPE.id(),
                com.github.giannigr.racmc.effect.Rac1EffectNetwork.Packet.TYPE.id()).size());
    }
}
