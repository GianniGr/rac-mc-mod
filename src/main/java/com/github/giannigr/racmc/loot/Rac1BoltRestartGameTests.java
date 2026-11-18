package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.*;

/**
 * Opt-in, two-process restart regression. Separate namespace and explicit private marker/stage
 * properties prevent ordinary GameTest runs from writing files or assuming a previous run.
 */
@GameTestHolder("racmc_restart")
@PrefixGameTestTemplate(false)
public final class Rac1BoltRestartGameTests {
    private static final Collision AIR = (p, v, r) -> new Movement(p.add(v), List.of());
    private static final List<ChunkPos> FORCED = new ArrayList<>();
    private static Consumer<EntityJoinLevelEvent> admissionProbe;
    private static Consumer<EntityTickEvent.Post> tickProbe;
    private Rac1BoltRestartGameTests() {}

    @AfterBatch(batch = "boltRestart")
    public static void cleanup(ServerLevel level) {
        if (admissionProbe != null) {
            NeoForge.EVENT_BUS.unregister(admissionProbe);
            admissionProbe = null;
        }
        if (tickProbe != null) {
            NeoForge.EVENT_BUS.unregister(tickProbe);
            tickProbe = null;
        }
        for (ChunkPos chunk : FORCED) level.setChunkForced(chunk.x, chunk.z, false);
        FORCED.clear();
    }

