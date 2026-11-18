package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Rac1ItemTextureLeaseTest {
    private static Rac1Texture[] textures(int count) {
        Rac1Texture[] textures = new Rac1Texture[count];
        java.util.Arrays.setAll(textures, i -> new Rac1Texture(1, 1, new byte[4]));
        return textures;
    }

    @Test
    void reuseReimportReloadAndMissingCacheReleaseExactlyOnce() {
        var serial = new AtomicInteger();
        var released = new ArrayList<Integer>();
        var lease = new Rac1ItemTextureLease<Integer>((i, t) -> serial.incrementAndGet(),
                released::add);
        Object first = new Object();
        assertEquals(List.of(1, 2), lease.get(first, 0, textures(2)));
        assertEquals(List.of(1, 2), lease.get(first, 0, textures(2)));
        assertTrue(released.isEmpty());
        Object second = new Object();
        assertEquals(List.of(3), lease.get(second, 0, textures(1)));
        assertEquals(List.of(1, 2), released);
        assertEquals(List.of(4), lease.get(second, 1, textures(1)));
        assertEquals(List.of(1, 2, 3), released);
        assertTrue(lease.get(null, 1, textures(0)).isEmpty());
        lease.clear();
        assertEquals(List.of(1, 2, 3, 4), released);
    }

    @Test
    void partialRegistrationFailureReleasesAndCanRetry() {
        var released = new ArrayList<Integer>();
        var attempts = new AtomicInteger();
        var lease = new Rac1ItemTextureLease<Integer>((i, t) -> {
            if (attempts.getAndIncrement() == 1) {
                throw new IllegalStateException("synthetic failure");
            }
            return i;
        }, released::add);
        Object content = new Object();
        assertThrows(IllegalStateException.class, () -> lease.get(content, 0, textures(2)));
        assertEquals(List.of(0), released);
        assertEquals(List.of(0, 1), lease.get(content, 0, textures(2)));
        lease.clear();
        assertEquals(List.of(0, 0, 1), released);
    }
}
