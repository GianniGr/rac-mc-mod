package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.*;
import com.github.giannigr.racmc.actions.*;
import com.github.giannigr.racmc.item.*;
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

/** Opt-in isolated fixture. Real server health/entities, synthetic connected packet sink, NOT a network client. */
@GameTestHolder("racmc_nanotech")
@PrefixGameTestTemplate(false)
public final class Rac1NanotechGameTests {
    private static ServerPlayer player(ServerLevel level, Vec3 at) {
        return player(level, at, UUID.randomUUID());
    }
    private static ServerPlayer player(ServerLevel level, Vec3 at, UUID id) {
        var profile = new com.mojang.authlib.GameProfile(id, "nano-probe");
        var p = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                p, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setPos(at); p.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(p);
        return p;
    }
    private static Vec3 base(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            h.getLevel().setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        return Vec3.atBottomCenterOf(pos);
    }
    private static void run(Rac1NanotechLedger ledger, ServerLevel level, int ticks) {
        for (int i = 0; i < ticks; i++) ledger.tick(level);
    }
    private static void cleanup(ServerLevel level, ServerPlayer... players) {
        for (var p : players) p.discard();
        for (var e : level.getEntitiesOfClass(Rac1NanotechEntity.class,
                new AABB(-30_000_000, -20_000_000, -30_000_000, 30_000_000, 20_000_000, 30_000_000))) e.discard();
    }
    @GameTest(template = "empty", batch = "nano_health")
    public static void actualFiveHpOneArrivalOneSoundAndNoXp(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h); var p = player(l, at.add(1, 0, 0)); p.setHealth(10);
        var ledger = new Rac1NanotechLedger(); ledger.release(UUID.randomUUID(), l.dimension(), at, 42);
        List<Rac1AudioSignal> sounds = new ArrayList<>();
        Consumer<Rac1AudioEmittedEvent> listener = e -> { if (e.signal().event() == Rac1AudioEvent.NANOTECH_FIRST_ARRIVAL) sounds.add(e.signal()); };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            run(ledger, l, 1);
            h.assertTrue(p.getHealth() == 10, "Release/attraction healed early");
            var view = l.getEntitiesOfClass(Rac1NanotechEntity.class, new AABB(at, at).inflate(2)).getFirst();
            var values = view.getEntityData().getNonDefaultValues();
            h.assertTrue(values != null && values.size() <= 4, "Unbounded semantic entity metadata");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), l.registryAccess());
            try {
                var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(view.getId(), values);
                net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, packet);
                h.assertTrue(buffer.readableBytes() < 128, "Cluster sends more than bounded semantic metadata");
                var decoded = net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
                h.assertTrue(decoded.id() == view.getId() && decoded.packedItems().size() == values.size()
                        && buffer.readableBytes() == 0, "Cluster metadata roundtrip failed");
            } finally { buffer.release(); }
            run(ledger, l, 40);
            h.assertTrue(p.getHealth() == 15 && ledger.pendingCount() == 0, "Not exactly5HP once");
            h.assertTrue(sounds.size() == 1 && p.totalExperience == 0, "Sound duplicated or XP awarded");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(listener); cleanup(l, p); }
    }
    @GameTest(template = "empty", batch = "nano_health")
    public static void fullAdmissionThenMidflightFillConsumesOnce(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h); var p = player(l, at.add(1, 0, 0));
        var ledger = new Rac1NanotechLedger(); ledger.release(UUID.randomUUID(), l.dimension(), at, 42);
        List<Rac1AudioSignal> sounds = new ArrayList<>();
        Consumer<Rac1AudioEmittedEvent> listener = e -> { if (e.signal().event() == Rac1AudioEvent.NANOTECH_FIRST_ARRIVAL) sounds.add(e.signal()); };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            run(ledger, l, 120);
            h.assertTrue(ledger.pendingCount() == 1 && sounds.isEmpty(), "Full health admitted/expired");
            p.setHealth(19); run(ledger, l, 1);
            p.setHealth(20); run(ledger, l, 40);
            h.assertTrue(p.getHealth() == 20 && ledger.pendingCount() == 0 && sounds.size() == 1,
                    "Already attracting full receiver must consume/clamp/cue once");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(listener); cleanup(l, p); }
    }
    @GameTest(template = "empty", batch = "nano_health")
    public static void reservationsClosestWinnerChangedMaxAndInvalidation(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h);
        var a = player(l, at.add(1, 0, 0)); var b = player(l, at.add(3, 0, 0));
        a.setHealth(15); b.setHealth(20);
        var ledger = new Rac1NanotechLedger();
        ledger.release(UUID.randomUUID(), l.dimension(), at, 42);
        ledger.release(UUID.randomUUID(), l.dimension(), at, 43);
        try {
            run(ledger, l, 1);
            long locked = ledger.save(new CompoundTag(), null).getList("clusters", Tag.TAG_COMPOUND).size();
            h.assertTrue(locked == 2, "Both uncommitted retained");
            a.setGameMode(GameType.SPECTATOR); run(ledger, l, 1);
            h.assertTrue(ledger.pendingCount() == 2, "Invalidation consumed");
            a.setGameMode(GameType.SURVIVAL); a.setHealth(19);
            run(ledger, l, 40);
            h.assertTrue(a.getHealth() == 20 && b.getHealth() == 20 && ledger.pendingCount() == 1,
                    "Reservations/full admission double consumed");
            a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
            a.setHealth(25); run(ledger, l, 40);
            h.assertTrue(a.getHealth() == 30 && ledger.pendingCount() == 0, "Heal scaled with modified max");
            h.succeed();
        } finally { cleanup(l, a, b); }
    }
    private static Rac1CrateBlockEntity place(ServerLevel level, BlockPos pos, com.github.giannigr.racmc.asset.Rac1CrateDefinition definition) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, RacMcBlocks.crate(definition).get().defaultBlockState(), 3);
        var c = (Rac1CrateBlockEntity) level.getBlockEntity(pos); c.initializeReward(); return c;
    }
    @GameTest(template = "empty", batch = "nano_destruction")
    public static void miningHeldSlamThrownAndCancellationReleaseNoBolts(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h); var pos = BlockPos.containing(at);
        var p = player(l, at.add(0, 0, -2));
        var health = Rac1NanotechLedger.get(l); var bolts = Rac1BoltLedger.get(l);
        try {
            for (int mode = 0; mode < 4; mode++) {
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mode == 0
                        ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(RacMcItems.OMNIWRENCH.get()));
                var c = place(l, pos, Rac1Crates.HEALTH);
                UUID source = c.rewardId();
                var before = bolts.save(new CompoundTag(), l.registryAccess());
                int count = health.pendingCount();
                if (mode < 2) p.gameMode.destroyBlock(pos);
                else NanotechWrenchProbe.destroy(p, pos, c, at, mode == 3);
                h.assertTrue(l.getBlockState(pos).isAir() && health.released(source)
                        && health.pendingCount() == count + 1, "No health release for mode" + mode);
                Rac1CrateRewards.destroy(l, pos, c.getBlockState(), p);
                h.assertTrue(health.pendingCount() == count + 1 && before.equals(bolts.save(new CompoundTag(), l.registryAccess())),
                        "Repeated destruction/health awarded bolts");
            }
            var denied = place(l, pos, Rac1Crates.HEALTH);
            Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> veto =
                    e -> { if (e.getPos().equals(pos)) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(veto);
            try {
                p.gameMode.destroyBlock(pos);
                h.assertTrue(!health.released(denied.rewardId()), "Cancellation free drop");
            } finally { NeoForge.EVENT_BUS.unregister(veto); }
            h.succeed();
        } finally { cleanup(l, p); }
    }
    @GameTest(template = "empty", batch = "nano_destruction")
    public static void fallingIdentityAndBlastHealthBoltConservation(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h); var pos = BlockPos.containing(at);
        var health = Rac1NanotechLedger.get(l); var bolts = Rac1BoltLedger.get(l);
        var c = place(l, pos, Rac1Crates.HEALTH); UUID source = c.rewardId();
        var saved = c.saveWithoutMetadata(l.registryAccess()); c.loadWithComponents(saved, l.registryAccess());
        var falling = net.minecraft.world.entity.item.FallingBlockEntity.fall(l, pos, c.getBlockState());
        falling.blockData = saved;
        h.assertTrue(!health.released(source), "Transfer released health");
        Rac1CrateRewards.destroy(l, falling, null); Rac1CrateRewards.destroy(l, falling, null);
        h.assertTrue(health.released(source), "Falling identity lost");
        var grounded = place(l, pos, Rac1Crates.HEALTH); UUID groundId = grounded.rewardId();
        var wood = place(l, pos.east(), Rac1Crates.BOLT); UUID boltId = wood.rewardId();
        int count = health.pendingCount();
        Rac1CrateBlast.detonate(l, at.add(0, .5, 0), null, null);
        Rac1CrateBlast.detonate(l, at.add(0, .5, 0), null, null);
        h.assertTrue(health.released(groundId) && health.pendingCount() == count + 1, "Blast health count");
        var boltTag = bolts.save(new CompoundTag(), l.registryAccess());
        h.assertTrue(boltTag.getList("rewarded", Tag.TAG_COMPOUND).stream()
                .anyMatch(t -> ((CompoundTag)t).getUUID("id").equals(boltId)), "Wood bolt payout lost");
        h.assertTrue(boltTag.getList("rewarded", Tag.TAG_COMPOUND).stream()
                .noneMatch(t -> ((CompoundTag)t).getUUID("id").equals(groundId)
                        || ((CompoundTag)t).getUUID("id").equals(source)), "Health entered bolt ledger");
        var reload = Rac1NanotechLedger.load(health.save(new CompoundTag(), null), null);
        h.assertTrue(reload.released(source) && reload.released(groundId), "Reload tombstones lost");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "nano_recovery")
    public static void deniedViewAdmissionRetainsPendingAndDeathReleasesReservation(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h); var p = player(l, at.add(1, 0, 0)); p.setHealth(10);
        var ledger = new Rac1NanotechLedger(); UUID source = UUID.randomUUID();
        ledger.release(source, l.dimension(), at, 42);
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> veto = e -> {
            if (e.getEntity() instanceof Rac1NanotechEntity) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            run(ledger, l, 50);
            h.assertTrue(ledger.pendingCount() == 1 && p.getHealth() == 10, "Denied admission consumed/healed");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        try {
            run(ledger, l, 1); p.setHealth(0); run(ledger, l, 1);
            h.assertTrue(ledger.pendingCount() == 1 && p.getHealth() == 0, "Death resurrected/consumed");
            var tag = ledger.save(new CompoundTag(), null);
            var c = ledger.cluster(tag.getList("clusters", Tag.TAG_COMPOUND).getCompound(0).getUUID("id"));
            h.assertTrue(c.receiver == null && c.age == 0, "Reservation not cancelled");
            // Same UUID, different player lifetime: replacing the receiver between server ticks cannot
            // redirect an old flight to a respawn/rejoin instance.
            p.setHealth(10); run(ledger, l, 1);
            h.assertTrue(c.receiverInstance == p, "Fixture did not lock original lifetime");
            l.removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            var replacement = player(l, at.add(1, 0, 0), p.getUUID()); replacement.setHealth(10);
            try {
                run(ledger, l, 1);
                h.assertTrue(c.receiver == null && replacement.getHealth() == 10,
                        "Old flight healed a respawn/rejoin replacement");
            } finally { replacement.discard(); }
            h.succeed();
        } finally { cleanup(l, p); }
    }
    @GameTest(template = "empty", batch = "nano_restart")
    public static void actualWorldLedgerSurvivesGracefulRestart(GameTestHelper h) {
        var ledger = Rac1NanotechLedger.get(h.getLevel());
        UUID source = UUID.fromString("98cdebe1-25cf-4be3-99a8-612fb53f0033");
        boolean read = "read".equals(System.getenv("RACMC_NANOTECH_RESTART_PHASE"));
        if (read) h.assertTrue(ledger.released(source), "Actual saved world lost source tombstone");
        else ledger.release(source, h.getLevel(), base(h));
        var tag = ledger.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(tag.getList("clusters", Tag.TAG_COMPOUND).stream()
                .anyMatch(t -> ((CompoundTag)t).getUUID("source").equals(source)), "Actual saved pending lost");
        h.succeed(); // GameTestServer's normal shutdown saves, next read-phase process loads this same world.
    }
    @GameTest(template = "empty", batch = "nano_selection")
    public static void strictRangeSightTieBreakAndMidflightMaximumClamp(GameTestHelper h) {
        var l = h.getLevel(); var at = base(h);
        var a = player(l, at.add(10, 0, 0)); a.setHealth(10);
        var b = player(l, at.add(-1, 0, 0)); b.setHealth(20);
        var ledger = new Rac1NanotechLedger(); ledger.release(UUID.randomUUID(), l.dimension(), at, 42);
        UUID id = ledger.save(new CompoundTag(), null).getList("clusters", Tag.TAG_COMPOUND).getCompound(0).getUUID("id");
        try {
            run(ledger, l, 1);
            h.assertTrue(ledger.cluster(id).receiver == null, "Distance10 admitted");
            a.setPos(at.add(3, 0, 0));
            var wall = BlockPos.containing(at).east();
            l.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            run(ledger, l, 1);
            h.assertTrue(ledger.cluster(id).receiver == null, "Blocked sight admitted");
            l.removeBlock(wall, false);
            a.setPos(at.add(1, 0, 0)); b.setHealth(10);
            run(ledger, l, 1);
            var expected = a.getUUID().toString().compareTo(b.getUUID().toString()) < 0 ? a : b;
            var other = expected == a ? b : a;
            h.assertTrue(expected.getUUID().equals(ledger.cluster(id).receiver), "Tie is not stableUUID");
            expected.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(12);
            run(ledger, l, 40);
            h.assertTrue(expected.getHealth() == 12 && other.getHealth() == 10,
                    "Changed max not clamped/overlapping second winner");
            h.succeed();
        } finally { cleanup(l, a, b); }
    }
}
