package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.ModSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source contract only: never initializes the Minecraft/AWT entrypoint in tests. */
class Rac1ShopClientCacheContractTest {
    @Test void consumesOnlyPublicEffectiveSelectionAndHasNoInstalledShopPolicy() {
        String source=ModSource.of("com/github/giannigr/racmc/client/shop/Rac1ShopClientCache.java");
        for(String required:new String[]{"RacMcClient.assetCacheDirectory()","RacMcClient.media().configuredPath()",
                "RacMcClient.media().isReady()","RacMcClient.assets().isReady()","main.discKey()",
                "main != mainSource","PREPARATION.invalidate()","ClientTickEvent.Post",
                "ClientPlayerNetworkEvent.LoggingOut","ResourceManagerReloadListener","Dist.CLIENT"})
            assertTrue(source.contains(required),required);
        for(String forbidden:new String[]{"CACHE_LOCATION","getDeclaredField","setScreen(","KeyMapping",
                "gameDirectory","attemptPath()","attemptResult()","getLanguageManager","sendToServer"})
            assertFalse(source.contains(forbidden),forbidden);
    }
    @Test void nativeOwnerStillRequiresFullReadinessAndDropsLostOrChangedSelection() {
        String source=ModSource.of("com/github/giannigr/racmc/client/shop/Rac1ShopClientResources.java");
        for(String required:new String[]{"Rac1ShopReadiness.inspect(shop,main).requireReady()",
                "!RacMcClient.media().isReady()","RacMcClient.assetCacheDirectory().isEmpty()",
                "Objects.equals(selectedPath,RacMcClient.media().configuredPath().orElse(null))"})
            assertTrue(source.contains(required),required);
    }
}
