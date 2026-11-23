package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import com.github.giannigr.racmc.block.Rac1ExplosiveFuse;
import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("racmc_ranged")
@PrefixGameTestTemplate(false)
public final class BombGloveGameTests {
    private BombGloveGameTests() {}

    private static final class Probe extends ServerPlayer {
        final ArrayList<BombGloveTracking.Held> snapshots = new ArrayList<>();

        Probe(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                    this, CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof BombGloveTrackingNetwork payload)
                        snapshots.add(payload.snapshot());
                }
            };
            setGameMode(GameType.SURVIVAL);
        }

        void useTicks(int ticks) {
            for (int i = 0; i < ticks && isUsingItem(); i++) updateUsingItem(getUseItem());
        }
    }

    private static Probe player(GameTestHelper h) {
        var id = UUID.randomUUID();
        var player = new Probe(h.getLevel(), new GameProfile(id, "bomb-" + id.toString().substring(0, 8)));
        var at = center(h, 5, 4, 5);
        player.teleportTo(at.x, at.y, at.z);
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RacMcItems.BOMB_GLOVE.get()));
        return player;
    }

    private static Vec3 center(GameTestHelper h, int x, int y, int z) {
        return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, y, z)));
    }

    private static boolean use(Probe player, InteractionHand hand) {
        return player.getItemInHand(hand).use(player.level(), player, hand).getResult().consumesAction();
    }

    @GameTest(template = "empty", batch = "bomb_throw")
    public static void nativeWindupCostsExactlyOnceAndRejectsWrongAmmoAndSwap(GameTestHelper h) {
        var player = player(h);
        var ammo = RacMcItems.BOMB_GLOVE_AMMO.get();
        var glove = RacMcItems.BOMB_GLOVE.get();
        var released = new ArrayList<Rac1Bomb>();
        Consumer<BombGloveActionEvent> observer = e -> {
            if (e.kind() == BombGloveActionEvent.Kind.RELEASE && e.bomb().getOwner() == player)
                released.add(e.bomb());
        };
        NeoForge.EVENT_BUS.addListener(observer);
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(glove));
            h.assertTrue(!use(player, InteractionHand.OFF_HAND), "Offhand started a throw");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 8));
            player.getInventory().setItem(2, new ItemStack(RacMcItems.BLASTER_AMMO.get(), 8));
            h.assertTrue(!use(player, InteractionHand.MAIN_HAND), "Other ammo paid for a throw");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ammo, 3));
            player.getInventory().setItem(3, new ItemStack(ammo, 1));
            h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Valid windup rejected");
            player.useTicks(BombGlovePose.WINDUP_TICKS - 1);
            h.assertTrue(released.isEmpty() && BombGloveAmmo.count(player) == 4, "Windup paid or released early");
            player.releaseUsingItem();
            h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Repress should allow new windup");
            player.getInventory().selected = 2;
            player.useTicks(BombGlovePose.WINDUP_TICKS);
            player.releaseUsingItem();
            player.getInventory().selected = 0;
            h.assertTrue(released.isEmpty() && BombGloveAmmo.count(player) == 4, "Slot swap released a stale throw");
            h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Valid second windup rejected");
            player.useTicks(30);
            h.assertTrue(released.size() == 1 && player.getOffhandItem().getCount() == 2
                    && BombGloveAmmo.count(player) == 3, "Held input repeated or charged the wrong stack");
            h.assertTrue(released.getFirst().position().equals(BombGlovePose.palm(player)), "Launch did not use palm");
            player.releaseUsingItem();
            h.assertTrue(!use(player, InteractionHand.MAIN_HAND), "Cooldown accepted an immediate second throw");
            h.succeed();
        } finally {
            player.releaseUsingItem();
            NeoForge.EVENT_BUS.unregister(observer);
            released.forEach(Rac1Bomb::discard);
        }
    }

    @GameTest(template = "empty", batch = "bomb_admission")
    public static void obstructedPalmAndSpawnVetoDoNotSpendOrStartCooldown(GameTestHelper h) {
        var level = h.getLevel();
        var player = player(h);
        var glove = RacMcItems.BOMB_GLOVE.get();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(), 2));
        var palm = BlockPos.containing(BombGlovePose.palm(player));
        level.setBlockAndUpdate(palm, Blocks.STONE.defaultBlockState());
        h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Windup rejected before obstruction resolution");
        player.useTicks(8);
        h.assertTrue(BombGloveTrackingServer.snapshot(player).phase() == BombGloveTracking.Phase.ABORTED,
                "Obstructed release did not publish an abort");
        player.releaseUsingItem();
        h.assertTrue(BombGloveAmmo.count(player) == 2 && !player.getCooldowns().isOnCooldown(glove),
                "Palm obstruction consumed ammo or cooldown");
        level.removeBlock(palm, false);
        var attempts = new ArrayList<Rac1Bomb>();
        Consumer<EntityJoinLevelEvent> veto = e -> {
            if (e.getEntity() instanceof Rac1Bomb bomb && bomb.getOwner() == player) {
                attempts.add(bomb);
                e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Windup after obstruction was blocked");
            player.useTicks(12);
            h.assertTrue(attempts.size() == 1 && attempts.getFirst().isRemoved()
                    && attempts.getFirst().committedThrow() == null
                    && BombGloveTrackingServer.snapshot(player).phase() == BombGloveTracking.Phase.ABORTED
                    && BombGloveAmmo.count(player) == 2 && !player.getCooldowns().isOnCooldown(glove),
                    "Spawn veto did not refund exactly once");
            h.succeed();
        } finally {
            player.releaseUsingItem();
            NeoForge.EVENT_BUS.unregister(veto);
        }
    }

    @GameTest(template = "empty", batch = "bomb_sync")
    public static void acceptedLastRoundJoinsMatchingIdAndLateTrackingDoesNotReplay(GameTestHelper h) {
        var player = player(h);
        var watcher = player(h);
        var observed = new ArrayList<Rac1Bomb>();
        Consumer<EntityJoinLevelEvent> beforeCommit = e -> {
            if (e.getEntity() instanceof Rac1Bomb bomb && bomb.getOwner() == player) {
                if (bomb.committedThrow() != null) throw new AssertionError("Spawn claimed uncommitted flight");
                observed.add(bomb);
            }
        };
        NeoForge.EVENT_BUS.addListener(beforeCommit);
        try {
            BombGloveThrows.tick(new PlayerTickEvent.Post(player));
            h.assertTrue(BombGloveTrackingServer.snapshot(player) == null, "Empty survival glove displayed a held bomb");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get()));
            BombGloveThrows.tick(new PlayerTickEvent.Post(player));
            var ready = BombGloveTrackingServer.snapshot(player);
            h.assertTrue(ready.phase() == BombGloveTracking.Phase.READY, "Ammo did not enable authoritative idle");
            BombGloveThrows.tracking(new PlayerEvent.StartTracking(watcher, player));
            h.assertTrue(watcher.snapshots.equals(java.util.List.of(ready)), "Late observer missed the idle snapshot");
            h.assertTrue(use(player, InteractionHand.MAIN_HAND), "Last-round windup did not begin");
            var windup = BombGloveTrackingServer.snapshot(player);
            h.assertTrue(windup.phase() == BombGloveTracking.Phase.WINDUP && windup.key().equals(ready.key()),
                    "Ready and windup did not share the next throw identity");
            player.useTicks(30);
            var terminal = BombGloveTrackingServer.snapshot(player);
            h.assertTrue(terminal.phase() == BombGloveTracking.Phase.RELEASED && observed.size() == 1
                    && BombGloveAmmo.count(player) == 0, "Last-round release repeated or lost its phase");
            var bomb = observed.getFirst();
            var commit = bomb.committedThrow();
            h.assertTrue(commit != null && commit.key().equals(windup.key())
                    && commit.key().equals(terminal.key()) && commit.releaseTick() == terminal.phaseTick(),
                    "Committed flight did not join the held action");

            BombGloveThrows.tracking(new PlayerEvent.StartTracking(watcher, player));
            h.assertTrue(watcher.snapshots.equals(java.util.List.of(ready, terminal)),
                    "Late tracking did not send only the current terminal snapshot");
            var saved = new CompoundTag();
            bomb.addAdditionalSaveData(saved);
            var restored = new Rac1Bomb(RacMcEntities.BOMB.get(), h.getLevel());
            restored.readAdditionalSaveData(saved);
            h.assertTrue(commit.equals(restored.committedThrow()), "Persisted flight lost its committed identity");
            BombGloveThrows.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
            h.assertTrue(BombGloveTrackingServer.snapshot(player) == null, "Logout left an owner pose snapshot");
            h.succeed();
        } finally {
            player.releaseUsingItem();
            BombGloveThrows.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
            NeoForge.EVENT_BUS.unregister(beforeCommit);
            observed.forEach(Rac1Bomb::discard);
        }
    }

    private static Rac1Bomb bomb(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 velocity) {
        var bomb = new Rac1Bomb(RacMcEntities.BOMB.get(), level);
        bomb.setOwner(owner);
        bomb.setPos(at);
        bomb.setDeltaMovement(velocity);
        if (!level.addFreshEntity(bomb)) throw new AssertionError("Bomb fixture rejected");
        return bomb;
    }

    private static Zombie target(ServerLevel level, Vec3 at) {
        var entity = new Zombie(EntityType.ZOMBIE, level);
        entity.setNoAi(true);
        entity.setPos(at);
        if (!level.addFreshEntity(entity)) throw new AssertionError("Mob fixture rejected");
        return entity;
    }

    @GameTest(template = "empty", batch = "bomb_attributed_fuse")
    public static void acceptedDryWaveArmsOnlyAuthorizedRacCrateWithNormalFuse(GameTestHelper h) {
        var level=h.getLevel();
        var owner=player(h);
        var center=h.absolutePos(new BlockPos(5,4,5));
        level.setBlockAndUpdate(center,Blocks.STONE.defaultBlockState());
        var allowed=center.offset(1,1,0);
        var denied=center.offset(-1,1,0);
        var vanilla=center.offset(0,1,1);
        var red=RacMcBlocks.crate(Rac1Crates.EXPLOSIVE).get().defaultBlockState();
        level.setBlockAndUpdate(allowed,red);
        level.setBlockAndUpdate(denied,red);
        level.setBlockAndUpdate(vanilla,Blocks.TNT.defaultBlockState());
        Consumer<BlockEvent.BreakEvent> veto=e -> { if (e.getPos().equals(denied)) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        var bomb=bomb(level,owner,Vec3.atBottomCenterOf(center).add(0,1.4,0),new Vec3(0,-1,0));
        bomb.commitThrow(new BombGloveTracking.Commit(new BombGloveTracking.Key(
                owner.getUUID(),level.dimension().location(),UUID.randomUUID(),1),level.getGameTime()));
        try {
            for (int i=0;i<=BombBallistics.WAVE_STEPS;i++) bomb.tick();
            var fuse=((Rac1CrateBlockEntity)level.getBlockEntity(allowed)).fuse;
            h.assertTrue(fuse.remaining()==Rac1ExplosiveFuse.FULL_TICKS && owner.getUUID().equals(fuse.bombOwner()),
                    "Accepted dry wave failed to start an attributed full fuse");
            h.assertTrue(((Rac1CrateBlockEntity)level.getBlockEntity(denied)).fuse.remaining()==Rac1ExplosiveFuse.IDLE
                            && level.getBlockState(vanilla).is(Blocks.TNT) && level.getBlockState(center).is(Blocks.STONE),
                    "Wave changed vetoed, vanilla TNT or terrain");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
            bomb.discard();
        }
    }

    @GameTest(template = "empty", batch = "bomb_wave")
    public static void dryContactExpandsDamagesOnceAndNeverCarvesTerrain(GameTestHelper h) {
        var level = h.getLevel();
        var owner = player(h);
        var contact = center(h, 5, 4, 5);
        var stone = BlockPos.containing(contact);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var open = target(level, contact.add(2.1, 1, 0));
        var protectedMob = target(level, contact.add(-2.1, 1, 0));
        level.setBlockAndUpdate(stone.offset(-1, 1, 0), Blocks.STONE.defaultBlockState());
        var vendor = stone.offset(0, 1, 2);
        level.setBlockAndUpdate(vendor, RacMcBlocks.VENDOR.get().defaultBlockState());
        var crate = stone.offset(0, 1, -2);
        level.setBlockAndUpdate(crate, RacMcBlocks.crate(Rac1Crates.AMMO).get().defaultBlockState());
        var openCrate = stone.offset(1, 1, -1);
        level.setBlockAndUpdate(openCrate, RacMcBlocks.crate(Rac1Crates.AMMO).get().defaultBlockState());
        var tnt = stone.offset(1, 1, 1);
        level.setBlockAndUpdate(tnt, RacMcBlocks.crate(Rac1Crates.EXPLOSIVE).get().defaultBlockState());
        var fuse = ((Rac1CrateBlockEntity) level.getBlockEntity(tnt)).fuse;
        var lootVeto = new ArrayList<BlockPos>();
        Consumer<BlockEvent.BreakEvent> veto = e -> {
            if (e.getPos().equals(crate)) {
                lootVeto.add(e.getPos().immutable());
                e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(veto);
        var bomb = bomb(level, owner, contact.add(0, 1.4, 0), new Vec3(0, -1, 0));
        try {
            float start = open.getHealth();
            float shielded = protectedMob.getHealth();
            bomb.tick();
            h.assertTrue(bomb.phase() == Rac1Bomb.Phase.BLAST && bomb.blastRadius() == .5,
                    "Solid contact did not start a small dry blast");
            h.assertTrue(open.getHealth() == start, "Outer damage applied before the wave arrived");
            float firstDamage = start;
            for (int i = 0; i <= BombBallistics.WAVE_STEPS; i++) {
                bomb.tick();
                if (firstDamage == start) firstDamage = open.getHealth();
            }
            h.assertTrue(bomb.isRemoved() && firstDamage < start && open.getHealth() == firstDamage
                    && protectedMob.getHealth() == shielded, "Wave did not expand, bypassed occlusion, or repeated damage");
            h.assertTrue(level.getBlockState(stone).is(Blocks.STONE)
                    && level.getBlockState(vendor).is(RacMcBlocks.VENDOR.get())
                    && level.getBlockState(crate).is(RacMcBlocks.crate(Rac1Crates.AMMO).get())
                    && lootVeto.size() == 1 && level.getBlockState(openCrate).isAir()
                    && fuse.remaining() == Rac1ExplosiveFuse.IDLE,
                    "Wave bypassed terrain/vendor or protected/accepted crate policy");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
            bomb.discard();
            open.discard();
            protectedMob.discard();
        }
    }

    @GameTest(template = "empty", batch = "bomb_water")
    public static void descendingWaterEntrySinksBeforeADeepBurstWithNoWave(GameTestHelper h) {
        var level = h.getLevel();
        var owner = player(h);
        var bottom = BlockPos.containing(center(h, 5, 2, 5));
        level.setBlockAndUpdate(bottom, Blocks.STONE.defaultBlockState());
        for (int i = 1; i <= 4; i++) level.setBlockAndUpdate(bottom.above(i), Blocks.WATER.defaultBlockState());
        var bomb = bomb(level, owner, Vec3.atCenterOf(bottom.above(5)), new Vec3(0, -1, 0));
        try {
            bomb.tick();
            h.assertTrue(bomb.phase() == Rac1Bomb.Phase.SINKING && bomb.getDeltaMovement().equals(BombBallistics.SINK),
                    "Descending bomb did not enter the water sink state");
            for (int i = 0; i < 80 && bomb.phase() == Rac1Bomb.Phase.SINKING; i++) bomb.tick();
            h.assertTrue(bomb.phase() == Rac1Bomb.Phase.DEEP_BURST && bomb.blastRadius() == 0,
                    "Deep bottom contact produced a damaging dry wave");
            bomb.tick();
            h.assertTrue(bomb.isRemoved() && level.getBlockState(bottom).is(Blocks.STONE), "Deep burst persisted/carved");
            h.succeed();
        } finally {
            bomb.discard();
        }
    }

    @GameTest(template = "empty", batch = "bomb_timeout")
    public static void persistedFlightBudgetExpiresOnceAndSpentWaveDoesNotReplay(GameTestHelper h) {
        var level = h.getLevel();
        var owner = player(h);
        var at = center(h, 5, 6, 5);
        var bomb = bomb(level, owner, at, Vec3.ZERO);
        var reloaded = new Rac1Bomb(RacMcEntities.BOMB.get(), level);
        reloaded.setPos(at);
        try {
            var nearExpiry = new CompoundTag();
            bomb.addAdditionalSaveData(nearExpiry);
            nearExpiry.putInt("FlightTicks", BombBallistics.FLIGHT_TICKS - 1);
            bomb.readAdditionalSaveData(nearExpiry);
            bomb.tick();
            h.assertTrue(bomb.phase() == Rac1Bomb.Phase.BLAST && bomb.position().equals(at),
                    "Persisted flight did not time out in clear air");
            var spent = new CompoundTag();
            bomb.addAdditionalSaveData(spent);
            reloaded.readAdditionalSaveData(spent);
            reloaded.tick();
            h.assertTrue(reloaded.isRemoved(), "Loaded blast replayed its damage wave");
            h.succeed();
        } finally {
            bomb.discard();
            reloaded.discard();
        }
    }

    @GameTest(template = "empty", batch = "bomb_policy")
    public static void nativePvpAndTeamPoliciesNeverAllowOwnerHit(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var owner = player(h);
        var other = player(h);
        var bomb = bomb(level, owner, center(h, 5, 6, 5), Vec3.ZERO);
        var scoreboard = level.getScoreboard();
        var team = scoreboard.addPlayerTeam(owner.getScoreboardName());
        boolean originalPvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            h.assertTrue(!bomb.mayDamage(owner) && !bomb.mayDamage(other), "PvP off did not block players");
            server.setPvpAllowed(true);
            h.assertTrue(bomb.mayDamage(other) && !bomb.mayDamage(owner), "PvP on did not preserve owner immunity");
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(other.getScoreboardName(), team);
            team.setAllowFriendlyFire(false);
            h.assertTrue(!bomb.mayDamage(other), "Friendly-fire off allowed a teammate hit");
            team.setAllowFriendlyFire(true);
            h.assertTrue(bomb.mayDamage(other) && !bomb.mayDamage(owner), "Friendly-fire on ignored native policy");
            h.succeed();
        } finally {
            server.setPvpAllowed(originalPvp);
            scoreboard.removePlayerTeam(team);
            bomb.discard();
        }
    }
}
