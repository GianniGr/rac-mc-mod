package com.github.giannigr.racmc.client.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1BeltGadget;
import com.github.giannigr.racmc.gear.Rac1BeltNetwork;
import com.github.giannigr.racmc.gear.Rac1BeltState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1BeltClient {
    private static final Rac1BeltView VIEW = new Rac1BeltView();

    private Rac1BeltClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Rac1BeltNetwork.receiver((state, context) -> {
            var current = current();
            if (current != null && current.connection() == context.connection()
                    && current.player() == context.player()) VIEW.receive(current, state);
        }));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEW.clear();
    }

    static Rac1BeltState selfState() {
        return VIEW.state(current());
    }

    static void request(Rac1BeltGadget gadget, boolean equip) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        var state = selfState();
        if (state == null) return;
        var slot = state.slot(gadget);
        if (slot.canRequest(equip)) PacketDistributor.sendToServer(
                new Rac1BeltNetwork.Equip(gadget, state.epoch(), slot.revision(), equip));
    }

    private static Rac1BeltView.Context current() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() != player.connection
                || player.level() != mc.level || !player.connection.getConnection().isConnected()) return null;
        return new Rac1BeltView.Context(player.connection.getConnection(), mc.level, player);
    }
}
