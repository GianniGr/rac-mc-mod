package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.clank.Rac1ClankServer;
import com.github.giannigr.racmc.clank.Rac1HydroRuntime;
import com.github.giannigr.racmc.item.Rac1ClankItem;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.rescue.Rac1PodFootprint;
import com.github.giannigr.racmc.rescue.Rac1PodMarker;
import com.github.giannigr.racmc.rescue.Rac1PodRegistration;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in native right-click and Creative ingress probe; never included in the production jar. */
@GameTestHolder("racmc_gear_escrow")
@PrefixGameTestTemplate(false)
public final class Rac1GearEscrowGameTests {
    private Rac1GearEscrowGameTests() {}

    private static final class Sink extends ServerGamePacketListenerImpl {
        Rac1GearState last;

        Sink(ServerLevel level, ServerPlayer player, GameProfile profile) {
            super(level.getServer(), new Connection(PacketFlow.SERVERBOUND) {
                private final io.netty.channel.embedded.EmbeddedChannel channel =
                        new io.netty.channel.embedded.EmbeddedChannel();

                @Override public io.netty.channel.Channel channel() { return channel; }
            }, player, CommonListenerCookie.createInitial(profile, false));
        }

        @Override public boolean isAcceptingMessages() { return true; }

        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof Rac1GearNetwork.Snapshot snapshot) last = snapshot.state();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playerIndex(ServerLevel level) throws ReflectiveOperationException {
        // Fixture-only identity registration; do not use PlayerList.placeNewPlayer or real networking.
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
    }

    private static ServerPlayer player(GameTestHelper test, Map<UUID, ServerPlayer> index,
                                       String name, GameType mode) {
        var level = test.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new Sink(level, player, profile);
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.setGameMode(mode);
        index.put(player.getUUID(), player);
        Rac1GearServer.login(new PlayerEvent.PlayerLoggedInEvent(player));
        return player;
    }

