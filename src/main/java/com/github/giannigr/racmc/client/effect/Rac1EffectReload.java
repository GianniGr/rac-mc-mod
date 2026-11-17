package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class Rac1EffectReload {
    private Rac1EffectReload() {}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> Rac1EffectClient.reload());
    }
}
