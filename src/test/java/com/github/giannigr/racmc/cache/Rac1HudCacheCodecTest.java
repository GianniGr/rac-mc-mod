package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.hud.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1HudCacheCodecTest {
    @Test void complete2DSectionAndOuterCacheRoundtrip() throws Exception {
        var original = SyntheticHud.assets();
        byte[] section = Rac1HudCacheCodec.write(original);
        var decoded = Rac1HudCacheCodec.read(section);
        assertTrue(Rac1BoltHudDefinition.complete(decoded));
        assertArrayEquals(section, Rac1HudCacheCodec.write(decoded));
        var content = SyntheticAssets.completeContent("test");
        byte[] file = Rac1CacheCodec.write(content, Rac1CacheStore.SCHEMA_VERSION);
        var read = Rac1CacheCodec.read(file, "test", Rac1CacheStore.SCHEMA_VERSION);
        assertArrayEquals(section, Rac1HudCacheCodec.write(read.hud()));
    }
    @Test void truncationBadVersionOversizedCountsAndTrailingBytesRejected() throws Exception {
        byte[] bytes = Rac1HudCacheCodec.write(SyntheticHud.assets());
        for (int size : new int[]{0, 4, 7, 20, bytes.length - 1})
            assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(Arrays.copyOf(bytes, size)));
        byte[] badVersion = bytes.clone(); badVersion[3] = 2;
        assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(badVersion));
        byte[] count = bytes.clone(); Arrays.fill(count, 4, 8, (byte) 127);
        assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(count));
        assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(new byte[33 * 1024 * 1024]));
    }
    @Test void missingHudAndOldSchemaAreNotComplete() throws Exception {
        assertFalse(Rac1BoltHudDefinition.complete(Rac1HudAssets.empty()));
        var full = SyntheticHud.assets();
        assertFalse(Rac1BoltHudDefinition.complete(new Rac1HudAssets(
                Map.of("bolt", full.sprites().get("bolt").subList(0, 29)), full.fonts(), full.properties())));
        byte[] oldSchema = Rac1CacheCodec.write(SyntheticAssets.completeContent("test"), 4);
        assertThrows(IOException.class, () -> Rac1CacheCodec.read(oldSchema, "test", Rac1CacheStore.SCHEMA_VERSION));
    }
    @Test void oversizedTextureAndBadFontUvRejectedBeforeRendering() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var out = new java.io.DataOutputStream(bytes);
        out.writeInt(1); out.writeInt(1); out.writeUTF("x"); out.writeInt(1);
        out.writeInt(Integer.MAX_VALUE); out.writeInt(1);
        assertThrows(IOException.class, () -> Rac1HudCacheCodec.read(bytes.toByteArray()));
        var original = SyntheticHud.assets().fonts().get("regular");
        byte[] metrics = original.metrics(); metrics['0' * 4] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> new Rac1HudFont(original.atlas(), metrics));
        assertThrows(IllegalArgumentException.class, () -> new Rac1HudFont(original.atlas(), new byte[4]));
    }
}
