package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.block.Rac1ExplosiveFuse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangedFuseTest {
    @Test void bombFuseAttributionPersistsAndCannotBeReplacedOrShortened() {
        var owner=java.util.UUID.randomUUID();
        var fuse=new Rac1ExplosiveFuse();
        assertTrue(fuse.armFromBomb(owner,100));
        assertEquals(60,fuse.remaining());
        assertFalse(fuse.arm(true,101));
        assertFalse(fuse.armFromBomb(java.util.UUID.randomUUID(),101));
        assertFalse(fuse.blasterHit(() -> { fail("Attributed fuse must not draw RNG"); return 5; },101));
        assertFalse(fuse.wrenchHit(5,101));
        var tag=new net.minecraft.nbt.CompoundTag(); fuse.save(tag);
        var transferred=new Rac1ExplosiveFuse(); transferred.load(tag);
        assertEquals(owner,transferred.bombOwner());
        assertFalse(transferred.tick(100));
        for (int tick=101;tick<160;tick++) assertFalse(transferred.tick(tick));
        assertTrue(transferred.tick(160)); assertEquals(owner,transferred.bombOwner());
        assertFalse(transferred.tick(161));
        tag.putString("RacBombFuseOwner","invalid");
        transferred.load(tag);
        assertEquals(Rac1ExplosiveFuse.TERMINAL,transferred.remaining());
        assertFalse(transferred.arm(true,161));
    }
    @Test void idleAndLongBecomeShortButShortTerminalAndChainNeverReroll() {
        var fuse = new Rac1ExplosiveFuse(); int[] random = {0};
        java.util.function.IntSupplier draw = () -> { random[0]++; return 15; };
        assertTrue(fuse.blasterHit(draw, 100));
        assertEquals(5, fuse.remaining()); assertEquals(1, random[0]);
        assertFalse(fuse.blasterHit(() -> { fail("Already short must not even draw RNG"); return 5; }, 101));
        assertEquals(5, fuse.remaining()); assertEquals(100, fuse.lastTick());
        fuse.restore(60, 99);
        assertTrue(fuse.blasterHit(draw, 100));
        assertEquals(5, fuse.remaining()); assertEquals(2, random[0]);
        for (int remaining : new int[]{-2, 0, 1, 2, 3, 4, 5}) {
            fuse.restore(remaining, 100);
            assertFalse(fuse.blasterHit(() -> { fail("Short/terminal rerolled"); return 5; }, 101));
            assertEquals(remaining, fuse.remaining());
        }
    }
    @Test void overlayTransferPreservesIncarnationClockAndNoDuplicateTickOrReroll() {
        var airborne = new Rac1ExplosiveFuse();
        assertTrue(airborne.blasterHit(() -> 9, 100));
        var tag = new net.minecraft.nbt.CompoundTag(); airborne.save(tag);
        var grounded = new Rac1ExplosiveFuse(); grounded.load(tag);
        assertFalse(grounded.tick(100));
        assertEquals(3, grounded.remaining());
        assertFalse(grounded.blasterHit(() -> { fail("Landing/contact duplicate rerolled"); return 5; }, 100));
        assertFalse(grounded.tick(101));
        assertEquals(2, grounded.remaining());
        assertFalse(grounded.tick(102));
        assertTrue(grounded.tick(103));
        assertFalse(grounded.tick(103));
        assertFalse(grounded.blasterHit(() -> 15, 104));
    }
}
