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
    /** Read-only presentation access; does not expose the mutable observer store. */
    public static com.github.giannigr.racmc.clank.Rac1ClankSession.Snapshot view(java.util.UUID actor) {
        return VIEWS.get(actor);
    }
    public static boolean fresh(java.util.UUID actor,long localTick) {
        var view=VIEWS.get(actor);
        return view!=null&&VIEWS.serverTime(actor,localTick)-view.serverTick()<=4;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Rac1ClankResources.update(true); // Optional prewarm only after the existing verified-asset gate.
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEWS.clear(); Rac1ClankResources.clear(); Rac1ClankPrediction.clear();Rac1ClankPlayback.clear();
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            VIEWS.forget(event.getEntity().getUUID());Rac1ClankPlayback.forget(event.getEntity().getUUID());
        }
    }

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Registration {
        private static Rac1ClankItemRenderer itemRenderer;
        private Registration() {}
        @SubscribeEvent public static void items(
                net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
            event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if(itemRenderer==null)itemRenderer=new Rac1ClankItemRenderer();
                    return itemRenderer;
                }
            },com.github.giannigr.racmc.item.RacMcItems.CLANK_HELI_PACK.get(),
                    com.github.giannigr.racmc.item.RacMcItems.CLANK_THRUSTER_PACK.get());
        }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                Rac1ClankPrediction.install();
                Rac1ClankNetwork.receiver(snapshot -> {
                var level = Minecraft.getInstance().level;
                if (level != null) VIEWS.accept(snapshot, level.getGameTime());
                });
            });
        }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            Rac1HydroAttachmentSampler.install(event.getContext());
            for (var skin : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new Rac1ClankLayer(renderer));
            }
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(() -> {
                Rac1ClankResources.reload();Rac1ClankPlayback.clear();
            }));
        }
    }
}