    @GameTest(template = "empty", batch = "boltRestart", timeoutTicks = 20000)
    public static void normalRestartReconstructsOneViewAndContinuesAllFourPhases(GameTestHelper helper) throws Exception {
        String stage = System.getProperty("racmc.boltRestartStage", "");
        String markerPath = System.getProperty("racmc.boltRestartMarker", "");
        helper.assertTrue(!markerPath.isBlank() && (stage.equals("write") || stage.equals("read")),
                "Explicit boltRestartMarker and boltRestartStage=write/read are required");
        Path marker = Path.of(markerPath);
        var level = helper.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        if (stage.equals("write")) {
            helper.assertTrue(!Files.exists(marker), "Use a fresh marker; never overwrite restart evidence");
            helper.assertTrue(total(ledger, level) == 0, "Writer requires its own fresh isolated world");
            ListTag saved = new ListTag();
            for (Phase phase : Phase.values()) {
                Set<UUID> before = ids(ledger, level);
                ledger.reward(UUID.randomUUID(), level, helper.absolutePos(new BlockPos(2, 10, 2)), 1);
                Set<UUID> created = ids(ledger, level);
                created.removeAll(before);
                helper.assertTrue(created.size() == 1, "Writer did not create exactly one pending value");
                UUID id = created.iterator().next();
                V p = new V(1000000.5 + phase.ordinal() * 64, 120, 1000000.5);
                helper.assertTrue(!level.hasChunkAt(BlockPos.containing(p.x(), p.y(), p.z())),
                        "Writer's distant chunk must remain unloaded");
                State s = new State(p, new V(.02, -.03, .01), phase, p.y() + 1, p, V.UP,
                        36, 18, 36, 12, .4, .2, 1, .05,
                        phase == Phase.ATTRACTING ? UUID.randomUUID() : null);
                ledger.move(id, s);
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", id); entry.putInt("value", 1);
                entry.putDouble("x", p.x()); entry.putDouble("y", p.y()); entry.putDouble("z", p.z());
                entry.put("motion", Rac1BoltMotionTag.save(s)); saved.add(entry);
            }
            helper.assertTrue(total(ledger, level) == 4, "Writer changed remaining value");
            CompoundTag root = new CompoundTag();
            root.put("states", saved);
            NbtIo.writeCompressed(root, marker);
            System.out.println("BOLT RESTART WRITE: four exact phases pending in unloaded chunks; normal shutdown next");
            helper.succeed();
            return;
        }

        helper.assertTrue(Files.exists(marker), "Reader requires marker from completed normal writer shutdown");
        CompoundTag root = NbtIo.readCompressed(marker, NbtAccounter.unlimitedHeap());
        Map<UUID, State> expected = new LinkedHashMap<>();
        for (Tag tag : root.getList("states", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            UUID id = entry.getUUID("id");
            var pickup = ledger.pickup(id);
            helper.assertTrue(pickup != null && pickup.value() == entry.getInt("value"), "Disk reload lost value/UUID");
            V p = new V(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"));
            State s = Rac1BoltMotionTag.load(entry.getCompound("motion"), p);
            helper.assertTrue(s.equals(pickup.motion()), "Disk reload changed phase/position/velocity/counters/target");
            expected.put(id, s);
        }
        helper.assertTrue(expected.size() == 4 && total(ledger, level) == 4, "Reader lost/duplicated remaining value");

        Map<UUID, Rac1BoltEntity> firstAdmission = new HashMap<>();
        Map<UUID, Integer> attempts = new HashMap<>(), observedTicks = new HashMap<>();
        List<String> admissionErrors = new ArrayList<>();
        admissionProbe = event -> {
            State s = expected.get(event.getEntity().getUUID());
            if (event.getLevel() != level || s == null || !(event.getEntity() instanceof Rac1BoltEntity bolt)) return;
            attempts.merge(bolt.getUUID(), 1, Integer::sum);
            firstAdmission.putIfAbsent(bolt.getUUID(), bolt);
            V p = s.position(), v = s.velocity();
            if (bolt.position().distanceToSqr(p.x(), p.y(), p.z()) != 0
                    || bolt.getDeltaMovement().distanceToSqr(v.x() * 3, v.y() * 3, v.z() * 3) != 0
                    || !s.equals(ledger.pickup(bolt.getUUID()).motion())) {
                admissionErrors.add("Admission rerolled saved motion for " + bolt.getUUID());
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, admissionProbe);
        // Count actual automatic tick invocations, not Entity.tickCount. The latter is not a
        // lifecycle-hook count (the engine and the candidate entity both increment it).
        tickProbe = event -> {
            UUID id = event.getEntity().getUUID();
            State s = expected.get(id);
            if (event.getEntity().level() != level || s == null) return;
            for (int frame = 0; frame < Rac1BoltMotion.SUBSTEPS; frame++) {
                s = Rac1BoltMotion.step(s, 1, AIR, null, () -> {
                    throw new AssertionError("Restart must not reroll this no-player fixture");
                }).state();
            }
            expected.put(id, s);
            observedTicks.merge(id, 1, Integer::sum);
            var pickup = ledger.pickup(id);
            if (pickup == null || !sameContinuation(s, pickup.motion())) {
                admissionErrors.add("Post-tick continuation differs for " + id + ": expected=" + s
                        + " actual=" + (pickup == null ? null : pickup.motion()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, tickProbe);
        long deadline = System.nanoTime() + 30_000_000_000L;
        for (State s : expected.values()) {
            BlockPos pos = BlockPos.containing(s.position().x(), s.position().y(), s.position().z());
            ChunkPos chunk = new ChunkPos(pos);
            FORCED.add(chunk);
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunkAt(pos);
        }
        // Deliberately reproduce T13's synchronous load/emit boundary, but do not assume tracking
        // visibility is synchronous with terrain availability. Capture the actual admission above.
        ledger.emit(level);
        long immediatelyVisible = expected.keySet().stream().filter(id -> level.getEntity(id) != null).count();
        System.out.println("BOLT RESTART immediate visible=" + immediatelyVisible + "/4; loaded terrain is not entity visibility");

        helper.onEachTick(() -> {
            helper.assertTrue(System.nanoTime() < deadline, "Restart readiness/continuation exceeded 30 real seconds");
            helper.assertTrue(admissionErrors.isEmpty(), admissionErrors.toString());
            helper.assertTrue(total(ledger, level) == 4, "Reconstruction lost/duplicated remaining value");
            boolean complete = true;
            for (var entry : expected.entrySet()) {
                UUID id = entry.getKey();
                State s = entry.getValue();
                BlockPos pos = BlockPos.containing(s.position().x(), s.position().y(), s.position().z());
                ChunkPos chunk = new ChunkPos(pos);
                if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isNaturalSpawningAllowed(chunk)) {
                    complete = false;
                    continue;
                }
                var entity = level.getEntity(id);
                if (!(entity instanceof Rac1BoltEntity bolt)) {
                    complete = false;
                    continue;
                }
                helper.assertTrue(firstAdmission.get(id) == bolt, "Visible UUID is not the original admitted view");
                helper.assertTrue(sameContinuation(s, ledger.pickup(id).motion()), "Resumed trajectory/phase differs for " + id);
                helper.assertTrue(bolt.position().distanceToSqr(s.position().x(), s.position().y(), s.position().z()) < 1e-14,
                        "Entity and durable ledger position diverged");
                helper.assertTrue(level.getEntitiesOfClass(Rac1BoltEntity.class,
                        new AABB(bolt.blockPosition()).inflate(8)).stream().filter(e -> e.getUUID().equals(id)).count() == 1,
                        "Expected exactly one visible view per UUID");
                if (observedTicks.getOrDefault(id, 0) < 20) complete = false;
            }
            if (complete) {
                System.out.println("BOLT RESTART READ PASS: exact disk/admission state; one view/UUID; >=20 automatic ticks"
                        + " in all four phases; pending value=4; admission attempts=" + attempts);
                cleanup(level);
                helper.succeed();
            }
        });
    }

    /** Disk/admission checks stay exact. Only the AIR-vs-voxel continued-position oracle tolerates
     * accumulated double rounding from the voxel adapter's split movement samples at x/z=1M. */
    static boolean sameContinuation(State expected, State actual) {
        return expected.position().sub(actual.position()).length() <= 1e-7
                && expected.equals(Rac1BoltMotion.relocated(actual, expected.position()));
    }

    private static Set<UUID> ids(Rac1BoltLedger ledger, ServerLevel level) {
        Set<UUID> result = new HashSet<>();
        for (Tag tag : ledger.save(new CompoundTag(), level.registryAccess()).getList("pickups", Tag.TAG_COMPOUND)) {
            result.add(((CompoundTag) tag).getUUID("id"));
        }
        return result;
    }
    private static long total(Rac1BoltLedger ledger, ServerLevel level) {
        CompoundTag root = ledger.save(new CompoundTag(), level.registryAccess());
        long total = 0;
        for (Tag tag : root.getList("pickups", Tag.TAG_COMPOUND)) total += ((CompoundTag) tag).getInt("value");
        for (Tag tag : root.getList("accounts", Tag.TAG_COMPOUND)) total += ((CompoundTag) tag).getLong("balance");
        return total;
    }
}
