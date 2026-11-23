package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1GearServer;
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

/** Effective worn equipment and movement synchronization; not the canonical unlock store. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1ClankServer {
    private static final Map<UUID, Rac1ClankSession> SESSIONS = new HashMap<>();
    private static long nextEpoch;
    private Rac1ClankServer() {}
    private static long epoch() { return nextEpoch = Math.incrementExact(nextEpoch); }
    static Rac1ClankSession session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(),
                id -> new Rac1ClankSession(id, epoch(), Rac1ClankEquipment.EMPTY));
    }
    static void intent(ServerPlayer player, Rac1ClankSession.Intent intent) {
        session(player).intent(intent); // Held owner policy: always denied. No broadcast amplification.
    }
    private static Rac1ClankNetwork.Snapshot packet(ServerPlayer player) {
        return new Rac1ClankNetwork.Snapshot(session(player).snapshot(player.serverLevel().getGameTime()));
    }
    static void broadcast(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, packet(player));
    }
    static void cancelEpisode(ServerPlayer player) {
        Rac1ClankRuntime.invalidate(player);
        session(player).invalidate(epoch());
        broadcast(player);Rac1ClankRuntime.correct(player);
    }
    /** Permission is checked here as well as in Brigadier; no client packet grants equipment. */
    public static boolean configureTest(CommandSourceStack source, ServerPlayer target,
            Rac1ClankEquipment.Module module) {
        if (!source.hasPermission(2) || !target.isAlive() || target.isRemoved()) return false;
        if (module == Rac1ClankEquipment.Module.NONE) clearFixture(target);
        else applySessionEquipment(target,module);
        return true;
    }
    public static Rac1ClankEquipment.Module fixture(ServerPlayer target) {
        var state = SESSIONS.get(target.getUUID());
        return state == null ? Rac1ClankEquipment.Module.NONE : state.fixture();
    }
    /** Dismiss only a current temporary fixture; restore the already committed physical projection. */
    public static void clearFixture(ServerPlayer target) {
        if (fixture(target) == Rac1ClankEquipment.Module.NONE) return;
        applyGearBack(target);
        Rac1GearServer.fixtureChanged(target);
    }
    /** Native item-use path derives the module from the actual server-held registered item only. */
    public static boolean equipCreative(ServerPlayer player,net.minecraft.world.InteractionHand hand) {
        if(player==null||hand==null||!player.isCreative()||player.isSpectator()
                ||!player.isAlive()||player.isRemoved())return false;
        var held=player.getItemInHand(hand);
        Rac1ClankEquipment.Module module;
        if(held.is(com.github.giannigr.racmc.item.RacMcItems.CLANK_HELI_PACK.get()))
            module=Rac1ClankEquipment.Module.HELI;
        else if(held.is(com.github.giannigr.racmc.item.RacMcItems.CLANK_THRUSTER_PACK.get()))
            module=Rac1ClankEquipment.Module.THRUSTER;
        else return false;
        var state=session(player);
        var current=state.equipment();
        // Repeated native right-click of an already selected pack must not restart an episode.
        if(state.testHydro()&&current.worn()&&current.selected()==module)return true;
        applySessionEquipment(player,module);
        return true;
    }
    private static void applySessionEquipment(ServerPlayer target,Rac1ClankEquipment.Module module) {
        Rac1ClankRuntime.invalidate(target);
        var state = session(target);
        state.testEquipment(module);
        syncEquipmentChange(target, state);
        Rac1GearServer.fixtureChanged(target);
    }
    /** Applies only the Back slot already committed by the server-owned Gear path. */
    public static void applyGearBack(ServerPlayer target) {
        Rac1ClankRuntime.invalidate(target);
        var state = session(target);
        state.gearEquipment(Rac1GearServer.effectiveBack(target));
        syncEquipmentChange(target, state);
    }
    private static void syncEquipmentChange(ServerPlayer target, Rac1ClankSession state) {
        state.invalidate(epoch());
        Rac1HydroRuntime.equipmentChanged(target);
        broadcast(target);
        Rac1ClankRuntime.correct(target);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Rac1HydroRuntime.forget(player);
            SESSIONS.put(player.getUUID(), new Rac1ClankSession(player.getUUID(), epoch(),
                    Rac1GearServer.effectiveBack(player)));
            broadcast(player);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, packet(target));
            Rac1HydroRuntime.tracking(observer,target);
        }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Rac1ClankRuntime.invalidate(player);
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Rac1HydroRuntime.forget(player);
            Rac1ClankRuntime.invalidate(player);
            session(player).gearEquipment(Rac1GearServer.effectiveBack(player));
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Rac1HydroRuntime.forget(player);
            Rac1ClankRuntime.invalidate(player);
            session(player).gearEquipment(Rac1ClankEquipment.EMPTY);
            session(player).invalidate(epoch());
            broadcast(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)Rac1HydroRuntime.forget(player);
        if(event.getEntity() instanceof ServerPlayer player)Rac1ClankRuntime.invalidate(player);
        SESSIONS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        Rac1HydroRuntime.clear();
        Rac1ClankRuntime.clear(); SESSIONS.clear();
    }
}
