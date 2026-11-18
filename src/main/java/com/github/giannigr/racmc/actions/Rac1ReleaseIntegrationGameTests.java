package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.*;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.item.*;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import com.github.giannigr.racmc.sound.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;

/** Opt-in final integration probes. Local server accounts/packet sinks are NOT network clients. */
@GameTestHolder("racmc_release")
@PrefixGameTestTemplate(false)
public final class Rac1ReleaseIntegrationGameTests {
    private Rac1ReleaseIntegrationGameTests() {}

    @GameTest(template = "empty", batch = "release_rewards")
    public static void heldSlamThrownShareRewardAndCosmeticTransaction(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var player = player(level, pos);
        List<EffectSignal> effects = new ArrayList<>();
        List<Rac1AudioSignal> sounds = new ArrayList<>();
        Consumer<EffectTriggeredEvent> fx = e -> effects.add(e.signal());
        Consumer<Rac1AudioEmittedEvent> audio = e -> sounds.add(e.signal());
        NeoForge.EVENT_BUS.addListener(fx);
        NeoForge.EVENT_BUS.addListener(audio);
        try {
            for (int mode = 0; mode < 3; mode++) {
                var crate = place(level, pos, Rac1Crates.BOLT);
                var state = crate.getBlockState();
                UUID id = crate.rewardId();
                int before = rewarded(level);
                int soundBefore = sounds.size(), fxBefore = effects.size();
                var candidate = new WrenchSlamBlocks.Candidate(pos, state, crate);
                if (mode == 0) player.gameMode.destroyBlock(pos);
                else WrenchActions.harvestContact(player, candidate, mode == 1 ? null
                        : new WrenchActions.ThrowReach(new WrenchContact.Segment(
                                WrenchActions.vector(Vec3.atCenterOf(pos).add(-1, 0, 0)),
                                WrenchActions.vector(Vec3.atCenterOf(pos).add(1, 0, 0))), 5));
                h.assertTrue(level.getBlockState(pos).isAir(), "Wrench mode " + mode + " did not remove");
                h.assertTrue(rewarded(level) == before + 1 && paid(level, id), "Missing shared payout");
                h.assertTrue(effects.size() == fxBefore + 1 && sounds.size() == soundBefore + 1,
                        "Missing/duplicate break cosmetics");
                WrenchActions.harvestContact(player, candidate);
                h.assertTrue(rewarded(level) == before + 1 && effects.size() == fxBefore + 1
                        && sounds.size() == soundBefore + 1, "Stale callback duplicated transaction");
            }
            // BreakEvent cancellation still precedes the shared transaction.
            var denied = place(level, pos, Rac1Crates.BOLT);
            Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> veto =
                    e -> { if (e.getPos().equals(pos)) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(veto);
            try {
                WrenchActions.harvestContact(player,
                        new WrenchSlamBlocks.Candidate(pos, denied.getBlockState(), denied));
                h.assertTrue(!denied.isRemoved() && !paid(level, denied.rewardId()), "Veto bypassed");
            } finally { NeoForge.EVENT_BUS.unregister(veto); }
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(fx);
            NeoForge.EVENT_BUS.unregister(audio);
        }
    }

    @GameTest(template = "empty", batch = "release_fuse")
    public static void directSlamThrownShortFusePayOnlyOnDetonation(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var player = player(level, pos);
        List<Rac1AudioSignal> sounds = new ArrayList<>();
        List<EffectSignal> effects = new ArrayList<>();
        Consumer<Rac1AudioEmittedEvent> audio = e -> sounds.add(e.signal());
        Consumer<EffectTriggeredEvent> fx = e -> effects.add(e.signal());
        NeoForge.EVENT_BUS.addListener(audio);
        NeoForge.EVENT_BUS.addListener(fx);
        try {
            for (int mode = 0; mode < 3; mode++) {
                var crate = place(level, pos, Rac1Crates.EXPLOSIVE);
                var candidate = new WrenchSlamBlocks.Candidate(pos, crate.getBlockState(), crate);
                UUID id = crate.rewardId();
                int before = rewarded(level), soundBefore = sounds.size(), fxBefore = effects.size();
                if (mode == 0) {
                    // ServerPlayer.tick is bookkeeping; connection-driven doTick advances Player cooldown.
                    for (int tick = 0; tick < 20; tick++) {
                        player.doTick();
                        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
                    }
                    player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
                    h.assertTrue(WrenchActions.valid(player) && player.isAlive()
                            && player.getAttackStrengthScale(0) >= .999F,
                            "Account fixture must be alive/valid/charged: "
                                    + player.getAttackStrengthScale(0) + "/" + player.getPose());
                    var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                            player, pos, Direction.NORTH,
                            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
                    NeoForge.EVENT_BUS.post(event);
                    h.assertTrue(event.isCanceled(), "Explosive held hit must not native-mine");
                } else {
                    WrenchActions.explosiveContact(player, candidate, mode == 1 ? null
                            : new WrenchActions.ThrowReach(new WrenchContact.Segment(
                                    WrenchActions.vector(Vec3.atCenterOf(pos).add(-1, 0, 0)),
                                    WrenchActions.vector(Vec3.atCenterOf(pos).add(1, 0, 0))), 5));
                }
                h.assertTrue(crate.fuse.remaining() >= 2 && crate.fuse.remaining() <= 5,
                        "Real short fuse not wired for mode " + mode);
                h.assertTrue(!crate.isRemoved() && rewarded(level) == before
                        && sounds.size() == soundBefore && effects.size() == fxBefore,
                        "Arming removed/paid/emitted a blast");
                var saved = crate.saveWithoutMetadata(level.registryAccess());
                crate.loadWithComponents(saved, level.registryAccess());
                h.assertTrue(id.equals(crate.rewardId()) && !crate.fuse.tick(level.getGameTime()),
                        "Save/load lost reward/fuse processed-tick identity");
                Rac1CrateBlast.detonate(level, Vec3.atCenterOf(pos), crate, null);
                Rac1CrateBlast.detonate(level, Vec3.atCenterOf(pos), crate, null);
                h.assertTrue(paid(level, id) && rewarded(level) == before + 1, "Detonation payout not once");
                h.assertTrue(sounds.size() == soundBefore + 1 && sounds.getLast().event() == Rac1AudioEvent.TNT_BLAST
                        && effects.size() == fxBefore + 1
                        && effects.getLast().semantic() == EffectSignal.Semantic.CRATE_TNT_BLAST,
                        "Detonation sound/FX not once");
            }
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(audio);
            NeoForge.EVENT_BUS.unregister(fx);
        }
    }

    @GameTest(template = "empty", batch = "release_protection")
    public static void vendorHardDeniedInCreativeAndVanillaUnaffected(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var player = player(level, pos);
        player.setGameMode(GameType.CREATIVE);
        var state = RacMcBlocks.VENDOR.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        h.assertTrue(Rac1WrenchEvents.action(state) == Rac1WrenchBlockPolicy.Action.PROTECTED,
                "Vendor ID must deny independent of datapacks");
        h.assertTrue(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos) == state,
                "Creative native break bypassed vendor protection");
        var candidate = new WrenchSlamBlocks.Candidate(pos, state, level.getBlockEntity(pos));
        for (var kind : Rac1WrenchCrateHits.ContactKind.values())
            h.assertTrue(!Rac1WrenchCrateHits.contact(player, pos, state, candidate.entity(), kind),
                    "Vendor entered explosive adapter");
        var action = new WrenchActionState();
        WrenchSlamBlocks.deliver(player, action, List.of(candidate), player.getMainHandItem(), true);
        WrenchSlamBlocks.deliver(player, action, List.of(candidate), player.getMainHandItem(), true,
                new WrenchActions.ThrowReach(new WrenchContact.Segment(new ActionVector(0, 0, 0),
                        new ActionVector(1, 0, 0)), 5));
        h.assertTrue(level.getBlockState(pos) == state, "Action bypassed vendor deny");
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(Rac1WrenchEvents.action(level.getBlockState(pos)) == Rac1WrenchBlockPolicy.Action.NORMAL
                && player.gameMode.destroyBlock(pos), "Vanilla block mining changed");
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

    @GameTest(template = "empty", batch = "release_airborne")
    public static void airborneDetonationSoundAndEffectsOnceAfterVeto(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 5, 3));
        var crate = place(level, pos, Rac1Crates.EXPLOSIVE);
        UUID id = crate.rewardId();
        int base = crate.rewardBase();
        crate.wrenchHit();
        Rac1FallingExplosive.fall(level, pos, crate.getBlockState());
        var falling = level.getEntitiesOfClass(Rac1FallingExplosive.class, new AABB(pos).inflate(1)).getFirst();
        h.assertTrue(falling.blockData.getUUID("BoltRewardId").equals(id)
                && falling.blockData.getInt("BoltRewardBase") == base
                && falling.fuse.remaining() == crate.fuse.remaining()
                && falling.fuse.lastTick() == crate.fuse.lastTick(), "Wrench-hit fall lost NBT timing/profile");
        List<Rac1AudioSignal> sounds = new ArrayList<>();
        List<EffectSignal> effects = new ArrayList<>();
        Consumer<Rac1AudioEmittedEvent> audio = e -> sounds.add(e.signal());
        Consumer<EffectTriggeredEvent> fx = e -> effects.add(e.signal());
        Consumer<net.neoforged.neoforge.event.level.ExplosionEvent.Start> veto =
                e -> e.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(audio);
        NeoForge.EVENT_BUS.addListener(fx);
        NeoForge.EVENT_BUS.addListener(veto);
        var center = falling.position().add(0, .5, 0);
        try {
            Rac1CrateBlast.detonate(level, center, null, falling);
            h.assertTrue(!falling.isRemoved() && !paid(level, id) && sounds.isEmpty()
                    && effects.isEmpty(), "Veto emitted cosmetics/reward");
            NeoForge.EVENT_BUS.unregister(veto);
            Rac1CrateBlast.detonate(level, center, null, falling);
            Rac1CrateBlast.detonate(level, center, null, falling);
            h.assertTrue(falling.isRemoved() && paid(level, id) && sounds.size() == 1
                    && sounds.getFirst().event() == Rac1AudioEvent.TNT_BLAST
                    && effects.size() == 1
                    && effects.getFirst().semantic() == EffectSignal.Semantic.CRATE_TNT_BLAST,
                    "Airborne reward/audio/FX not exactly once");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
            NeoForge.EVENT_BUS.unregister(audio);
            NeoForge.EVENT_BUS.unregister(fx);
        }
    }
    private static int rewarded(ServerLevel level) {
        return Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess())
                .getList("rewarded", Tag.TAG_COMPOUND).size();
    }
    private static boolean paid(ServerLevel level, UUID id) {
        return Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess())
                .getList("rewarded", Tag.TAG_COMPOUND).stream()
                .anyMatch(t -> ((CompoundTag) t).getUUID("id").equals(id));
    }
    private static ServerPlayer player(ServerLevel level, BlockPos pos) {
        return player(level, pos, new ArrayList<>());
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos,
            List<net.minecraft.network.protocol.Packet<?>> packets) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "release-account");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(RacMcItems.OMNIWRENCH.get()));
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        return player;
    }

    @GameTest(template = "empty", batch = "release_cooldown")
    public static void heldStartCooldownRejectsWithoutMutationAndResyncs(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var crate = place(level, pos, Rac1Crates.EXPLOSIVE);
        List<net.minecraft.network.protocol.Packet<?>> packets = new ArrayList<>();
        var player = player(level, pos, packets);
        for (int tick = 0; tick < 20; tick++) {
            player.doTick();
            player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        }
        var item = RacMcItems.OMNIWRENCH.get();
        player.getCooldowns().addCooldown(item, 100);
        h.assertTrue(WrenchActions.valid(player) && player.getAttackStrengthScale(0) >= .999F
                && player.getCooldowns().isOnCooldown(item), "Fixture must be charged with active item cooldown");
        var state = crate.getBlockState();
        var metadata = crate.saveWithoutMetadata(level.registryAccess());
        var ledger = Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess());
        packets.clear();
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                player, pos, Direction.NORTH,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
        NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled() && level.getBlockState(pos) == state
                && level.getBlockEntity(pos) == crate && !crate.isRemoved()
                && crate.fuse.remaining() == Rac1ExplosiveFuse.IDLE,
                "Cooldown START armed/removed/replaced explosive");
        h.assertTrue(metadata.equals(crate.saveWithoutMetadata(level.registryAccess()))
                && ledger.equals(Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess())),
                "Rejected START mutated fuse/profile/reward");
        h.assertTrue(packets.stream().filter(p ->
                p instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket block
                        && block.getPos().equals(pos) && block.getBlockState() == state).count() == 1,
                "Rejected START must resync the authoritative block exactly once");
        h.assertTrue(packets.stream().filter(p ->
                p instanceof net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket data
                        && data.getPos().equals(pos) && metadata.equals(data.getTag())).count() == 1,
                "Rejected START must resync unchanged block-entity metadata exactly once");
        h.assertTrue(player.getAttackStrengthScale(0) >= .999F
                && WrenchActions.normalAttackAllowed(player), "Rejected START consumed normal attack gate");
        player.getCooldowns().removeCooldown(item);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                player, pos, Direction.NORTH,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START));
        h.assertTrue(crate.fuse.remaining() >= 2 && crate.fuse.remaining() <= 5
                && ledger.equals(Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess())),
                "Cleared cooldown must permit short fuse without premature reward");
        h.succeed();
    }
}
