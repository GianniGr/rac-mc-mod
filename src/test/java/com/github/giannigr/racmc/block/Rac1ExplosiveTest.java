package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.ModSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class Rac1ExplosiveTest {
    @Test
    void fullFuseIsSixtyLoadedTicksAndNeverRearms() {
        Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
        assertFalse(fuse.tick(100));
        assertTrue(fuse.arm(false, 100));
        assertFalse(fuse.tick(100));
        for (long time = 101; time < 160; time++) {
            assertFalse(fuse.arm(false, time));
            assertFalse(fuse.arm(true, time));
            assertFalse(fuse.tick(time));
            assertFalse(fuse.tick(time));
        }
        assertEquals(1, fuse.remaining());
        assertTrue(fuse.tick(160));
        assertEquals(Rac1ExplosiveFuse.TERMINAL, fuse.remaining());
        assertFalse(fuse.tick(161));
        assertFalse(fuse.arm(true, 161));
    }

    @Test
    void chainIsOneDeferredTickNotRecursionOrFullFuse() {
        Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
        assertTrue(fuse.arm(true, 10));
        assertEquals(1, fuse.remaining());
        assertFalse(fuse.tick(10));
        assertTrue(fuse.tick(11));
        assertFalse(fuse.tick(12));
    }

    @Test
    void transferGuardsSameTickAndUnloadDoesNotSpendOfflineTime() {
        Rac1ExplosiveFuse grounded = new Rac1ExplosiveFuse();
        grounded.arm(false, 0);
        grounded.tick(1);
        Rac1ExplosiveFuse falling = new Rac1ExplosiveFuse();
        falling.restore(grounded.remaining(), grounded.lastTick());
        assertFalse(falling.tick(1));
        assertEquals(59, falling.remaining());
        falling.tick(50000);
        assertEquals(58, falling.remaining());
        Rac1ExplosiveFuse landed = new Rac1ExplosiveFuse();
        landed.restore(falling.remaining(), falling.lastTick());
        assertFalse(landed.arm(false, 50000));
        assertFalse(landed.tick(50000));
        assertEquals(58, landed.remaining());
    }

    @Test
    void invalidStateFailsTerminalAndValidStatesRoundTrip() {
        Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
        for (int state : new int[]{-2, -1, 0, 1, 30, 60}) {
            fuse.restore(state, 77);
            assertEquals(state, fuse.remaining());
            assertEquals(77, fuse.lastTick());
        }
        for (int state : new int[]{Integer.MIN_VALUE, -3, 61, Integer.MAX_VALUE}) {
            fuse.restore(state, 77);
            assertEquals(Rac1ExplosiveFuse.TERMINAL, fuse.remaining());
            assertFalse(fuse.tick(78));
        }
    }

    @Test
    void flashUsesOriginalCounterPredicateAtTwentyTps() {
        List<Integer> red = new ArrayList<>();
        for (int elapsed = 0; elapsed < 60; elapsed++) {
            if (Rac1ExplosiveFuse.red(60 - elapsed)) {
                red.add(elapsed);
            }
        }
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 20, 21, 22, 23, 24, 25, 26,
                40, 41, 42, 43, 44, 45, 46, 55, 56, 57, 58, 59), red);
        assertFalse(Rac1ExplosiveFuse.red(-1));
        assertFalse(Rac1ExplosiveFuse.red(-2));
        assertFalse(Rac1ExplosiveFuse.red(0));
    }

    @Test
    void halfTntMeansHalfDamageNotHalfStrength() {
        assertEquals(28.5F, Rac1ExplosiveFuse.halfTntDamage(0, 1));
        assertEquals(11F, Rac1ExplosiveFuse.halfTntDamage(0.5, 1));
        assertEquals(0.5F, Rac1ExplosiveFuse.halfTntDamage(1, 1));
        assertEquals(0.5F, Rac1ExplosiveFuse.halfTntDamage(0, 0));
        assertEquals(8, Rac1CrateBlast.DAMAGE_RADIUS);
        assertEquals(3, Rac1CrateBlast.CRATE_RADIUS);
    }

    @Test
    void crateRangeUsesClosestPointOfShapeNotCenterAndHasNoWallRay() {
        assertTrue(Rac1CrateBlast.inCrateRange(Vec3.ZERO, new AABB(3, 0, 0, 4, 1, 1)));
        assertFalse(Rac1CrateBlast.inCrateRange(Vec3.ZERO, new AABB(3.001, 0, 0, 4, 1, 1)));
        assertFalse(Rac1CrateBlast.inCrateRange(Vec3.ZERO, new AABB(3, 3, 3, 4, 4, 4)));
        assertTrue(Rac1CrateBlast.inCrateRange(Vec3.ZERO, new AABB(-1, -1, -1, 1, 1, 1)));
    }

    @Test
    void blastSourceKeepsEventFinalAllowlistAndNoVanillaDestructionPass() {
        String source = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlast.java");
        assertTrue(source.indexOf("onExplosionStart") < source.indexOf("onExplosionDetonate"));
        assertTrue(source.indexOf("onExplosionDetonate") < source.indexOf("!destroyCrate"));
        assertTrue(source.contains("!originals.contains(entity)"));
        assertTrue(source.contains("entity instanceof ItemEntity"));
        assertTrue(source.contains("level.getBlockEntity(pos) != crate"));
        assertTrue(source.contains("!authorized.contains(pos)"));
        assertFalse(source.contains("finalizeExplosion("));
        assertFalse(source.contains(".explode("));
        assertFalse(source.contains("setBlockAndUpdate"));
        assertTrue(source.contains("false, Explosion.BlockInteraction.DESTROY"));
        assertTrue(source.indexOf("damage(level, context, center, living")
                < source.indexOf("for (Map.Entry<BlockPos"));
    }

    @Test
    void transferAndTrackingHaveExplicitPersistenceAndClientState() {
        String source = ModSource.of("com/github/giannigr/racmc/block/Rac1FallingExplosive.java");
        assertTrue(source.indexOf("crate.saveWithoutMetadata") < source.indexOf("level.setBlock"));
        assertTrue(source.contains("originY > pos.getY()"));
        assertTrue(source.contains("tag.putDouble(\"RacOriginY\""));
        assertTrue(source.contains("fuse.save(blockData)"));
        assertTrue(source.contains("entityData.set(FUSE"));
        assertTrue(source.contains("super.tick();"));
        assertTrue(source.contains("if (isRemoved())"));
    }

    @Test
    void contactOnlyAcceptsPhysicalLivingPlayerOrMob() {
        String source = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlast.java");
        assertTrue(source.contains("bounds.inflate(1.0E-6)"));
        assertTrue(source.contains("e instanceof Player || e instanceof Mob"));
        assertTrue(source.contains("!e.isSpectator()"));
        assertFalse(source.contains("inflate(1.0)"));
    }

    @Test
    void vanillaExplosionCallbacksArmInsteadOfRemovingExplosives() {
        String block = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlock.java");
        assertTrue(block.contains("protected void onExplosionHit("));
        assertTrue(block.contains("crate.arm(true)"));
        assertTrue(block.contains("TRIGGER_BLOCK"));
        String falling = ModSource.of("com/github/giannigr/racmc/block/Rac1FallingExplosive.java");
        assertTrue(falling.contains("public void onExplosionHit("));
    }

    @Test
    void sharedNbtCodecPreservesFuseTickAndUnrelatedComponentData() {
        Rac1ExplosiveFuse original = new Rac1ExplosiveFuse();
        original.arm(false, 50);
        original.tick(51);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("OtherComponent", "preserved");
        original.save(tag);
        Rac1ExplosiveFuse restored = new Rac1ExplosiveFuse();
        restored.load(tag.copy());
        assertEquals(59, restored.remaining());
        assertEquals(51, restored.lastTick());
        assertFalse(restored.tick(51));
        assertEquals("preserved", tag.getString("OtherComponent"));
        restored.load(new net.minecraft.nbt.CompoundTag());
        assertEquals(Rac1ExplosiveFuse.IDLE, restored.remaining());
        tag.putInt("RacFuse", 999);
        restored.load(tag);
        assertEquals(Rac1ExplosiveFuse.TERMINAL, restored.remaining());
        tag.putString("RacFuse", "invalid");
        restored.load(tag);
        assertEquals(Rac1ExplosiveFuse.TERMINAL, restored.remaining());
        assertFalse(restored.tick(52));
    }

    @Test
    void baselineCarrierMigrationPreservesSavedDataAndDoesNotConsumeOnSpawnVeto() {
        String source = ModSource.of("com/github/giannigr/racmc/block/Rac1FallingExplosive.java");
        assertTrue(source.contains("old.saveWithoutId(new CompoundTag())"));
        assertTrue(source.contains("entity.load(saved)"));
        assertTrue(source.indexOf("if (!level.addFreshEntity(entity))")
                < source.indexOf("old.discard()"));
        String hook = ModSource.of("com/github/giannigr/racmc/block/Rac1CrateFallEvents.java");
        assertTrue(hook.contains("event.isCanceled()"));
        assertTrue(hook.contains("falling instanceof Rac1FallingExplosive"));
        assertTrue(hook.contains("event.setCanceled(true)"));
    }
}
