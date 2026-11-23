package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Held;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Key;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveTracking.Phase;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/** Presentation snapshots only. Called from the existing authoritative throw lifecycle. */
final class BombGloveTrackingServer {
    private static final Map<ServerPlayer, Held> HELD = new IdentityHashMap<>();
    private static UUID epoch = UUID.randomUUID();
    private static long sequence;

    private BombGloveTrackingServer() {}

    static @Nullable Held snapshot(ServerPlayer player) {
        Held held = HELD.get(player);
        return held != null && held.key().dimension().equals(player.level().dimension().location()) ? held : null;
    }

    private static Key key(ServerPlayer player, long id) {
        return new Key(player.getUUID(), player.level().dimension().location(), epoch, id);
    }

    private static void publish(ServerPlayer player, Key key, Phase phase) {
        var held = new Held(key, player.getId(), ++sequence, phase, player.level().getGameTime());
        HELD.put(player, held);
        BombGloveTrackingNetwork.broadcast(player, held);
    }

    static Key begin(ServerPlayer player) {
        Held before = snapshot(player);
        Key key = before != null && before.phase() == Phase.READY ? before.key() : key(player, ++sequence);
        publish(player, key, Phase.WINDUP);
        return key;
    }

    static void committed(ServerPlayer player, Key key, Rac1Bomb bomb) {
        bomb.commitThrow(new BombGloveTracking.Commit(key, player.level().getGameTime()));
        publish(player, key, Phase.RELEASED);
    }

    static void abort(ServerPlayer player, Key key) {
        Held before = snapshot(player);
        if (before != null && before.phase() == Phase.WINDUP && before.key().equals(key))
            publish(player, key, Phase.ABORTED);
    }

    static void idle(ServerPlayer player) {
        Held before = snapshot(player);
        boolean heldGlove = player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.getMainHandItem().is(RacMcItems.BOMB_GLOVE.get());
        if (heldGlove && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().is(RacMcItems.BOMB_GLOVE.get()) && before != null
                && (before.phase() == Phase.ABORTED || before.phase() == Phase.RELEASED)) return;
        boolean ready = heldGlove && !player.isUsingItem()
                && !player.getCooldowns().isOnCooldown(RacMcItems.BOMB_GLOVE.get())
                && (player.getAbilities().instabuild || BombGloveAmmo.count(player) > 0);
        if (ready) {
            if (before == null || before.phase() != Phase.READY) publish(player, key(player, ++sequence), Phase.READY);
        } else if (before != null && before.phase() != Phase.HIDDEN) {
            publish(player, key(player, 0), Phase.HIDDEN);
        }
    }

    static void forget(ServerPlayer player) { HELD.remove(player); }

    static void stopped() {
        HELD.clear();
        epoch = UUID.randomUUID();
        sequence = 0;
    }
}
