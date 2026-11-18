package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.Rac1BlasterServer;
import com.github.giannigr.racmc.sound.RangedFireSignal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-owned accepted-session audio, independent of the held-empty animation product policy. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class RangedFireSessions {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static long revision;
    private static final class Session {
        final ServerPlayer player;
        final Object world;
        final ItemStack weapon;
        final UUID id = UUID.randomUUID();
        final int slot;
        long lastShot;
        Session(ServerPlayer player) {
            this.player = player; world = player.level(); weapon = player.getMainHandItem();
            slot = player.getInventory().selected;
        }
        void stop() { RangedFireSignal.emit(player, id, ++revision, false); }
    }
    private RangedFireSessions() {}
    public static void accepted(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
            reject(player);
            return;
        }
        Session session = ACTIVE.get(player.getUUID());
        if (session != null && (session.player != player || session.world != player.level()
                || session.weapon != player.getMainHandItem() || session.slot != player.getInventory().selected)) {
            session.stop(); ACTIVE.remove(player.getUUID()); session = null;
        }
        if (session == null) {
            session = new Session(player);
            ACTIVE.put(player.getUUID(), session);
        }
        session.lastShot = player.level().getGameTime();
        RangedFireSignal.emit(player, session.id, ++revision, true);
    }
    public static void reject(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        // A delayed old-entity callback must not stop a replacement player's new session.
        if (session != null && session.player == player) {
            ACTIVE.remove(player.getUUID());
            session.stop();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        ACTIVE.values().removeIf(session -> {
            var player = session.player;
            boolean stop = player.isRemoved() || !player.isAlive() || player.level() != session.world
                    || player.getMainHandItem() != session.weapon || player.getInventory().selected != session.slot
                    || !Rac1BlasterServer.isFiring(player)
                    || player.level().getGameTime() - session.lastShot > 2;
            if (stop) session.stop();
            return stop;
        });
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); revision = 0; }
}
