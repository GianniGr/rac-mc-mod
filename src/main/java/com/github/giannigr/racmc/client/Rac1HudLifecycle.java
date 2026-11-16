package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.render.Rac1HudTextures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1HudLifecycle {
    private Rac1HudLifecycle() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        Rac1HudTextures.release();
        Rac1BoltHud.reset();
    }
}
