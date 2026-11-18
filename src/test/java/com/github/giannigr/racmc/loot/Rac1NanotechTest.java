package com.github.giannigr.racmc.loot;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1NanotechTest {
    @Test void fixedFiveHealthClampsIncludingChangedMaximum() {
        assertEquals(15, Rac1NanotechRules.arrivalHealth(10, 20));
        assertEquals(20, Rac1NanotechRules.arrivalHealth(19, 20));
        assertEquals(20, Rac1NanotechRules.arrivalHealth(20, 20)); // full after lock still consumes
        assertEquals(9, Rac1NanotechRules.arrivalHealth(8, 9));
        assertEquals(35, Rac1NanotechRules.arrivalHealth(30, 100)); // not25% modified max
        assertThrows(IllegalArgumentException.class, () -> Rac1NanotechRules.arrivalHealth(0, 20));
    }
    @Test void fullDeadNonfiniteAndPendingAdmission() {
        assertFalse(Rac1NanotechRules.eligible(20, 20, 0));
        assertFalse(Rac1NanotechRules.eligible(0, 20, 0));
        assertFalse(Rac1NanotechRules.eligible(Float.NaN, 20, 0));
        assertFalse(Rac1NanotechRules.eligible(1, Float.POSITIVE_INFINITY, 0));
        assertTrue(Rac1NanotechRules.eligible(19, 20, 0));
        assertFalse(Rac1NanotechRules.eligible(15, 20, 1));
        assertTrue(Rac1NanotechRules.eligible(14.9f, 20, 1));
        assertFalse(Rac1NanotechRules.eligible(10, 20, 2));
    }
    @Test void strictRangeAndDeterministicEightDurations() {
        assertTrue(Rac1NanotechRules.inRange(9.999 * 9.999));
        assertFalse(Rac1NanotechRules.inRange(100));
        assertFalse(Rac1NanotechRules.inRange(Double.NaN));
        for (long seed = 0; seed < 1000; seed++) {
            int min = 80;
            for (int orb = 0; orb < 8; orb++) {
                int duration = Rac1NanotechRules.duration(seed, orb);
                assertTrue(duration >= 20 && duration <= 80);
                min = Math.min(min, duration);
            }
            assertEquals((min + 2) / 3, Rac1NanotechRules.firstArrival(seed));
        }
    }
    private Rac1NanotechLedger released(UUID source) {
        var ledger = new Rac1NanotechLedger();
        assertTrue(ledger.release(source, Level.OVERWORLD, new Vec3(1, 64, 2), 42));
        return ledger;
    }
    @Test void incarnationExactlyOnceAndNewPlacementIndependent() {
        UUID source = UUID.randomUUID();
        var ledger = released(source);
        for (int i = 0; i < 1000; i++)
            assertFalse(ledger.release(source, Level.OVERWORLD, new Vec3(1, 64, 2), i));
        assertEquals(1, ledger.pendingCount());
        assertTrue(ledger.release(UUID.randomUUID(), Level.OVERWORLD, new Vec3(1, 64, 2), 42));
        assertEquals(2, ledger.pendingCount());
    }
    @Test void saveLoadUnspawnedRetainsIdentitySeedPositionAndTombstone() {
        UUID source = UUID.randomUUID();
        var ledger = released(source);
        var saved = ledger.save(new CompoundTag(), null);
        var restored = Rac1NanotechLedger.load(saved, null);
        assertEquals(saved, restored.save(new CompoundTag(), null));
        assertFalse(restored.release(source, Level.OVERWORLD, new Vec3(1, 64, 2), 99));
    }
    @Test void gracefulFlightResetAndCommittedNoReplay() {
        var ledger = released(UUID.randomUUID());
        var saved = ledger.save(new CompoundTag(), null);
        UUID id = saved.getList("clusters", Tag.TAG_COMPOUND).getCompound(0).getUUID("id");
        var c = ledger.cluster(id);
        c.receiver = UUID.randomUUID(); c.age = 5; c.position = new Vec3(10, 90, 20);
        var restored = Rac1NanotechLedger.load(ledger.save(new CompoundTag(), null), null);
        assertNull(restored.cluster(id).receiver);
        assertEquals(new Vec3(1, 64, 2), restored.cluster(id).position);
        c.committed = true;
        restored = Rac1NanotechLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(0, restored.pendingCount());
        assertTrue(restored.released(c.source));
    }
    @Test void malformedOwnershipAndPositionsFailClosed() {
        var ledger = released(UUID.randomUUID());
        var saved = ledger.save(new CompoundTag(), null);
        var clusters = saved.getList("clusters", Tag.TAG_COMPOUND);
        clusters.add(clusters.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> Rac1NanotechLedger.load(saved, null));
        clusters.remove(1);
        clusters.getCompound(0).getCompound("p").putDouble("x", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> Rac1NanotechLedger.load(saved, null));
        assertFalse(new Rac1NanotechLedger().release(UUID.randomUUID(), Level.OVERWORLD,
                new Vec3(Double.NaN, 0, 0), 1));
    }
    @Test void invalidVelocityAndMissingTombstoneNeverRegenerate() {
        var saved = released(UUID.randomUUID()).save(new CompoundTag(), null);
        saved.getList("clusters", Tag.TAG_COMPOUND).getCompound(0).putDouble("velocity", -100);
        assertThrows(IllegalArgumentException.class, () -> Rac1NanotechLedger.load(saved, null));
        saved.getList("clusters", Tag.TAG_COMPOUND).getCompound(0).putDouble("velocity", 0);
        saved.put("released", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> Rac1NanotechLedger.load(saved, null));
    }
}
