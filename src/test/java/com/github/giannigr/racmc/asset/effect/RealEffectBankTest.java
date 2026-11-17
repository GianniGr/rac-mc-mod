package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.cache.effect.Rac1EffectCacheCodec;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in read-only real banks; no original bytes/images/cache files are written by this test. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealEffectBankTest {
    @Test void levelOneBanksAndStaticEffectMeshesMatchAcceptedRegionalFacts() throws Exception {
        try (var inspection = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertNotNull(inspection.image());
            var image = inspection.image();
            String sha1 = image.revision() == Rac1DiscRevision.NTSC_U_V1_00
                    ? "aee9275384f739c9b24cd5de6f7e200292db7b01" : "ef304c641c7cd426ba492707dda5974679befbc0";
            var assets = Rac1LevelAssets.load(image, 1);
            var banks = Rac1EffectBankImporter.read(assets, sha1);
            var nano = Rac1NanotechImporter.read(image);
            assertEquals(870, nano.hemisphere().length);
            var groups = com.github.giannigr.racmc.effect.Rac1NanotechVisuals.geometry(nano,
                    new com.github.giannigr.racmc.effect.EffectSignal.Point(0, 0, 0),
                    new com.github.giannigr.racmc.effect.EffectSignal.Point(3, 2, 4), 30);
            assertEquals(3, groups.size());
            assertEquals(32 * 4, groups.get(1).vertices().size());
            assertEquals(4 * 4, groups.get(2).vertices().size());
            var blastTables = Rac1BlastTables.read(image);
            assertEquals(12, blastTables.debrisOffsets().length);
            assertEquals(6, blastTables.sparkA().length);
            assertEquals(41, banks.particles().entries().size());
            assertEquals(49, banks.fx().entries().size());
            assertEquals(81, banks.definitions().count());
            assertArrayEquals(new byte[]{32}, banks.definitions().storedSpan(26));
            assertArrayEquals(new byte[]{28}, banks.definitions().storedSpan(72));
            assertArrayEquals(new byte[]{33, 39}, banks.definitions().storedSpan(62));
            assertEquals(90, banks.particles().decodeAll().size() + banks.fx().decodeAll().size());
            assertPixelDigest(banks.particles(), 0, "588af23faf47e1587c9eb688560c284c7137e18c5548f536f9c5d8400790a24b");
            assertPixelDigest(banks.particles(), 28, "4a57bdd97253d9cec58de950076a9464579b2cdbfe51c8a3781c484475d41625");
            assertPixelDigest(banks.particles(), 32, "7d21aceea0b27f3b4bd2e9659f1cf9132994515ba4fac5d3a62bdad1ae36cadb");
            assertPixelDigest(banks.fx(), 11, "a6e3cd53fa4d9223b9dba7c8b7c79861773bae862c83d5a7e181ca96a73c1eda");
            assertPixelDigest(banks.fx(), 21, "7cd112130ae3c61264d36e9aaf05a3dc55a433072d99228fc9bb3ca1507e3790");
            var restored = Rac1EffectCacheCodec.read(Rac1EffectCacheCodec.write(banks), sha1, 1);
            assertArrayEquals(banks.fx().bytes(), restored.fx().bytes());
            for (int id : new int[]{112, 348, 349, 357, 358, 363, 364}) {
                var mesh = Rac1EffectMeshImporter.read(assets, sha1, id);
                assertTrue(mesh.mesh().indices().length > 0);
                assertFalse(mesh.textures().isEmpty());
            }
            assertThrows(com.github.giannigr.racmc.asset.AssetFormatException.class,
                    () -> Rac1EffectMeshImporter.read(assets, sha1, 305));
            assertThrows(com.github.giannigr.racmc.asset.AssetFormatException.class,
                    () -> Rac1EffectMeshImporter.read(assets, sha1, 806));
        }
    }
    private static void assertPixelDigest(EffectTextureBank bank, int id, String expected) throws Exception {
        var entry = bank.entries().get(id);
        byte[] pixels = Arrays.copyOfRange(bank.bytes(), entry.pixelOffset(),
                entry.pixelOffset() + entry.width() * entry.height());
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels)));
    }
}
