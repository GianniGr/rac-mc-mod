package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lifecycle/synchronization foundation. Runtime sessions start EMPTY; no grant/load/clone
 * persistence policy is chosen here. Durable save codec is available for later approved wiring.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1ClankServer {
    private static final Map<UUID, Rac1ClankSession> SESSIONS = new HashMap<>();
    private static long nextEpoch;
    private Rac1ClankServer() {}
    private static long epoch() { return nextEpoch = Math.incrementExact(nextEpoch); }
    private static Rac1ClankSession session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(),
                id -> new Rac1ClankSession(id, epoch(), Rac1ClankEquipment.EMPTY));
    }
    static void intent(ServerPlayer player, Rac1ClankSession.Intent intent) {
        session(player).intent(intent); // Held owner policy: always denied. No broadcast amplification.
    }
    private static Rac1ClankNetwork.Snapshot packet(ServerPlayer player) {
        return new Rac1ClankNetwork.Snapshot(session(player).snapshot(player.serverLevel().getGameTime()));
    }
    private static void broadcast(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, packet(player));
    }
    /** Permission is checked here as well as in Brigadier; no client packet grants equipment. */
    public static boolean configureTest(CommandSourceStack source, ServerPlayer target,
            Rac1ClankEquipment.Module module) {
        if (!source.hasPermission(2) || !target.isAlive() || target.isRemoved()) return false;
        var state = session(target);
        state.testEquipment(module);
        state.invalidate(epoch());
        broadcast(target);
        return true;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.put(player.getUUID(), new Rac1ClankSession(player.getUUID(), epoch(),
                    Rac1ClankEquipment.EMPTY));
            broadcast(player);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target)
            PacketDistributor.sendToPlayer(observer, packet(target));
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            session(player).testEquipment(Rac1ClankEquipment.Module.NONE);
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            session(player).testEquipment(Rac1ClankEquipment.Module.NONE);
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }
}
