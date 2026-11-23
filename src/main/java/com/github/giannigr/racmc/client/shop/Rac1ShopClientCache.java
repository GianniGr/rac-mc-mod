package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.shop.Rac1ShopAssets;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.cache.shop.Rac1ShopCachePreparation;
import com.github.giannigr.racmc.cache.shop.Rac1ShopSelectedDiscLoader;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Explicit local import request/hooks, not scene readiness or installed Screen/input/commerce. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1ShopClientCache {
    private static final Rac1ShopCachePreparation PREPARATION = new Rac1ShopCachePreparation(
            Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task,"racmc-shop-sidecar"); thread.setDaemon(true); return thread;
            }),selection -> {
                try (var lease=RacMcClient.media().lease(selection.discImage(),selection.mainDiscKey())) {
                    return Rac1ShopSelectedDiscLoader.load(selection,lease);
                }
            });
    private static Integer language;
    private static Rac1CacheContent mainSource;
    private static long mediaGeneration=-1;
    private Rac1ShopClientCache() {}

    /** Caller supplies an original language index; no Minecraft-language or catalog policy inferred. */
    public static synchronized void prepare(int sourceLanguage) {
        RenderSystem.assertOnRenderThread();
        if (!Set.of(0,2,3,4,5).contains(sourceLanguage))
            throw new IllegalArgumentException("Unsupported original shop language.");
        language=sourceLanguage; synchronizeSelection();
    }

    public static synchronized Optional<Rac1ShopAssets> importedResources() {
        RenderSystem.assertOnRenderThread();
        synchronizeSelection();
        return PREPARATION.importedResources();
    }
    public static synchronized Optional<String> error() {
        RenderSystem.assertOnRenderThread();
        synchronizeSelection();
        return PREPARATION.error();
    }
    public static synchronized void clear() {
        language=null; mainSource=null; mediaGeneration=-1; PREPARATION.invalidate();
    }

    private static void synchronizeSelection() {
        var main = RacMcClient.assets().isReady() ? RacMcClient.assets().content().orElse(null) : null;
        long generation=RacMcClient.media().generation();
        if (main != mainSource || mediaGeneration!=generation) {
            PREPARATION.invalidate(); mainSource=main; mediaGeneration=generation;
        }
        var path = RacMcClient.media().configuredPath();
        var root = RacMcClient.assetCacheDirectory();
        if (language == null || main == null || !RacMcClient.media().isReady() || RacMcClient.media().isChecking()
                || path.isEmpty() || root.isEmpty()) {
            PREPARATION.update(null); return;
        }
        PREPARATION.update(new Rac1ShopCachePreparation.Selection(root.get(),path.get(),main.discKey(),language));
    }
    @SubscribeEvent public static synchronized void tick(ClientTickEvent.Post event) {
        if (language != null) synchronizeSelection();
        else PREPARATION.update(null); // release an invalidated job's completed CPU payload too
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
