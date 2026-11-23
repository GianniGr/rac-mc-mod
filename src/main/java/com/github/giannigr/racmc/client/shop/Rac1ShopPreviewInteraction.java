package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.client.RacMcClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client expectation/reload hooks; only the accepted server nonce opens the shop. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ShopPreviewInteraction {
    private Rac1ShopPreviewInteraction() {}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        Rac1ShopClientPackets.clear();
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof Rac1ShopPreviewScreen) mc.setScreen(null);
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) return;
        var mc=Minecraft.getInstance();
        if (event.getEntity()!=mc.player || mc.screen!=null || mc.player==null || !mc.player.isAlive()
                || mc.player.isSpectator() || !mc.player.canInteractWithBlock(event.getPos(),0)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof Rac1VendorBlockEntity vendor)) return;
        // Consume interaction even without media; never fall through into block-item placement.
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        var content=RacMcClient.assets().content().orElse(null);
        var path=RacMcClient.media().configuredPath();
        if (content==null || !RacMcClient.assets().isReady() || !RacMcClient.media().isReady() || path.isEmpty()) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Gadgetron preview needs the validated selected disc and ready asset cache."),true);
            return;
        }
        Rac1ShopClientPackets.expect(event.getPos()); // server accepts the vanilla interaction and issues the nonce
    }
    @EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(Rac1ShopClientPackets::install);
        }
        @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                var mc=Minecraft.getInstance();
                if (mc.screen instanceof Rac1ShopPreviewScreen) mc.setScreen(null);
            });
        }
    }
}
