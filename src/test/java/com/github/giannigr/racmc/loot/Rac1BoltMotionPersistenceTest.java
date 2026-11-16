package com.github.giannigr.racmc.loot;

import java.util.*;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltMotionPersistenceTest {
    private static final Collision AIR = (p, v, r) -> new Movement(p.add(v), List.of());
    private static State frame(State s, Hero hero) {
        return Rac1BoltMotion.step(s, 1, AIR, hero, () -> .5).state();
    }
    private static CompoundTag ledgerInput(UUID id, State s) {
        CompoundTag root = new CompoundTag(), pickup = new CompoundTag();
        ListTag list = new ListTag();
        pickup.putUUID("id", id); pickup.putString("dimension", "minecraft:overworld");
        pickup.putInt("value", 1); pickup.putDouble("x", s.position().x());
        pickup.putDouble("y", s.position().y()); pickup.putDouble("z", s.position().z());
        pickup.put("motion", Rac1BoltMotionTag.save(s)); list.add(pickup); root.put("pickups", list);
        return root;
    }
    @Test void everyPhaseResumesExactContinuationWithoutRerollOrDuplicatedValue() {
        Hero hero = new Hero(UUID.randomUUID(), V.ZERO, new V(2, 1, 0));
        State launch = Rac1BoltMotion.launch(V.ZERO, new Random(26));
        State flight = frame(frame(launch, null), null);
        Collision floor = (p, v, r) -> new Movement(p.add(v), List.of(new Contact(V.ZERO, V.UP, V.UP)));
        State settle = Rac1BoltMotion.step(flight, 1, floor, null, () -> .5).state();
        State idle = Rac1BoltMotion.legacy(new V(.5, 0, 0));
        State attraction = frame(idle, hero);
        for (State state : List.of(flight, settle, idle, attraction)) {
            UUID id = UUID.randomUUID();
            var original = Rac1BoltLedger.load(ledgerInput(id, state), null);
            var serialized = original.save(new CompoundTag(), null);
            var restored = Rac1BoltLedger.load(serialized, null);
            assertEquals(serialized, restored.save(new CompoundTag(), null));
            assertEquals(state, restored.pickup(id).motion());
            assertEquals(1, serialized.getList("pickups", Tag.TAG_COMPOUND).size());
            State a = state, b = restored.pickup(id).motion();
            for (int i = 0; i < 60; i++) {
                a = frame(a, hero); b = frame(b, hero);
                assertEquals(a, b);
            }
            restored.move(id, b);
            assertEquals(b, Rac1BoltLedger.load(restored.save(new CompoundTag(), null), null).pickup(id).motion());
            assertEquals(1, restored.pickup(id).value());
            UUID absent = UUID.randomUUID();
            restored.move(absent, b);
            assertNull(restored.pickup(absent)); // motion updates cannot resurrect a consumed UUID
        }
    }
    @Test void legacyRecordsAreNotAssignedANewRandomLaunch() {
        V position = new V(1, 2, 3);
        State a = Rac1BoltMotion.legacy(position), b = Rac1BoltMotion.legacy(position);
        assertEquals(a, b); assertEquals(Phase.IDLE, a.phase()); assertEquals(V.ZERO, a.velocity());
    }
    @Test void stackOriginIsBottomCenterAndNotDoubleHalfBlock() {
        State lower = Rac1BoltMotion.launch(new V(4.5, 10, 6.5), new Random(26));
        State upper = Rac1BoltMotion.launch(new V(4.5, 13, 6.5), new Random(26));
        assertEquals(10.5, lower.position().y());
        assertEquals(13.5, upper.position().y());
        for (int i = 0; i < 60; i++) {
            lower = frame(lower, null); upper = frame(upper, null);
            assertEquals(3, upper.position().y() - lower.position().y(), 1e-12);
            assertEquals(lower.velocity(), upper.velocity());
        }
    }
    @Test void rejectedAdmissionUnloadedChunksAndDuplicateViewsDoNotLoseOrRerollValue() {
        UUID id = UUID.randomUUID();
        State launch = Rac1BoltMotion.launch(new V(4.5, 10, 6.5), new Random(26));
        var ledger = Rac1BoltLedger.load(ledgerInput(id, launch), null);
        CompoundTag before = ledger.save(new CompoundTag(), null);
        List<Rac1BoltLedger.Pickup> attempts = new ArrayList<>();
        ledger.emitPending(Level.OVERWORLD, p -> false, p -> false, p -> {
            fail("Unloaded chunk must not attempt admission"); return false;
        });
        ledger.emitPending(Level.NETHER, p -> true, p -> false, p -> {
            fail("Wrong dimension must not attempt admission"); return false;
        });
        ledger.emitPending(Level.OVERWORLD, p -> true, p -> false, p -> { attempts.add(p); return false; });
        assertEquals(before, ledger.save(new CompoundTag(), null));
        var reloaded = Rac1BoltLedger.load(before, null);
        reloaded.emitPending(Level.OVERWORLD, p -> true, p -> false, p -> { attempts.add(p); return true; });
        assertEquals(2, attempts.size());
        assertEquals(attempts.getFirst(), attempts.getLast());
        assertEquals(launch, attempts.getLast().motion());
        reloaded.emitPending(Level.OVERWORLD, p -> true, p -> true, p -> {
            fail("Existing UUID view must not be admitted twice"); return false;
        });
        assertEquals(before, reloaded.save(new CompoundTag(), null));
    }
}
