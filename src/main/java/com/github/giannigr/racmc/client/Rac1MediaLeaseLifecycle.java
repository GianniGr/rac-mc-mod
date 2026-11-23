package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Shared lease invalidation only; no feature, asset import, screen, sound or cache dependency. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1MediaLeaseLifecycle {
    private Rac1MediaLeaseLifecycle() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RacMcClient.media().invalidateLeases();
    }
    @EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> RacMcClient.media().invalidateLeases());
        }
    }
}
