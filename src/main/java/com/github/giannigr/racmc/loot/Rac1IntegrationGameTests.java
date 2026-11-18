package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in exact integrated-tree checks; isolated synthetic fixture, never an owner/client world. */
@GameTestHolder("racmc_integration")
@PrefixGameTestTemplate(false)
public final class Rac1IntegrationGameTests {
    private Rac1IntegrationGameTests() {}

    @GameTest(template = "empty", batch = "integration_chain", timeoutTicks = 40)
    public static void deferredChainPaysOnceAndConservesValue(GameTestHelper h) {
        var level = h.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos b = a.east(3);
        var first = place(level, a, Rac1Crates.EXPLOSIVE);
        var chained = place(level, b, Rac1Crates.EXPLOSIVE);
        var wood = place(level, a.south(2), Rac1Crates.BOLT);
        place(level, a.west(), Rac1Crates.HEALTH);
        place(level, a.north(), Rac1Crates.AMMO);
        UUID aid = first.rewardId(), bid = chained.rewardId(), wid = wood.rewardId();
        int beforeCount = snapshot(level).getList("rewarded", Tag.TAG_COMPOUND).size();
        long beforeValue = total(snapshot(level));
        // An existing moving currency view must survive every member of the chain.
        ledger.reward(UUID.randomUUID(), level, a.above(2), 1);
        ledger.emit(level);
        var view = level.getEntitiesOfClass(Rac1BoltEntity.class, new AABB(a).inflate(4)).getFirst();
        UUID viewId = view.getUUID();
        int min = lower(first.rewardBase()) + lower(chained.rewardBase()) + lower(wood.rewardBase()) + 1;
        int max = upper(first.rewardBase()) + upper(chained.rewardBase()) + upper(wood.rewardBase()) + 1;
        Rac1CrateBlast.detonate(level, Vec3.atCenterOf(a), first, null);
        h.assertTrue(chained.fuse.remaining() == 1, "Chain must defer one tick");
        h.assertTrue(!rewarded(snapshot(level), bid), "Arming paid before detonation");
        for (int i = 0; i < 3; i++) {
            Rac1CrateBlast.detonate(level, Vec3.atCenterOf(a), first, null);
            chained.arm(true);
        }
        h.runAfterDelay(4, () -> {
            var paid = snapshot(level);
            h.assertTrue(rewarded(paid, aid) && rewarded(paid, bid) && rewarded(paid, wid),
                    "Missing actual self/chain/neighbor reward");
            // Health/ammo have zero value but are consumed through the same service.
            h.assertTrue(paid.getList("rewarded", Tag.TAG_COMPOUND).size() == beforeCount + 6,
                    "Incarnation was lost or consumed more than once");
            long value = total(paid) - beforeValue;
            h.assertTrue(value >= min && value <= max, "Payout outside original profiles");
            h.assertTrue(!view.isRemoved() && ledger.pickup(viewId) != null, "Chain lost existing bolt");
            h.assertTrue(paid.equals(Rac1BoltLedger.load(paid, level.registryAccess())
                    .save(new CompoundTag(), level.registryAccess())), "Motion/ledger roundtrip changed value");
            var player = accountPlayer(level);
            player.teleportTo(a.getX(), a.getY() + 4, a.getZ());
            long balance = ledger.balance(player.getUUID());
            for (Tag tag : paid.getList("pickups", Tag.TAG_COMPOUND)) {
                UUID id = ((CompoundTag) tag).getUUID("id");
                h.assertTrue(ledger.collect(id, player), "First collect rejected");
                h.assertTrue(!ledger.collect(id, player), "Duplicate collect credited");
            }
            h.assertTrue(ledger.balance(player.getUUID()) - balance == total(paid),
                    "Pending-to-account conversion lost/duplicated value");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "integration_protection")
    public static void canceledAndDeniedDetonationNeverPays(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var crate = place(level, pos, Rac1Crates.EXPLOSIVE);
        crate.arm(true);
        h.assertTrue(crate.fuse.tick(level.getGameTime() + 1), "Fixture must reserve terminal");
        CompoundTag before = snapshot(level);
        Consumer<ExplosionEvent.Start> veto = event -> event.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            Rac1CrateBlast.detonate(level, Vec3.atCenterOf(pos), crate, null);
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
        }
        h.assertTrue(before.equals(snapshot(level)) && level.getBlockEntity(pos) == crate,
                "Canceled Start removed or paid crate");
        h.assertTrue(!crate.arm(true) && !crate.fuse.tick(level.getGameTime() + 2),
                "Canceled terminal retried");
        Consumer<ExplosionEvent.Detonate> denySelf = event -> event.getAffectedBlocks().remove(pos);
        NeoForge.EVENT_BUS.addListener(denySelf);
        try {
            Rac1CrateBlast.detonate(level, Vec3.atCenterOf(pos), crate, null);
        } finally {
            NeoForge.EVENT_BUS.unregister(denySelf);
        }
        h.assertTrue(before.equals(snapshot(level)) && level.getBlockEntity(pos) == crate,
                "Denied self-consumption emitted reward");
        BlockPos drop = pos.east(4).above(3);
        var protectedFall = place(level, drop, Rac1Crates.EXPLOSIVE);
        UUID id = protectedFall.rewardId();
        protectedFall.arm(false);
        com.github.giannigr.racmc.RacMcMod.LOGGER.info(
                "Integration protected-fall fixture: reward={}, fuse={}", id, protectedFall.fuse.remaining());
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> denySpawn = event -> {
            if (event.getEntity() instanceof Rac1FallingExplosive) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(denySpawn);
        try {
            Rac1FallingExplosive.fall(level, drop, protectedFall.getBlockState());
        } finally {
            NeoForge.EVENT_BUS.unregister(denySpawn);
        }
        h.assertTrue(level.getBlockEntity(drop) == protectedFall
                && id.equals(protectedFall.rewardId()) && protectedFall.fuse.remaining() == 60
                && before.equals(snapshot(level)), "Rejected fall lost source/profile/fuse or paid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "integration_falling", timeoutTicks = 100)
    public static void fallingTransfersProfileFuseAndLandingOrder(GameTestHelper h) {
        fallingTransfersProfileFuseAndLandingOrder(h, false);
    }

    @GameTest(template = "empty", batch = "integration_falling", timeoutTicks = 100)
    public static void fallingAccountingIsScopedAcrossUnrelatedReward(GameTestHelper h) {
        fallingTransfersProfileFuseAndLandingOrder(h, true);
    }

    private static void fallingTransfersProfileFuseAndLandingOrder(GameTestHelper h, boolean interleave) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 1, 2));
        BlockPos origin = floor.above(4);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var crate = place(level, origin, Rac1Crates.EXPLOSIVE);
        level.removeBlock(origin.below(), false);
        crate.arm(false);
        UUID id = crate.rewardId();
        int base = crate.rewardBase();
        CompoundTag before = snapshot(level);
        h.assertTrue(!rewarded(before, id), "Fresh falling incarnation already rewarded");
        Rac1FallingExplosive.fall(level, origin, crate.getBlockState());
        var falling = level.getEntitiesOfClass(Rac1FallingExplosive.class, new AABB(origin).inflate(2)).getFirst();
        CompoundTag saved = falling.saveWithoutId(new CompoundTag());
        var restored = new Rac1FallingExplosive(
                com.github.giannigr.racmc.item.RacMcEntities.FALLING_EXPLOSIVE.get(), level);
        restored.load(saved);
        h.assertTrue(restored.blockData.getUUID("BoltRewardId").equals(id)
                && restored.blockData.getInt("BoltRewardBase") == base
                && restored.fuse.remaining() == falling.fuse.remaining()
                && restored.fuse.lastTick() == falling.fuse.lastTick(), "Airborne save/load lost metadata");
        h.assertTrue(before.equals(snapshot(level)), "Transfer paid");
        UUID unrelated = UUID.randomUUID();
        if (interleave) h.runAfterDelay(5, () ->
                Rac1BoltLedger.get(level).reward(unrelated, level, floor.east(4), 13));
        h.runAfterDelay(30, () -> {
            var landed = level.getBlockEntity(floor.above());
            h.assertTrue(landed instanceof Rac1CrateBlockEntity, "Explosive failed to land");
            var result = (Rac1CrateBlockEntity) landed;
            h.assertTrue(id.equals(result.rewardId()) && result.rewardBase() == base,
                    "Landing NBT overlay rerolled reward");
            h.assertTrue(result.fuse.remaining() > 0 && result.fuse.remaining() < 40,
                    "Landing reset or double-ticked fuse");
            var beforeDetonation = snapshot(level);
            // The ledger is world-global and other batches can leave ticking explosives behind.
            // reward() reserves the placement UUID BEFORE adding any pickups, even for zero value.
            // Absence of THIS UUID plus an unconsumed, unchanged profile proves no subject payout;
            // global value/count equality across 30 ticks instead measures unrelated lifecycles.
            var unconsumed = result.saveWithoutMetadata(level.registryAccess());
            h.assertTrue(!rewarded(beforeDetonation, id) && !unconsumed.getBoolean("BoltRewardConsumed")
                    && id.equals(unconsumed.getUUID("BoltRewardId"))
                    && unconsumed.getInt("BoltRewardBase") == base,
                    "Landing consumed/paid or changed the subject profile before detonation: " + id);
            if (interleave) h.assertTrue(rewarded(beforeDetonation, unrelated),
                    "Fixture did not exercise unrelated world-ledger activity");
            var otherRewards = rewardIds(beforeDetonation);
            otherRewards.removeAll(rewardIds(before));
            com.github.giannigr.racmc.RacMcMod.LOGGER.info(
                    "Integration landing accounting: subject={}, paid={}, fuse={}, otherNewRewards={}",
                    id, rewarded(beforeDetonation, id), result.fuse.remaining(), otherRewards);
            // Save/load the combined grounded state, then actual detonation through shared seam.
            var tag = result.saveWithoutMetadata(level.registryAccess());
            result.loadWithComponents(tag, level.registryAccess());
            Rac1CrateBlast.detonate(level, Vec3.atCenterOf(floor.above()), result, null);
            var paid = snapshot(level);
            var newRewards = rewardIds(paid);
            newRewards.removeAll(rewardIds(beforeDetonation));
            long payout = total(paid) - total(beforeDetonation);
            h.assertTrue(newRewards.equals(java.util.Set.of(id))
                    && payout >= lower(base) && payout <= upper(base),
                    "Actual detonation must pay only this UUID once within its preserved profile");
            Rac1CrateRewards.destroy(level, floor.above(), result.getBlockState(), null);
            h.assertTrue(rewarded(paid, id) && paid.equals(snapshot(level)), "Landed reward duplicated/lost");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "integration_airborne")
    public static void airborneSelfAndBlastDestroyedCarriersPayOnce(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2, 4, 2));
        var crate = place(level, origin, Rac1Crates.EXPLOSIVE);
        UUID self = crate.rewardId();
        Rac1FallingExplosive.fall(level, origin, crate.getBlockState());
        var emitter = level.getEntitiesOfClass(Rac1FallingExplosive.class, new AABB(origin).inflate(1)).getFirst();
        var wood = place(level, origin.east(2), Rac1Crates.BOLT);
        UUID woodId = wood.rewardId();
        CompoundTag woodData = wood.saveWithoutMetadata(level.registryAccess());
        var carrier = FallingBlockEntity.fall(level, wood.getBlockPos(), wood.getBlockState());
        carrier.blockData = woodData;
        Rac1CrateBlast.detonate(level, emitter.position().add(0, .5, 0), null, emitter);
        var paid = snapshot(level);
        h.assertTrue(emitter.isRemoved() && carrier.isRemoved(), "Airborne blast did not consume carriers");
        h.assertTrue(rewarded(paid, self) && rewarded(paid, woodId), "Airborne payout missing");
        Rac1CrateBlast.detonate(level, emitter.position().add(0, .5, 0), null, emitter);
        Rac1CrateRewards.destroy(level, carrier, emitter);
        h.assertTrue(paid.equals(snapshot(level)), "Repeated airborne callback duplicated reward");
        h.succeed();
    }

    private static Rac1CrateBlockEntity place(ServerLevel level, BlockPos pos,
            com.github.giannigr.racmc.asset.Rac1CrateDefinition definition) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, RacMcBlocks.crate(definition).get().defaultBlockState(), 3);
        var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
        crate.initializeReward();
        return crate;
    }

    private static CompoundTag snapshot(ServerLevel level) {
        return Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess());
    }

    /** Server account fixture only: packets sink locally; does NOT verify client synchronization. */
    private static net.minecraft.server.level.ServerPlayer accountPlayer(ServerLevel level) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "integration-account");
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                level.getServer(), new net.minecraft.network.Connection(
                        net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        return player;
    }

    private static boolean rewarded(CompoundTag ledger, UUID id) {
        return ledger.getList("rewarded", Tag.TAG_COMPOUND).stream()
                .anyMatch(tag -> ((CompoundTag) tag).getUUID("id").equals(id));
    }

    private static java.util.Set<UUID> rewardIds(CompoundTag ledger) {
        var result = new java.util.HashSet<UUID>();
        for (Tag entry : ledger.getList("rewarded", Tag.TAG_COMPOUND))
            result.add(((CompoundTag) entry).getUUID("id"));
        return result;
    }

    private static long total(CompoundTag ledger) {
        return ledger.getList("pickups", Tag.TAG_COMPOUND).stream()
                .mapToLong(tag -> ((CompoundTag) tag).getInt("value")).sum();
    }

    private static int lower(int base) { return base - (base >= 8 ? base / 4 : base >= 2 ? 1 : 0); }
    private static int upper(int base) { return base + (base >= 8 ? base / 4 : base >= 2 ? 1 : 0); }
}
