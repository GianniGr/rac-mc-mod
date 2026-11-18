package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.render.Rac1VendorDrawing;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Release vendor CPU/GPU resources on cache loss/change even when no vendor is visible. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1VendorClient {
    private static Rac1CacheContent source;

    private Rac1VendorClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Rac1CacheContent current = RacMcClient.assets().content().orElse(null);
        if (source != current) {
            Rac1VendorDrawing.clear();
            source = current;
        }
    }
}
