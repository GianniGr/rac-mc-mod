package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.asset.MobyClassReader;
import com.github.giannigr.racmc.client.shop.Rac1ShopEnvironmentState;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="racmc.isoImage", matches=".+")
class RealDiscShopEnvironmentMapsTest {
    @Test void matchesIndependentBothDiscRawGsOracles() throws Exception {
        var validator = new Rac1DiscValidator();
        try (var outcome = validator.inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported());
            String sha = validator.catalog().profiles().stream()
                    .filter(p -> p.revision()==outcome.result().revision()).findFirst().orElseThrow().imageSha1();
            var level = Rac1LevelAssets.load(outcome.image(), 1);
            var maps = Rac1ShopEnvironmentMaps.read(level, sha);
            assertEquals(sha, maps.discSha1());
            assertEquals(1, maps.sourceLevel());
            assertEquals(2, maps.images().size());
            // Independent bounded Python primary-disc probe, not the Java decoder.
            assertMap(maps.forSelector(-2), 128, 0x400, 0,
                    "f43715904618797684d7d747615704902ba9f95d0e6b474d203b9ea417552cb8");
            assertMap(maps.forSelector(-3), 64, 0x4800, 0x4400,
                    "b2af7c28391bfa61c9d7b4e954b0b1539fe4b210f200126145a819c9cedddace");
            int packets = 0, bindings = 0;
            for (int classId : new int[]{13,1143}) {
                var reader = MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(classId).offset()));
                for (var packet : reader.readMaterialMetadata().packets()) {
                    if (!packet.metal()) continue;
                    packets++;
                    for (var material : packet.materials()) {
                        assertEquals(classId==13 ? -3 : -2, material.textureSelector());
                        assertNotNull(maps.forSelector(material.textureSelector()));
                        long source = material.tex1();
                        assertEquals(4, source>>>32, "Source relocation filter input, not already a GS register");
                        long relocated = ((source>>>32)<<6) | 0x20 | ((source&0xffffffffL)<<32);
                        var state = new Rac1ShopEnvironmentState(0x8000000044L, 0x5360b, relocated, 5);
                        assertTrue(state.linearMinification()); assertFalse(state.mipmaps());
                        bindings++;
                    }
                }
            }
            // Independent resume6 primary oracle:13 has blocks[1,0],1143[1,0,0,0,0,0,0].
            assertEquals(9, packets); assertEquals(2, bindings);
            System.out.printf("%s original environment maps: chrome128/glass64 raw-GS RGBA pins pass%n",
                    outcome.result().revision());
        }
    }
    private static void assertMap(Rac1ShopEnvironmentMaps.Image map, int side, int pixels, int palette,
                                  String digest) throws Exception {
        assertEquals(side, map.role().side());
        assertEquals(pixels, map.pixelAddress()); assertEquals(palette, map.paletteAddress());
        byte[] rgba = map.rgbaGs();
        assertEquals(side*side*4, rgba.length);
        assertEquals(digest, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba)));
        int min = 255, max = 0;
        for (int p = 3; p < rgba.length; p += 4) { min = Math.min(min, rgba[p]&255); max = Math.max(max, rgba[p]&255); }
        assertEquals(13, min); assertEquals(128, max);
    }
}
