package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.sound.Rac1AudioEvent;
import com.github.giannigr.racmc.sound.Rac1AudioSignal;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Cosmetic adapter: inventory, firing and ammo collection remain owned by their native paths. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class BlasterEquipAudio {
    private record Observation(ResourceKey<Level> dimension, BlasterEquipTransition transition) {}
    private static final Map<ServerPlayer, Observation> PLAYERS = new IdentityHashMap<>();

    private BlasterEquipAudio() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
            PLAYERS.remove(player);
            return;
        }
        var dimension = player.level().dimension();
        var observation = PLAYERS.get(player);
        if (observation == null || !dimension.equals(observation.dimension())) {
            observation = new Observation(dimension, new BlasterEquipTransition());
            PLAYERS.put(player, observation);
        }
        var cue = observation.transition().observe(player.getMainHandItem().is(RacMcItems.BLASTER.get()));
        switch (cue) {
            case START -> Rac1AudioSignal.emitTo(player, player.position(), Rac1AudioEvent.BLASTER_EQUIP_START);
            case FINISH -> Rac1AudioSignal.emitTo(player, player.position(), Rac1AudioEvent.BLASTER_EQUIP_FINISH);
            case NONE -> { }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PLAYERS.remove(player);
    }

    @SubscribeEvent
    public static void replaced(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) PLAYERS.remove(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PLAYERS.clear();
    }
}
