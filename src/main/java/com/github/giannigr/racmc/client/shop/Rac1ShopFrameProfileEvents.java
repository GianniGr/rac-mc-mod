package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ShopFrameProfileEvents {
    private static long tickStart;
    private Rac1ShopFrameProfileEvents() {}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(RenderFrameEvent.Pre event) {
        Rac1ShopFrameProfile.frameStart();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(RenderFrameEvent.Post event) {
        Rac1ShopFrameProfile.frameEnd();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preTick(ClientTickEvent.Pre event) {
        tickStart=Rac1ShopFrameProfile.begin();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void postTick(ClientTickEvent.Post event) {
        Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.CLIENT_TICK,tickStart);
        tickStart=0;
    }
}
