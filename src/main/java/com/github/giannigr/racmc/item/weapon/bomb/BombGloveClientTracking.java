package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Renderer API. No local prediction, imported assets, or presentation playback side effects. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class BombGloveClientTracking {
    private static ClientLevel world;
    private static BombGloveTracking.View view;

    private BombGloveClientTracking() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BombGloveTrackingNetwork.installClient(
                    BombGloveClientTracking::receiveHeld, BombGloveClientTracking::observeFlight);
            NeoForge.EVENT_BUS.addListener(BombGloveClientTracking::logout);
        });
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        world = null;
        view = null;
    }

    private static @Nullable BombGloveTracking.View view(Level source) {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            world = current;
            view = current == null ? null : new BombGloveTracking.View(current.dimension().location());
        }
        return source == world ? view : null;
    }

    private static void receiveHeld(Level source, BombGloveTracking.Held snapshot) {
        var current = view(source);
        if (current != null) current.held(snapshot);
    }

    private static void observeFlight(Rac1Bomb bomb) { flight(bomb); }

    /** Null means the held bomb must not be drawn. Use phase and phaseTick for pose interpolation. */
    public static @Nullable BombGloveTracking.Held heldBomb(Player player) {
        var current = view(player.level());
        return current == null || player.isRemoved() || !player.isAlive() || player.isSpectator()
                || !player.getMainHandItem().is(RacMcItems.BOMB_GLOVE.get())
                ? null : current.heldBomb(player.getUUID(), player.getId());
    }

    /** Server-observed phase/tick for client choreography; never a replacement for heldBomb(). */
    public static @Nullable BombGloveTracking.VisualPhase visualPhase(Player player) {
        var current = view(player.level());
        return current == null ? null : current.visualPhase(player.getUUID(), player.getId());
    }

    /** Null means this native projectile is not a drawable committed flight. */
    public static @Nullable BombGloveTracking.Commit flight(Rac1Bomb bomb) {
        var current = view(bomb.level());
        var commit = bomb.committedThrow();
        if (current == null || commit == null
                || !commit.key().dimension().equals(bomb.level().dimension().location())) return null;
        current.committed(commit);
        return bomb.isRemoved() ? null : commit;
    }
}
