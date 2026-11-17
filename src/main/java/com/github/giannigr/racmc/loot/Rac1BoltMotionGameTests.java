package com.github.giannigr.racmc.loot;

import java.util.*;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

/** Synthetic-world checks for independent T execution; no ISO, client or copied structures. */
@GameTestHolder("racmc")
@PrefixGameTestTemplate(false)
public final class Rac1BoltMotionGameTests {
    private Rac1BoltMotionGameTests() {}

    @GameTest(template = "empty")
    public static void sphereUsesRealSlabAndWallShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(floor, Blocks.STONE_SLAB.defaultBlockState(), 3);
        var entity = new Rac1BoltEntity(RacMcEntities.BOLT.get(), level);
        var collision = new Rac1BoltWorldCollision(level, entity);
        V start = new V(floor.getX() + .5, floor.getY() + .8, floor.getZ() + .5);
        Movement slab = collision.move(start, new V(0, -.3, 0), .23);
        helper.assertTrue(slab.position().y() >= floor.getY() + .73, "Sphere penetrated slab");
        helper.assertTrue(slab.contacts().stream().anyMatch(c -> Rac1BoltMotion.ground(c.separation(), c.normal())),
                "Actual slab shape did not produce suitable ground");
        level.setBlock(floor.east(), Blocks.STONE.defaultBlockState(), 3);
        Movement wall = collision.move(new V(floor.getX() + .7, floor.getY() + .8, floor.getZ() + .5),
                new V(.4, 0, 0), .15);
        helper.assertTrue(wall.position().x() < floor.getX() + 1, "Sphere tunneled through actual wall");
        helper.assertTrue(wall.contacts().stream().anyMatch(c -> c.separation().x() < 0), "No wall reflection normal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ledgerLaunchPrecedesAdmissionAndRepeatedEmitDoesNotReroll(GameTestHelper helper) {
        var level = helper.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        Set<UUID> before = ids(ledger.save(new CompoundTag(), level.registryAccess()));
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        UUID reward = UUID.randomUUID();
        ledger.reward(reward, level, source, 76);
        CompoundTag pending = ledger.save(new CompoundTag(), level.registryAccess());
        Set<UUID> created = ids(pending);
        created.removeAll(before);
        helper.assertTrue(created.size() > 1, "Fixture needs multiple denominations");
        Set<V> starts = new HashSet<>();
        Map<UUID, State> states = new HashMap<>();
        int total = 0;
        for (UUID id : created) {
            var pickup = ledger.pickup(id);
            State s = pickup.motion();
            helper.assertTrue(s != null && s.phase() == Phase.FALLING, "Launch missing before admission");
            helper.assertTrue(Math.abs(s.position().y() - source.getY() - .5) < 1e-9, "Double launch height");
            starts.add(s.position()); states.put(id, s); total += pickup.value();
        }
        helper.assertTrue(starts.size() > 1, "All launched pickups overlap");
        helper.assertTrue(total == 76, "Launch changed payout value");
        ledger.emit(level);
        ledger.emit(level);
        ledger.reward(reward, level, source, 76);
        for (UUID id : created) {
            var entity = level.getEntity(id);
            helper.assertTrue(entity instanceof Rac1BoltEntity, "Pickup not admitted");
            State s = states.get(id);
            helper.assertTrue(entity.position().distanceToSqr(s.position().x(), s.position().y(), s.position().z()) < 1e-18,
                    "Admission lost ring start");
            helper.assertTrue(entity.getDeltaMovement().lengthSqr() > 0, "Tracking starts at zero velocity");
            helper.assertTrue(s.equals(ledger.pickup(id).motion()), "Repeat emit rerolled state");
            helper.assertTrue(level.getEntitiesOfClass(Rac1BoltEntity.class,
                    new AABB(source).inflate(1)).stream().filter(e -> e.getUUID().equals(id)).count() == 1,
                    "Duplicate admitted view");
        }
        helper.assertTrue(pending.equals(ledger.save(new CompoundTag(), level.registryAccess())),
                "Admission/repeated incarnation mutated canonical pending value");
        helper.succeed();
    }
    private static Set<UUID> ids(CompoundTag root) {
        Set<UUID> result = new HashSet<>();
        for (Tag tag : root.getList("pickups", Tag.TAG_COMPOUND)) result.add(((CompoundTag) tag).getUUID("id"));
        return result;
    }
}
