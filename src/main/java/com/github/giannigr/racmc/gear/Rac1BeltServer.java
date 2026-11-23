package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1BeltState.Availability;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BeltServer {
    private static final Rac1BeltGadget BOLT_GRABBER = Rac1BeltGadget.BOLT_GRABBER;
    private static final Map<UUID, Scope> SCOPES = new HashMap<>();
    private static long nextEpoch;
    private static long nextRevision;

    private static final class Scope {
        final ServerPlayer player;
        final Rac1BeltLedger ledger;
        final long epoch;
        Rac1BeltState last;
        int retryDelay;

        Scope(ServerPlayer player) {
            this.player = player;
            ledger = Rac1BeltLedger.get(player.serverLevel());
            epoch = nextEpoch = Math.incrementExact(nextEpoch);
        }
    }

    private Rac1BeltServer() {}

    /** Read-only server-thread seam. Never resolves a save, opens a chunk or reads inventory. */
    public static boolean isEquipped(ServerPlayer player, Rac1BeltGadget gadget) {
        if (!live(player) || gadget == null) return false;
        var scope = scope(player);
        return scope != null && scope.ledger.equipped(player.getUUID(), gadget);
    }

    static void request(ServerPlayer player, Rac1BeltNetwork.Equip request) {
        if (!live(player)) return;
        var scope = scope(player);
        if (scope == null || !scope.ledger.available()
                || player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !player.containerMenu.getCarried().isEmpty()) return;
        publish(scope); // Re-read actual inventory before accepting a revision.
        if (!accepts(scope.last, request)) return;
        var gadget = request.gadget();
        if (request.equipped()) {
            if (!scope.ledger.equip(player.getUUID(), gadget, player.getInventory())) return;
            Rac1BeltWorn.refresh(player);
        } else {
            if (!scope.ledger.beginReturn(player.getUUID(), gadget)) return;
            Rac1BeltWorn.refresh(player);
            publish(scope); // Loses effect and invalidates this request before any drop callbacks.
            if (!deliver(scope, gadget)) scope.retryDelay = 20;
        }
        player.containerMenu.broadcastChanges();
        publish(scope);
    }

    static boolean accepts(Rac1BeltState current, Rac1BeltNetwork.Equip request) {
        if (current == null || current.epoch() != request.epoch()) return false;
        var slot = current.slot(request.gadget());
        return slot.revision() == request.revision() && slot.canRequest(request.equipped());
    }

    private static boolean live(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.connection != null && player.connection.isAcceptingMessages();
    }

    private static Scope scope(ServerPlayer player) {
        var scope = SCOPES.get(player.getUUID());
        return scope != null && scope.player == player ? scope : null;
    }

    private static boolean deliver(Scope scope, Rac1BeltGadget gadget) {
        var player = scope.player;
        var level = player.serverLevel();
        return scope.ledger.deliver(player.getUUID(), gadget, player.getInventory(), new Rac1BeltLedger.Delivery() {
            @Override
            public boolean alreadyDropped(UUID receipt, ItemStack stack) {
                for (var loaded : level.getServer().getAllLevels())
                    if (loaded.getEntity(receipt) instanceof ItemEntity item
                            && !item.isRemoved() && ItemStack.matches(item.getItem(), stack)) return true;
                return false;
            }

            @Override
            public boolean drop(UUID receipt, ItemStack stack) {
                var item = new ItemEntity(level, player.getX(), player.getY() + .25, player.getZ(), stack);
                item.setUUID(receipt);
                item.setDefaultPickUpDelay();
                return level.addFreshEntity(item);
            }
        });
    }

    private static void start(ServerPlayer player) {
        var scope = new Scope(player);
        SCOPES.put(player.getUUID(), scope);
        Rac1BeltWorn.refresh(player);
        publish(scope);
    }

    private static void publish(Scope scope) {
        if (!scope.ledger.available()) return;
        var old = scope.last;
        var bolts = readSlot(scope, BOLT_GRABBER);
        if (old != null && bolts == old.boltGrabber()) return;
        scope.last = new Rac1BeltState(scope.epoch, bolts);
        PacketDistributor.sendToPlayer(scope.player, new Rac1BeltNetwork.Snapshot(scope.last));
    }

    private static Rac1BeltState.Slot readSlot(Scope scope, Rac1BeltGadget gadget) {
        var player = scope.player;
        var uuid = player.getUUID();
        var availability = scope.ledger.equipped(uuid, gadget) ? Availability.EQUIPPED
                : scope.ledger.returning(uuid, gadget) ? Availability.RETURNING
                : Rac1BeltLedger.matchingSlot(player.getInventory(), gadget) >= 0 ? Availability.READY
                : Availability.MISSING;
        var prior = scope.last == null ? null : scope.last.slot(gadget);
        return prior != null && prior.availability() == availability ? prior
                : new Rac1BeltState.Slot(nextRevision = Math.incrementExact(nextRevision), availability);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !live(player)) return;
        var scope = scope(player);
        if (scope == null || !scope.ledger.available()) return;
        if (scope.retryDelay > 0) scope.retryDelay--;
        if (scope.retryDelay == 0 && scope.ledger.returning(player.getUUID(), BOLT_GRABBER)
                && !deliver(scope, BOLT_GRABBER)) scope.retryDelay = 20;
        publish(scope);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) start(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) start(player);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) start(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && scope(player) != null)
            SCOPES.remove(player.getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SCOPES.clear();
    }
}
