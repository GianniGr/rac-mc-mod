package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.clank.Rac1ClankServer;
import com.github.giannigr.racmc.item.Rac1ClankItem;
import com.github.giannigr.racmc.item.Rac1ClankItem.Stamp;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1GearServer {
    public enum RescueResult { NEWLY_UNLOCKED, ALREADY_UNLOCKED, DENIED }

    private static final class Scope {
        final ServerPlayer player;
        final long epoch;
        Rac1GearState last;
        int returnCooldown;

        Scope(ServerPlayer player, long epoch) {
            this.player = player;
            this.epoch = epoch;
        }
    }
    private static final Map<UUID, Scope> SCOPES = new HashMap<>();
    private static long nextEpoch;
    private static long nextRevision;

    private Rac1GearServer() {}

    /**
     * The only token issuance seam. Call from a validated natural pod interaction on the server
     * thread. The caller owns pod provenance, loaded marker, reach and interaction permissions.
     * Mark the site used in {@code onCommit}: this runs only after the durable entitlement
     * and its pending token exist, before inventory delivery or network publication.
     */
    public static RescueResult rescueClank(ServerPlayer player, Runnable onCommit) {
        Objects.requireNonNull(onCommit);
        if (!eligible(player) || !player.level().dimension().equals(Level.OVERWORLD))
            return RescueResult.DENIED;
        Rac1GearLedger ledger = Rac1GearLedger.get(player.serverLevel());
        RescueResult result = ledger.rescue(player.getUUID());
        if (result == RescueResult.NEWLY_UNLOCKED) {
            onCommit.run();
            deliverRescue(player.getInventory(), player.getUUID(), ledger);
            publish(scope(player), ledger);
        }
        return result;
    }

    /** Public Clank-runtime projection only: an unworn Back slot discloses no ownership. */
    public static Rac1ClankEquipment effectiveBack(ServerPlayer player) {
        return Rac1GearLedger.get(player.serverLevel()).record(player.getUUID()).effectiveBack();
    }

    static void equipBack(ServerPlayer player, Rac1GearNetwork.EquipBack request) {
        if (!eligible(player)) return;
        Scope scope = scope(player);
        if (request.epoch() != scope.epoch) return;
        Rac1GearLedger ledger = Rac1GearLedger.get(player.serverLevel());
        publish(scope, ledger);
        if (scope.last == null || request.revision() != scope.last.revision()) return;
        if (!scope.last.allowsBackRequest(request.worn())) return;
        if (scope.last.fixture() != Module.NONE) {
            // Dismiss the overlay only. A physical token beneath it stays in escrow.
            Rac1ClankServer.clearFixture(player);
            return;
        }
        if (request.worn()) {
            int slot = looseSlot(player.getInventory(), ledger);
            if (slot < 0 || takeIntoEscrow(player.getInventory(), player.getUUID(), ledger, slot, false)
                    != Rac1GearLedger.EquipResult.EQUIPPED) return;
        } else {
            if (!ledger.beginReturn(player.getUUID())) return;
        }
        Rac1ClankServer.applyGearBack(player);
        if (!request.worn()) deliverReturn(scope, ledger);
        player.containerMenu.broadcastChanges();
        publish(scope, ledger);
    }

    /** Native item use only, with no client-selected item or recipient identity. */
    public static boolean useClank(ServerPlayer player, InteractionHand hand) {
        if (!eligible(player) || hand == null) return false;
        var ledger = Rac1GearLedger.get(player.serverLevel());
        var inventory = player.getInventory();
        int slot = hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
        var result = takeIntoEscrow(inventory, player.getUUID(), ledger, slot, true);
        if (result == Rac1GearLedger.EquipResult.DENIED) return false;
        if (result == Rac1GearLedger.EquipResult.EQUIPPED) Rac1ClankServer.applyGearBack(player);
        publish(scope(player), ledger);
        return true;
    }

    static Rac1GearLedger.EquipResult takeIntoEscrow(
            Inventory inventory, UUID player, Rac1GearLedger ledger, int slot, boolean giftUse) {
        var stamp = Rac1ClankItem.rewardId(inventory.getItem(slot));
        var result = ledger.equip(player, stamp, giftUse);
        if (result == Rac1GearLedger.EquipResult.EQUIPPED) {
            inventory.setItem(slot, ItemStack.EMPTY);
            inventory.setChanged();
        }
        return result;
    }

    static int looseSlot(Inventory inventory, Rac1GearLedger ledger) {
        for (int i = 0; i < inventory.items.size(); i++)
            if (ledger.looseAuthentic(Rac1ClankItem.rewardId(inventory.getItem(i)))) return i;
        return ledger.looseAuthentic(Rac1ClankItem.rewardId(inventory.getItem(Inventory.SLOT_OFFHAND)))
                ? Inventory.SLOT_OFFHAND : -1;
    }

    private static boolean hasStamp(Inventory inventory, Stamp stamp) {
        for (var stack : inventory.items)
            if (stamp.equals(Rac1ClankItem.rewardId(stack))) return true;
        return stamp.equals(Rac1ClankItem.rewardId(inventory.getItem(Inventory.SLOT_OFFHAND)));
    }

    private static boolean insert(Inventory inventory, Stamp stamp) {
        if (hasStamp(inventory, stamp)) return true;
        int slot = inventory.getFreeSlot();
        if (slot < 0) return false;
        inventory.setItem(slot, Rac1ClankItem.reward(stamp));
        inventory.setChanged();
        return true;
    }

    static void deliverRescue(Inventory inventory, UUID player, Rac1GearLedger ledger) {
        var stamp = ledger.pendingRescue(player);
        // Natural rescues never drop a pending reward, even in Creative where Inventory.add
        // would otherwise be allowed to discard overflow.
        if (stamp != null && insert(inventory, stamp)) ledger.finishRescueDelivery(player, stamp);
    }

    @FunctionalInterface
    interface Drop {
        boolean place(Stamp stamp);
    }

    static boolean deliverReturn(Inventory inventory, UUID player, Rac1GearLedger ledger, Drop drop) {
        var stamp = ledger.pendingReturn(player);
        if (stamp == null) return true;
        if (!insert(inventory, stamp) && !drop.place(stamp)) return false;
        return ledger.finishReturn(player, stamp);
    }

    private static void deliverReturn(Scope scope, Rac1GearLedger ledger) {
        if (!deliverReturn(scope.player.getInventory(), scope.player.getUUID(), ledger,
                stamp -> spawnReturn(scope.player, stamp))) scope.returnCooldown = 20;
    }

    private static boolean spawnReturn(ServerPlayer player, Stamp stamp) {
        // Stable for every retry of this return; never expose the authentication seal itself.
        var seal = stamp.seal();
        UUID entityId = UUID.nameUUIDFromBytes(ByteBuffer.allocate(16)
                .putLong(seal.getMostSignificantBits()).putLong(seal.getLeastSignificantBits()).array());
        var level = player.serverLevel();
        for (var loaded : level.getServer().getAllLevels()) {
            var existing = loaded.getEntity(entityId);
            if (existing != null) return existing instanceof ItemEntity item
                    && !item.isRemoved() && stamp.equals(Rac1ClankItem.rewardId(item.getItem()));
        }
        var item = new ItemEntity(level, player.getX(), player.getY() + .25, player.getZ(),
                Rac1ClankItem.reward(stamp));
        item.setUUID(entityId);
        item.setDefaultPickUpDelay();
        return level.addFreshEntity(item);
    }

    private static Scope scope(ServerPlayer player) {
        Scope scope = SCOPES.get(player.getUUID());
        return scope != null && scope.player == player ? scope : null;
    }

    private static boolean eligible(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.connection != null && player.connection.isAcceptingMessages()
                && player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && scope(player) != null;
    }

    private static void startScope(ServerPlayer player) {
        Scope scope = new Scope(player, nextEpoch = Math.incrementExact(nextEpoch));
        SCOPES.put(player.getUUID(), scope);
        var ledger = Rac1GearLedger.get(player.serverLevel());
        if (eligible(player)) deliverRescue(player.getInventory(), player.getUUID(), ledger);
        publish(scope, ledger);
    }

    private static void publish(Scope scope, Rac1GearLedger ledger) {
        if (!ledger.available()) return;
        var player = scope.player;
        var record = ledger.record(player.getUUID());
        boolean carried = looseSlot(player.getInventory(), ledger) >= 0;
        boolean rescue = ledger.pendingRescue(player.getUUID()) != null;
        boolean returning = ledger.pendingReturn(player.getUUID()) != null;
        Module fixture = Rac1ClankServer.fixture(player);
        var previous = scope.last;
        if (previous != null && previous.back().equals(record.back()) && previous.clankInInventory() == carried
                && previous.rewardPending() == rescue && previous.returnPending() == returning
                && previous.fixture() == fixture) return;
        scope.last = new Rac1GearState(scope.epoch, nextRevision = Math.incrementExact(nextRevision),
                record.back(), carried, rescue, returning, fixture);
        PacketDistributor.sendToPlayer(player, new Rac1GearNetwork.Snapshot(scope.last));
    }

    /** Publish native fixture changes using the same revision guard as physical Back changes. */
    public static void fixtureChanged(ServerPlayer player) {
        if (eligible(player)) publish(scope(player), Rac1GearLedger.get(player.serverLevel()));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) return;
        var scope = scope(player);
        var ledger = Rac1GearLedger.get(player.serverLevel());
        if (scope.returnCooldown > 0) scope.returnCooldown--;
        if (scope.returnCooldown == 0 && ledger.pendingReturn(player.getUUID()) != null) deliverReturn(scope, ledger);
        deliverRescue(player.getInventory(), player.getUUID(), ledger);
        publish(scope, ledger);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) startScope(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) startScope(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) startScope(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && scope(player) != null)
            SCOPES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SCOPES.clear();
    }
}
