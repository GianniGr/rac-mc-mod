package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.*;
import com.github.giannigr.racmc.asset.shop.*;
import com.github.giannigr.racmc.asset.effect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopCacheTest {
    public static Rac1ShopAssets fixture() {
        var texture = new Rac1Texture(32, 32, new byte[32 * 32 * 4]);
        Map<String, List<Rac1Texture>> sprites = new TreeMap<>();
        for (String key : List.of("led_atlas", "cone", "glass", "noise", "scan_plane", "scan_bar")) {
            int size = key.equals("noise") ? 32 : key.equals("scan_bar") ? 16 : 64;
            sprites.put(key, List.of(new Rac1Texture(size, size, new byte[size * size * 4])));
        }
        sprites.put("blaster_icons", Collections.nCopies(5, texture));
        var atlas = new Rac1Texture(256, 128, new byte[256 * 128 * 4]);
        var font = new Rac1HudFont(atlas, new byte[928]);
        Map<Integer, byte[]> strings = new TreeMap<>();
        for (int id : Rac1ShopText.REQUIRED) strings.put(id, new byte[]{0x10, 'A'});
        strings.put(20321, new byte[]{'A'});
        int[] cells = new int[64], advances = new int[64], ticker = new int[24];
        Arrays.fill(cells, -1); Arrays.fill(advances, 9); Arrays.fill(ticker, 20321);
        var tables = new Rac1ShopSceneTables(new Rac1ShopLedFont(cells, advances), ticker,
                new float[24], new float[8], new float[20],1006,new int[4],.25f);
        return new Rac1ShopAssets("a".repeat(40), 0,
                new Rac1HudAssets(sprites, Map.of("small", font, "regular", font), Map.of()), new Rac1ShopText(strings), tables,
                new Rac1ShopModels(model(12, 10), model(13, 1), model(1137, 2),
                        new EffectMeshAsset(new EffectAssetRef("a".repeat(40), 1, EffectAssetRef.Bank.MOBY, 1006),
                                new Rac1Mesh(new float[]{0,0,0,1,0,0,0,1,0},new float[9],new float[6],
                                        new int[]{0,1,2},new int[]{0}), List.of(texture))), metadataFixture());
    }
    public static Map<Integer, MobyClassReader.ClassMaterialMetadata> metadataFixture() {
        Map<Integer, MobyClassReader.ClassMaterialMetadata> map = new TreeMap<>();
        for (int id : Rac1ShopAssets.MATERIAL_CLASSES) {
            var normal = new MobyClassReader.PacketMetadata(0,false,3,
                    new byte[]{0,1,2,3,4,5,6,7,8,9,10,11},
                    List.of(new MobyClassReader.PacketMaterial(id == 1137 ? -1 : 0,
                            0xfedcba9876543210L,0x8877665544332211L)));
            var packets = new ArrayList<MobyClassReader.PacketMetadata>(); packets.add(normal);
            if (id == 13) packets.add(new MobyClassReader.PacketMetadata(3,true,2,new byte[0],
                    List.of(new MobyClassReader.PacketMaterial(-3,0x1020304050607080L,0x9182736455463728L))));
            map.put(id,new MobyClassReader.ClassMaterialMetadata(0x89abcdef,0xabcd,255,packets));
        }
        return Map.copyOf(map);
    }
    private static Rac1AnimatedAsset model(int id, int count) {
        var source = SyntheticVendor.asset(id); var original = source.animations()[0];
        var clips = new Rac1Animation[count];
        for (int i = 0; i < count; i++) clips[i] = new Rac1Animation("sequence_" + i, original.times(), false,
                original.rotations(), new int[0], new int[0], new float[0], new int[0], new int[0], new float[0], new boolean[0]);
        return new Rac1AnimatedAsset(id, source.name(), source.mesh(), source.skeleton(), clips, source.textures());
    }
    @Test void roundTripChecksIdentityVersionsChecksumTruncationAndTrailingBytes() throws Exception {
        var assets = fixture(); byte[] encoded = Rac1ShopCacheCodec.write(assets);
        var read = Rac1ShopCacheCodec.read(encoded, assets.discSha1(), 0);
        assertTrue(read.missing2d().isEmpty());
        assertTrue(read.missingSourceTables().isEmpty());
        assertTrue(read.missingMaterialMetadata().isEmpty());
        assertArrayEquals(Rac1ShopMaterialCodec.write(assets.materialMetadata()),Rac1ShopMaterialCodec.write(read.materialMetadata()));
        assertEquals(assets.sceneTables().led(), read.sceneTables().led());
        assertArrayEquals(assets.text().get(21044), read.text().get(21044));
        assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.read(encoded, "b".repeat(40), 0));
        assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.read(encoded, assets.discSha1(), 2));
        for (int offset : new int[]{0, 7, 11, encoded.length / 2, encoded.length - 1}) {
            byte[] bad = encoded.clone(); bad[offset] ^= 1;
            assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.read(bad, assets.discSha1(), 0));
        }
        assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.read(Arrays.copyOf(encoded, encoded.length - 1), assets.discSha1(), 0));
        assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.read(Arrays.copyOf(encoded, encoded.length + 1), assets.discSha1(), 0));
    }
    @Test void missingResourcesFailRatherThanFallback() throws Exception {
        var complete = fixture();
        var missing = new Rac1ShopAssets(complete.discSha1(), 0, new Rac1HudAssets(Map.of(), Map.of(), Map.of()), complete.text());
        assertTrue(missing.missing2d().contains("font:small"));
        assertThrows(java.io.IOException.class, () -> Rac1ShopCacheCodec.write(missing));
    }
    @Test void storeUsesSeparateNamespaceAndPreservesLastGoodOnRejectedWrite(@TempDir Path root) throws Exception {
        var store = new Rac1ShopCacheStore(root); var assets = fixture();
        assertTrue(store.read(assets.discSha1(), 0).isEmpty());
        store.write(assets);
        Path path = store.path(assets.discSha1(), 0); byte[] good = Files.readAllBytes(path);
        assertTrue(path.toString().contains("shop"));
        assertEquals("5-5",path.getParent().getFileName().toString());
        assertTrue(store.read(assets.discSha1(), 0).isPresent());
        var bad = new Rac1ShopAssets(assets.discSha1(), 0, new Rac1HudAssets(Map.of(), Map.of(), Map.of()), assets.text());
        assertThrows(java.io.IOException.class, () -> store.write(bad));
        assertArrayEquals(good, Files.readAllBytes(path));
        assertThrows(IllegalArgumentException.class, () -> store.path("../bad", 0));
        assertThrows(IllegalArgumentException.class, () -> store.path(assets.discSha1(), 1));
    }
    @Test void previousNamespaceIsNeitherConsumedNorDeleted(@TempDir Path root) throws Exception {
        var store = new Rac1ShopCacheStore(root); var assets = fixture();
        Path old = root.resolve(assets.discSha1()).resolve("shop/4-4/language-0.racshop");
        Files.createDirectories(old.getParent()); byte[] retained={4,4,9}; Files.write(old,retained);
        assertTrue(store.read(assets.discSha1(),0).isEmpty());
        store.write(assets);
        assertArrayEquals(retained,Files.readAllBytes(old));
        assertTrue(store.read(assets.discSha1(),0).isPresent());
    }
}
