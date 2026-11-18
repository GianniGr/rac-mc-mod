package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1ClankNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1ClankClient {
    static final Rac1ClankViews VIEWS = new Rac1ClankViews();
    private Rac1ClankClient() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) return;
        Rac1ClankResources.update(VIEWS.anyWorn());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEWS.clear(); Rac1ClankResources.clear();
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) VIEWS.forget(event.getEntity().getUUID());
    }

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> Rac1ClankNetwork.receiver(snapshot -> {
                var level = Minecraft.getInstance().level;
                if (level != null) VIEWS.accept(snapshot, level.getGameTime());
            }));
        }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new Rac1ClankLayer(renderer));
            }
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Rac1ClankResources.reload());
        }
    }
}
