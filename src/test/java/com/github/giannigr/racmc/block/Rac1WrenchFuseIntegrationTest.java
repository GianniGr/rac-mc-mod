package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1WrenchFuseIntegrationTest {
    @Test void originalBoundsRoundUpAndTickOnlyOnce() {
        for (int source = 5; source <= 15; source++) {
            var fuse = new Rac1ExplosiveFuse();
            assertTrue(fuse.wrenchHit(source, 100));
            int ticks = (source + 2) / 3;
            assertEquals(ticks, fuse.remaining());
            assertFalse(fuse.tick(100));
            for (int tick = 1; tick <= ticks; tick++) {
                assertEquals(tick == ticks, fuse.tick(100 + tick));
                assertFalse(fuse.tick(100 + tick));
            }
            assertFalse(fuse.wrenchHit(5, 200));
            assertFalse(fuse.tick(201));
        }
    }
    @Test void rehitNeverExtendsAndMetadataRoundtrips() {
        var fuse = new Rac1ExplosiveFuse();
        fuse.arm(false, 10);
        assertTrue(fuse.wrenchHit(15, 11));
        assertFalse(fuse.wrenchHit(15, 12));
        assertEquals(11, fuse.lastTick());
        assertTrue(fuse.wrenchHit(5, 12));
        var tag = new net.minecraft.nbt.CompoundTag();
        fuse.save(tag);
        var restored = new Rac1ExplosiveFuse();
        restored.load(tag);
        assertEquals(2, restored.remaining());
        assertEquals(12, restored.lastTick());
        assertFalse(restored.tick(12));
        var chain = new Rac1ExplosiveFuse();
        chain.arm(true, 1);
        assertFalse(chain.wrenchHit(5, 2));
        assertEquals(1, chain.remaining());
        assertThrows(IllegalArgumentException.class, () -> chain.wrenchHit(4, 2));
        assertThrows(IllegalArgumentException.class, () -> chain.wrenchHit(16, 2));
    }
}
