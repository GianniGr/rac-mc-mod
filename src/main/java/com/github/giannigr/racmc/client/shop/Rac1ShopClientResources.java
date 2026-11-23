package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.shop.*;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Shop-only resource hooks; no shared bootstrap edit, screen opening or incomplete-scene bypass. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1ShopClientResources {
    private static Rac1ShopResources.Lifecycle lifecycle;
    private static Rac1CacheContent mainSource;
    private static Rac1ShopAssets shopSource;
    private static java.nio.file.Path selectedPath;
    private static int windowWidth, windowHeight;
    private Rac1ShopClientResources() {}

    /** Full readiness is mandatory, not missing2d() or a caller-supplied "ready" boolean. */
    public static void ensure(Rac1ShopAssets shop, Rac1CacheContent main, int width, int height,
                              Rac1ShopResources.Factory factory) throws Exception {
        RenderSystem.assertOnRenderThread();
        Rac1ShopReadiness.inspect(shop,main).requireReady();
        if (mainSource != main || shopSource != shop) clear();
        if (lifecycle == null) lifecycle = new Rac1ShopResources.Lifecycle();
        lifecycle.ensure(shop.discSha1()+"|"+shop.language(),width,height,factory);
        mainSource = main; shopSource = shop;
        selectedPath = RacMcClient.media().configuredPath().orElse(null);
        windowWidth = Minecraft.getInstance().getWindow().getWidth();
        windowHeight = Minecraft.getInstance().getWindow().getHeight();
    }
    public static void clear() {
        Rac1ShopPreviewPreparation.clear();
        if (lifecycle == null) {
            mainSource = null; shopSource = null; selectedPath = null; windowWidth = windowHeight = 0;
            return; // initial reload/logout has no native owner and may precede render setup
        }
        RenderSystem.assertOnRenderThread();
        var previous = lifecycle;
        lifecycle = null; mainSource = null; shopSource = null; selectedPath = null; windowWidth = windowHeight = 0;
        if (previous != null) {
            try { previous.close(); }
            catch (Exception failure) { throw new IllegalStateException("Original shop resource disposal failed",failure); }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (lifecycle == null) return;
        var minecraft = Minecraft.getInstance();
        if (!RacMcClient.assets().isReady() || !RacMcClient.media().isReady()
                || RacMcClient.assetCacheDirectory().isEmpty()
                || !java.util.Objects.equals(selectedPath,RacMcClient.media().configuredPath().orElse(null))
                || RacMcClient.assets().content().orElse(null) != mainSource
                || minecraft.getWindow().getWidth() != windowWidth || minecraft.getWindow().getHeight() != windowHeight)
            clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
        }
    }
}
