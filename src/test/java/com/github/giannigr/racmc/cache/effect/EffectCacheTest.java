package com.github.giannigr.racmc.cache.effect;

import com.github.giannigr.racmc.asset.effect.EffectBankTest;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EffectCacheTest {
    @TempDir Path temporary;
    @Test void independentSidecarRoundTrip() throws Exception {
        var banks = EffectBankTest.synthetic();
        byte[] file = Rac1EffectCacheCodec.write(banks);
        assertEquals("RACEFX", new String(file, 0, 6, java.nio.charset.StandardCharsets.US_ASCII));
        var decoded = Rac1EffectCacheCodec.read(file, EffectBankTest.SHA1, 1);
        assertArrayEquals(banks.particles().bytes(), decoded.particles().bytes());
        assertArrayEquals(banks.definitions().bytes(), decoded.definitions().bytes());
    }
    @Test void corruptTruncatedAndWrongSourcesFail() throws Exception {
        byte[] file = Rac1EffectCacheCodec.write(EffectBankTest.synthetic());
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(file, "0".repeat(40), 1));
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(file, EffectBankTest.SHA1, 2));
        file[file.length - 1] ^= 1;
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(file, EffectBankTest.SHA1, 1));
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(new byte[16], EffectBankTest.SHA1, 1));
    }
    @Test void rechecksContentEvenWithRecomputedDigest() throws Exception {
        byte[] file = Rac1EffectCacheCodec.write(EffectBankTest.synthetic());
        file[11] = 99; // schema
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.util.Arrays.copyOf(file, file.length - 32));
        System.arraycopy(digest, 0, file, file.length - 32, 32);
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(file, EffectBankTest.SHA1, 1));
    }
    @Test void storeNeverTouchesSharedMeshOrAudioFiles() throws Exception {
        Path mesh = temporary.resolve("fixture.racm"), sound = temporary.resolve("fixture.racsnd");
        Files.writeString(mesh, "mesh sentinel");
        Files.writeString(sound, "audio sentinel");
        var store = new Rac1EffectCacheStore(temporary);
        assertTrue(store.read(EffectBankTest.SHA1, 1).isEmpty());
        store.write(EffectBankTest.synthetic());
        assertTrue(store.read(EffectBankTest.SHA1, 1).isPresent());
        assertEquals("mesh sentinel", Files.readString(mesh));
        assertEquals("audio sentinel", Files.readString(sound));
        try (var paths = Files.walk(temporary)) {
            assertEquals(0, paths.filter(p -> p.getFileName().toString().startsWith("effect-staging-")).count());
        }
    }
    @Test void traversalAndBadProfilesRejected() {
        var store = new Rac1EffectCacheStore(temporary);
        assertThrows(IllegalArgumentException.class, () -> store.path("../outside", 1));
        assertThrows(IllegalArgumentException.class, () -> store.path(EffectBankTest.SHA1, -1));
    }
    @Test void attackerRehashedBankOffsetsAndTrailingDataAreStillRefused() throws Exception {
        byte[] file = Rac1EffectCacheCodec.write(EffectBankTest.synthetic());
        // First bank entry palette offset, after magic/version/source/namespace/count.
        java.nio.ByteBuffer.wrap(file).putInt(80, Integer.MAX_VALUE);
        rehash(file);
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(file, EffectBankTest.SHA1, 1));
        byte[] valid = Rac1EffectCacheCodec.write(EffectBankTest.synthetic());
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        rehash(trailing);
        assertThrows(IOException.class, () -> Rac1EffectCacheCodec.read(trailing, EffectBankTest.SHA1, 1));
    }
    private static void rehash(byte[] file) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.util.Arrays.copyOf(file, file.length - 32));
        System.arraycopy(digest, 0, file, file.length - 32, 32);
    }
}