    @GameTest(template = "empty")
    public static void giftedNativeRightClickEscrowsAndRefreshesSelf(GameTestHelper test) throws Exception {
        var index = playerIndex(test.getLevel());
        var a = player(test, index, "clank-issuer", GameType.CREATIVE);
        var b = player(test, index, "clank-recipient", GameType.SURVIVAL);
        try {
            test.assertTrue(Rac1GearServer.rescueClank(a, () -> {}) == Rac1GearServer.RescueResult.NEWLY_UNLOCKED,
                    "Validated Creative bridge did not issue");
            var donor = (Sink) a.connection;
            var recipient = (Sink) b.connection;
            var loose = a.getMainHandItem();
            var issued = Rac1ClankItem.rewardId(loose);
            test.assertTrue(issued != null && !donor.last.back().worn(), "Rescue did not issue a loose token");
            a.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37, loose.copy()));
            test.assertTrue(a.getInventory().getItem(1).isEmpty(), "Creative assignment copied issued item");
            a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            b.setItemInHand(InteractionHand.OFF_HAND, loose);
            var used = b.gameMode.useItem(b, test.getLevel(), b.getOffhandItem(), InteractionHand.OFF_HAND);
            test.assertTrue(used.consumesAction() && b.getOffhandItem().isEmpty(), "Native use left stack in hand");
            var worn = recipient.last;
            test.assertTrue(worn.clankUnlocked() && worn.back().worn() && !worn.clankInInventory(),
                    "Self snapshot did not atomically unlock and wear");
            Rac1GearServer.equipBack(b, new Rac1GearNetwork.EquipBack(worn.epoch(), worn.revision(), false));
            Rac1GearServer.equipBack(b, new Rac1GearNetwork.EquipBack(worn.epoch(), worn.revision(), false));
            test.assertTrue(!recipient.last.back().worn() && recipient.last.clankInInventory(),
                    "Unequip did not publish ready loose state");
            var returned = Rac1ClankItem.rewardId(b.getMainHandItem());
            test.assertTrue(returned != null && returned.token().equals(issued.token())
                    && !returned.seal().equals(issued.seal()) && b.getInventory().getItem(1).isEmpty(),
                    "Return did not preserve one logical token");
            test.assertTrue(Rac1GearLedger.get(test.getLevel()).record(a.getUUID()).back().acquired()
                    && Rac1GearServer.looseSlot(a.getInventory(), Rac1GearLedger.get(test.getLevel())) < 0,
                    "Giver lost entitlement or kept a wearable item");
        } finally {
            for (var player : java.util.List.of(a, b)) {
                Rac1GearServer.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                index.remove(player.getUUID());
                player.discard();
            }
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFixtureDismissDoesNotReturnOverlaidBackOrGrantGear(GameTestHelper test) throws Exception {
        var level = test.getLevel();
        var index = playerIndex(level);
        var player = player(test, index, "clank-fixture", GameType.CREATIVE);
        var sink = (Sink) player.connection;
        try {
            var pack = new ItemStack(RacMcItems.CLANK_THRUSTER_PACK.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, pack);
            var used = player.gameMode.useItem(player, level, pack, InteractionHand.MAIN_HAND);
            var fixture = sink.last;
            test.assertTrue(used.consumesAction() && fixture.fixture() == Module.THRUSTER
                    && !fixture.clankUnlocked() && !fixture.back().worn() && player.getMainHandItem().is(pack.getItem()),
                    "Right-click fixture did not publish temporary wear without taking the item");
            Rac1GearServer.equipBack(player, new Rac1GearNetwork.EquipBack(fixture.epoch(), fixture.revision(), false));
            test.assertTrue(sink.last.fixture() == Module.NONE && !sink.last.clankUnlocked(),
                    "Dismiss left a fixture or granted permanent Gear");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            test.assertTrue(Rac1GearServer.rescueClank(player, () -> {}) == Rac1GearServer.RescueResult.NEWLY_UNLOCKED,
                    "Could not set up a genuine Back token");
            var issued = Rac1ClankItem.rewardId(player.getMainHandItem());
            test.assertTrue(issued != null && Rac1GearServer.useClank(player, InteractionHand.MAIN_HAND)
                    && sink.last.back().worn() && Rac1HydroRuntime.snapshot(player).access(),
                    "Worn physical Back did not admit Creative Hydro");
            player.setItemInHand(InteractionHand.MAIN_HAND, pack);
            player.gameMode.useItem(player, level, pack, InteractionHand.MAIN_HAND);
            fixture = sink.last;
            test.assertTrue(fixture.fixture() == Module.THRUSTER && fixture.back().worn(), "Overlay lost physical Back");
            var dismiss = new Rac1GearNetwork.EquipBack(fixture.epoch(), fixture.revision(), false);
            Rac1GearServer.equipBack(player, dismiss);
            Rac1GearServer.equipBack(player, dismiss);
            var restored = sink.last;
            test.assertTrue(restored.fixture() == Module.NONE && restored.back().worn() && !restored.clankInInventory(),
                    "Dismissing or replaying the fixture request touched physical escrow");

            player.setGameMode(GameType.SURVIVAL);
            Rac1HydroRuntime.stepTick(player);
            test.assertTrue(!Rac1HydroRuntime.snapshot(player).access() && sink.last.back().worn(),
                    "Physical Back retained Creative Hydro in Survival");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Rac1GearServer.equipBack(player, new Rac1GearNetwork.EquipBack(restored.epoch(), restored.revision(), false));
            var returned = Rac1ClankItem.rewardId(player.getMainHandItem());
            test.assertTrue(returned != null && returned.token().equals(issued.token()) && !sink.last.back().worn(),
                    "A separate physical Unequip did not return the same logical token");
        } finally {
            var logout = new PlayerEvent.PlayerLoggedOutEvent(player);
            Rac1GearServer.logout(logout);
            Rac1ClankServer.logout(logout);
            index.remove(player.getUUID());
            player.discard();
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void sameShipOnlyOneAwardThenOneDecorativeRecovery(GameTestHelper test) throws Exception {
        var level = test.getLevel();
        var index = playerIndex(level);
        var a = player(test, index, "ship-a", GameType.CREATIVE);
        var b = player(test, index, "ship-b", GameType.SURVIVAL);
        var origin = test.absolutePos(new BlockPos(5, 3, 5));
        var site = new BlockPos(interior(origin.getX()), origin.getY(), interior(origin.getZ()));
        var second = site.above(4);
        try {
            var firstMarker = naturalHull(level, site);
            var secondMarker = naturalHull(level, second);
            var ledger = Rac1GearLedger.get(level);
            a.gameMode.destroyBlock(site.west());
            test.assertTrue(Rac1PodFootprint.complete(level, site), "Creative broke an unclaimed hull");
            useWing(level, a, site);
            useWing(level, b, site);
            test.assertTrue(firstMarker.isClaimed() && Rac1ClankItem.rewardId(a.getMainHandItem()) != null,
                    "First valid use did not claim and deliver");
            test.assertTrue(!ledger.record(b.getUUID()).back().acquired(), "Second player got the same rescue");
            useWing(level, a, second);
            test.assertTrue(!secondMarker.isClaimed(), "Already-unlocked player consumed a second site");
            for (int i = 0; i < 36; i++) b.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            useWing(level, b, second);
            test.assertTrue(secondMarker.isClaimed() && ledger.pendingRescue(b.getUUID()) != null,
                    "Full inventory did not make one persistent pending rescue");

            b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_PICKAXE));
            b.getMainHandItem().setDamageValue(b.getMainHandItem().getMaxDamage() - 1);
            b.gameMode.destroyBlock(site.west());
            assertEmptyHull(test, site);
            var drops = decorativeDrops(level, site);
            test.assertTrue(b.getMainHandItem().isEmpty(), "Native pickaxe did not wear out");
            test.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
                    "Last-use golden pickaxe did not recover exactly one pod");
            // Hold the one recovered item (other site and the Gear token remain independent).
            b.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
            drops.getFirst().discard();
            var floor = site.below();
            level.setBlock(site.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            placePod(level, b, floor);
            test.assertTrue(level.getBlockState(site).isAir() && b.getMainHandItem().getCount() == 1,
                    "Obstruction did not deny placement without consuming the item");
            level.removeBlock(site.east(), false);
            Consumer<BlockEvent.EntityMultiPlaceEvent> veto = event -> {
                if (event.getEntity() == b) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(veto);
            try {
                placePod(level, b, floor);
                assertEmptyHull(test, site);
                test.assertTrue(b.getMainHandItem().getCount() == 1 && level.getBlockEntity(site) == null,
                        "Multi-place veto failed to roll back stack or marker");
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }
            placePod(level, b, floor);
            test.assertTrue(Rac1PodFootprint.complete(level, site)
                    && level.getBlockEntity(site) instanceof Rac1PodMarker marker
                    && marker.isDecorative() && !marker.isNatural() && b.getMainHandItem().isEmpty(),
                    "Recovered item did not place just one empty hull");
            b.gameMode.destroyBlock(site.east());
            assertEmptyHull(test, site);
            test.assertTrue(decorativeDrops(level, site).isEmpty(), "Bare hand produced a decorative drop");
            var token = a.getMainHandItem();
            a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Rac1PodRegistration.DECORATIVE.get()));
            placePod(level, a, floor);
            test.assertTrue(Rac1PodFootprint.complete(level, site) && a.getMainHandItem().getCount() == 1,
                    "Creative placement consumed an item or failed");
            a.gameMode.destroyBlock(site);
            assertEmptyHull(test, site);
            test.assertTrue(decorativeDrops(level, site).isEmpty(), "Creative dropped a decorative item");
            a.setItemInHand(InteractionHand.MAIN_HAND, token);
            // Leave the second claimed hull in this disposable world for a normal restart probe.
            System.out.printf("P40_SAVED_WRECK %d %d %d%n", second.getX(), second.getY(), second.getZ());
        } finally {
            for (var player : java.util.List.of(a, b)) {
                Rac1GearServer.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                index.remove(player.getUUID());
                player.discard();
            }
        }
        test.succeed();
    }

    private static int interior(int coordinate) {
        return (coordinate & ~15) + Math.clamp(Math.floorMod(coordinate, 16), 2, 13);
    }

    private static Rac1PodMarker naturalHull(ServerLevel level, BlockPos anchor) {
        var blocks = BuiltInRegistries.BLOCK.asLookup();
        for (int i = 0; i < Rac1PodFootprint.CELLS.size(); i++) {
            var pos = Rac1PodFootprint.CELLS.get(i).at(anchor);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var tag = new CompoundTag();
            tag.putString("Name", i == 0 ? "racmc:crashed_pod" : "racmc:crashed_pod_hull");
            var props = new CompoundTag();
            if (i == 0) props.putString("full", "true");
            else props.putString("part", Integer.toString(i));
            tag.put("Properties", props);
            level.setBlock(pos, NbtUtils.readBlockState(blocks, tag), Block.UPDATE_CLIENTS);
        }
        var marker = (Rac1PodMarker) level.getBlockEntity(anchor);
        var nbt = new CompoundTag();
        nbt.putLong("NaturalAnchor", anchor.asLong());
        marker.loadWithComponents(nbt, level.registryAccess());
        return marker;
    }

    private static void useWing(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        player.setPos(anchor.getX() - 1.8, anchor.getY(), anchor.getZ() + .5);
        var hit = new BlockHitResult(Vec3.atLowerCornerWithOffset(anchor, -1, .5, .5),
                Direction.WEST, anchor.west(), false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static void placePod(ServerLevel level, ServerPlayer player, BlockPos floor) {
        player.setPos(floor.getX() - 2, floor.getY() + 1, floor.getZ() + .5);
        var hit = new BlockHitResult(Vec3.atLowerCornerWithOffset(floor, .5, 1, .5), Direction.UP, floor, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static java.util.List<ItemEntity> decorativeDrops(ServerLevel level, BlockPos site) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(site).inflate(7),
                item -> item.isAlive() && item.getItem().is(Rac1PodRegistration.DECORATIVE.get()));
    }

    private static void assertEmptyHull(GameTestHelper test, BlockPos site) {
        for (var cell : Rac1PodFootprint.CELLS) {
            test.assertTrue(test.getLevel().getBlockState(cell.at(site)).isAir(), "Hull left a constituent behind");
        }
    }
}
