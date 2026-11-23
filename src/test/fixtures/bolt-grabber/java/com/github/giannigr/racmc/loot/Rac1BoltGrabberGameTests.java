package com.github.giannigr.racmc.loot;

import com.mojang.authlib.GameProfile;
import com.github.giannigr.racmc.gear.Rac1BeltGadget;
import com.github.giannigr.racmc.gear.Rac1BeltNetwork;
import com.github.giannigr.racmc.gear.Rac1BeltServer;
import com.github.giannigr.racmc.gear.Rac1BeltState;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Opt-in, isolated server fixture. Real Belt requests and ledger; no remote network client. */
@GameTestHolder("racmc_bolt_grabber")
@PrefixGameTestTemplate(false)
public final class Rac1BoltGrabberGameTests {
    private Rac1BoltGrabberGameTests() {}

    @GameTest(template = "empty")
    public static void realBeltAdmitsOrdinaryBoltAndOnlyArrivalCredits(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(1, 5, 1));
        Vec3 origin = Vec3.atBottomCenterOf(base);
        var belt = new Actor(level, "bolt-collector");
        var plain = new Actor(level, "bolt-rival");
        var collector = belt.player;
        var rival = plain.player;
        var ledger = Rac1BoltLedger.get(level);
        try {
            collector.setPos(origin.add(8, 0, 0));
            rival.setPos(origin.add(4, 0, 0)); // Closer, but outside baseline range.
            collector.getInventory().setItem(0, new ItemStack(RacMcItems.BOLT_GRABBER.get()));
            Rac1BeltServer.tick(new PlayerTickEvent.Post(collector));
            var bolt = idleBolt(h, base);
            UUID id = bolt.getUUID();
            bolt.tick();
            h.assertTrue(!Rac1BeltServer.isEquipped(collector, Rac1BeltGadget.BOLT_GRABBER)
                    && ledger.pickup(id).motion().target() == null, "Loose inventory item extended range");
            Vec3 position = bolt.position();
            // Ordinary admission and homing are allowed through walls.
            level.setBlockAndUpdate(base.east(2), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.east(2).above(), Blocks.STONE.defaultBlockState());
            belt.equip(true);
            h.assertTrue(Rac1BeltServer.isEquipped(collector, Rac1BeltGadget.BOLT_GRABBER)
                    && !Rac1BeltServer.isEquipped(rival, Rac1BeltGadget.BOLT_GRABBER),
                    "Belt did not expose the confirmed player's equipment");
            bolt.tick();
            h.assertTrue(collector.getUUID().equals(ledger.pickup(id).motion().target())
                    && bolt.position().x > position.x && ledger.balance(collector.getUUID()) == 0,
                    "Ordinary flight did not start, or credited before arrival");
            Rac1BoltEntity.attractFromReturningWrench(rival, bolt.position());
            h.assertTrue(collector.getUUID().equals(ledger.pickup(id).motion().target()),
                    "Returning wrench stole ordinary flight");
            belt.equip(false);
            h.assertTrue(!Rac1BeltServer.isEquipped(collector, Rac1BeltGadget.BOLT_GRABBER),
                    "Unequip did not clear admission range");
            collector.setPos(origin.add(9, 1, 0)); // The lock follows the player's new body position.
            var newBolt = idleBolt(h, base);
            newBolt.tick();
            h.assertTrue(ledger.pickup(newBolt.getUUID()).motion().target() == null,
                    "Unequipped player retained boosted admission");
            for (int i = 0; i < 60 && !bolt.isRemoved(); i++) bolt.tick();
            bolt.tick();
            ledger.emit(level);
            h.assertTrue(ledger.pickup(id) == null && bolt.isRemoved()
                    && ledger.balance(collector.getUUID()) == 1 && ledger.revision(collector.getUUID()) == 1
                    && ledger.balance(rival.getUUID()) == 0 && ledger.revision(rival.getUUID()) == 0
                    && level.getEntity(id) == null && collector.totalExperience == 0,
                    "An admitted flight was lost, recreated, or credited more than once");
            // Wrench return still independently locks a plain player, even after rival re-equips.
            belt.equip(true);
            Rac1BoltEntity.attractFromReturningWrench(rival, newBolt.position());
            newBolt.tick();
            h.assertTrue(rival.getUUID().equals(ledger.pickup(newBolt.getUUID()).motion().target()),
                    "Ordinary boost stole an already-admitted wrench return lock");
            for (int i = 0; i < 60 && !newBolt.isRemoved(); i++) newBolt.tick();
            h.assertTrue(newBolt.isRemoved() && ledger.balance(rival.getUUID()) == 1
                    && ledger.revision(rival.getUUID()) == 1 && ledger.balance(collector.getUUID()) == 1,
                    "Wrench arrival failed to credit exactly its original target");
            belt.equip(false);
            h.succeed();
        } finally {
            plain.close();
            belt.close();
        }
    }

    private static Rac1BoltEntity idleBolt(GameTestHelper h, BlockPos base) {
        var level = h.getLevel();
        var ledger = Rac1BoltLedger.get(level);
        Set<UUID> old = ids(h);
        ledger.reward(UUID.randomUUID(), level, base, 1); // Non-greedy payout yields exactly one view.
        Set<UUID> created = ids(h);
        created.removeAll(old);
        h.assertTrue(created.size() == 1, "Expected exactly one ordinary bolt from fixture reward");
        UUID id = created.iterator().next();
        Vec3 p = Vec3.atBottomCenterOf(base);
        ledger.move(id, Rac1BoltMotion.legacy(new V(p.x, p.y, p.z)));
        ledger.emit(level);
        h.assertTrue(level.getEntity(id) instanceof Rac1BoltEntity, "Bolt fixture was not admitted");
        return (Rac1BoltEntity) level.getEntity(id);
    }

    private static Set<UUID> ids(GameTestHelper h) {
        var level = h.getLevel();
        var ids = new HashSet<UUID>();
        var save = Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess());
        for (Tag entry : save.getList("pickups", Tag.TAG_COMPOUND)) ids.add(((CompoundTag) entry).getUUID("id"));
        return ids;
    }

    private static final class Actor {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private Rac1BeltState state;

        private Actor(ServerLevel level, String name) {
            var profile = new GameProfile(UUID.randomUUID(), name);
            var client = ClientInformation.createDefault();
            player = new ServerPlayer(level.getServer(), level, profile, client);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player,
                    new CommonListenerCookie(profile, 0, client, false, ConnectionType.NEOFORGE));
        }

        private void equip(boolean equipped) {
            channel.runPendingTasks();
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof Rac1BeltNetwork.Snapshot snapshot) state = snapshot.state();
            }
            var gadget = Rac1BeltGadget.BOLT_GRABBER;
            if (state == null) throw new AssertionError("Missing server Belt snapshot");
            var request = new Rac1BeltNetwork.Equip(gadget, state.epoch(), state.slot(gadget).revision(), equipped);
            NetworkRegistry.handleModdedPayload(player.connection, new ServerboundCustomPayloadPacket(request));
        }

        private void close() {
            player.serverLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
