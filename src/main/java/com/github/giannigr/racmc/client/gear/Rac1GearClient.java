package com.github.giannigr.racmc.client.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1GearNetwork;
import com.github.giannigr.racmc.gear.Rac1GearState;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1GearClient {
    private static final Rac1GearView VIEW = new Rac1GearView();

    private Rac1GearClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Rac1GearNetwork.selfReceiver(Rac1GearClient::receive));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEW.clear();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        VIEW.state(current());
    }

    /** Empty until an authoritative self snapshot for this connection/level/player is ready. */
    public static Optional<Rac1GearState> selfState() {
        return VIEW.state(current());
    }

    static void requestBack(Rac1GearState shown, boolean worn) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || !player.isAlive()) return;
        selfState().filter(state -> state.equals(shown) && state.allowsBackRequest(worn))
                .ifPresent(state -> PacketDistributor.sendToServer(
                        new Rac1GearNetwork.EquipBack(state.epoch(), state.revision(), worn)));
    }

    private static void receive(Rac1GearState state, IPayloadContext context) {
        var current = current();
        if (current != null && current.connection() == context.connection() && current.player() == context.player())
            VIEW.accept(current, state);
    }

    private static Rac1GearView.Context current() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() != player.connection
                || player.level() != mc.level || !player.connection.getConnection().isConnected()) return null;
        return new Rac1GearView.Context(player.connection.getConnection(), mc.level, player);
    }
}
