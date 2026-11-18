package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.block.*;
import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.item.RacMcEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Headless development regression tests, only discovered by the enabled GameTest namespace. */
@GameTestHolder("racmc")
@PrefixGameTestTemplate(false)
public final class Rac1BoltGameTests {
    private Rac1BoltGameTests() {}

    @BeforeBatch(batch = "defaultBatch")
    public static void emptyFixture(net.minecraft.server.level.ServerLevel level) {
        // Synthetic air fixture, generated in memory; no copied binary structure/retail assets.
        level.getStructureManager().getOrCreate(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("racmc", "empty"))
                .fillFromWorld(level, new BlockPos(0, level.getMaxBuildHeight() - 8, 0),
                        new net.minecraft.core.Vec3i(5, 5, 5), false, Blocks.STRUCTURE_VOID);
    }

    @GameTest(template = "empty")
    public static void repeatedExplosionsPreserveCurrency(GameTestHelper helper) {
        var level = helper.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        ledger.reward(UUID.randomUUID(), level, pos, 1);
        ledger.emit(level);
        var entity = level.getEntitiesOfClass(Rac1BoltEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1)).getFirst();
        Vec3 before = entity.position();
        Vec3 velocityBefore = entity.getDeltaMovement();
        var tnt = EntityType.TNT.create(level);
        var creeper = EntityType.CREEPER.create(level);
        for (int i = 0; i < 3; i++) {
            level.explode(tnt, pos.getX(), pos.getY(), pos.getZ(), 4, Level.ExplosionInteraction.TNT);
            level.explode(creeper, pos.getX(), pos.getY(), pos.getZ(), 4, Level.ExplosionInteraction.MOB);
            // A mod blast using the standard engine plus independent explosion-tagged damage.
            level.explode(null, pos.getX(), pos.getY(), pos.getZ(), 4, Level.ExplosionInteraction.BLOCK);
            entity.hurt(level.damageSources().explosion(tnt, tnt), 1000);
        }
        helper.assertTrue(!entity.isRemoved(), "Repeated blasts removed a bolt");
        helper.assertTrue(entity.position().equals(before), "Blast displaced bolt");
        helper.assertTrue(entity.getDeltaMovement().equals(velocityBefore), "Blast changed intentional launch velocity");
        helper.assertTrue(ledger.pickup(entity.getUUID()) != null, "Unclaimed value lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardOncePersistenceAndCollection(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var block = RacMcBlocks.crate(Rac1Crates.definitions().getFirst()).get();
        level.setBlock(pos, block.defaultBlockState(), 3);
        var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
        crate.initializeReward();
        int base = crate.rewardBase();
        UUID incarnation = crate.rewardId();
        CompoundTag saved = crate.saveWithoutMetadata(level.registryAccess());
        crate.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(crate.rewardBase() == base && crate.rewardId().equals(incarnation),
                "Reload rerolled placed profile");
        var ledger = Rac1BoltLedger.get(level);
        Rac1CrateRewards.destroy(level, pos, block.defaultBlockState(), null);
        CompoundTag first = ledger.save(new CompoundTag(), level.registryAccess());
        Rac1CrateRewards.destroy(level, pos, block.defaultBlockState(), null);
        helper.assertTrue(first.equals(ledger.save(new CompoundTag(), level.registryAccess())),
                "Duplicate destruction changed ledger");
        var restored = Rac1BoltLedger.load(first, level.registryAccess());
        helper.assertTrue(first.equals(restored.save(new CompoundTag(), level.registryAccess())),
                "Ledger did not roundtrip");
        // Ledger-only account with an explicit packet sink. The generic GameTest login helper
        // has no negotiated mod channel and throws during login before this test's assertions.
        // This is NOT a negotiated client/network test, and does not disable production validation.
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var player = ledgerAccount(level, packets);
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        var previousIds = new java.util.HashSet<UUID>();
        for (var tag : ledger.save(new CompoundTag(), level.registryAccess())
                .getList("pickups", net.minecraft.nbt.Tag.TAG_COMPOUND))
            previousIds.add(((CompoundTag) tag).getUUID("id"));
        UUID pickup = UUID.randomUUID();
        ledger.reward(pickup, level, pos, 1);
        var added = ledger.save(new CompoundTag(), level.registryAccess())
                .getList("pickups", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                .map(tag -> ((CompoundTag) tag).getUUID("id"))
                .filter(id -> !previousIds.contains(id)).toList();
        helper.assertTrue(added.size() == 1, "The isolated one-bolt reward must add exactly one pickup");
        ledger.emit(level);
        var entity = (Rac1BoltEntity) level.getEntity(added.getFirst());
        helper.assertTrue(entity != null, "The tested reward's pickup view was not emitted");
        long balance = ledger.balance(player.getUUID());
        int value = entity.value();
        helper.assertTrue(ledger.collect(entity.getUUID(), player), "First collection rejected");
        helper.assertTrue(!ledger.collect(entity.getUUID(), player), "Duplicate collection accepted");
        helper.assertTrue(ledger.balance(player.getUUID()) == balance + value, "Incorrect currency credit");
        helper.assertTrue(player.totalExperience == 0, "Collection granted XP");
        helper.assertTrue(!ledger.tryDebit(player, -1), "Negative debit accepted");
        helper.assertTrue(!ledger.tryDebit(player, Long.MAX_VALUE), "Overspend accepted");
        var balances = new java.util.ArrayList<Rac1BoltNetwork.Balance>();
        for (var sent : packets)
            if (sent instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof Rac1BoltNetwork.Balance message) balances.add(message);
        helper.assertTrue(balances.size() == 1
                && balances.getFirst().total() == balance + value && balances.getFirst().pickup(),
                "Collection must still dispatch exactly one genuine balance payload to the fixture sink");
        helper.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer ledgerAccount(
            net.minecraft.server.level.ServerLevel level,
            java.util.List<net.minecraft.network.protocol.Packet<?>> packets) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "bolt-ledger-account");
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fallingKeepsPlacedProfile(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos pos = floor.above(3);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var state = RacMcBlocks.crate(Rac1Crates.BOLT).get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
        crate.initializeReward();
        UUID id = crate.rewardId();
        int base = crate.rewardBase();
        helper.runAfterDelay(40, () -> {
            var landed = level.getBlockEntity(floor.above());
            helper.assertTrue(landed instanceof Rac1CrateBlockEntity, "Crate failed to land");
            var restored = (Rac1CrateBlockEntity) landed;
            helper.assertTrue(restored.rewardId().equals(id) && restored.rewardBase() == base,
                    "Falling rerolled incarnation/profile");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void playerBreakAndExcludedCrates(GameTestHelper helper) {
        var level = helper.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (var definition : Rac1Crates.definitions()) {
            var state = RacMcBlocks.crate(definition).get().defaultBlockState();
            level.setBlock(pos, state, 3);
            var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
            crate.initializeReward();
            helper.assertTrue(definition.classNumber() == 501 || definition.classNumber() == 511
                    ? crate.rewardBase() == 0 : crate.rewardBase() > 0, "Wrong reward eligibility");
            int before = ledger.save(new CompoundTag(), level.registryAccess())
                    .getList("pickups", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            state.getBlock().onDestroyedByPlayer(state, level, pos, player, true, state.getFluidState());
            int after = ledger.save(new CompoundTag(), level.registryAccess())
                    .getList("pickups", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            helper.assertTrue(level.getBlockState(pos).isAir(), "Player hook did not remove crate");
            helper.assertTrue(definition.classNumber() == 501 || definition.classNumber() == 511
                    ? after == before : after > before, "Wrong player-break payout");
        }
        helper.succeed();
    }
}
