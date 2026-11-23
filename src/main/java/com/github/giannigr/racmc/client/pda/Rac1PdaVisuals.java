package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.pda.*;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1ImportedItemDrawing;
import java.util.concurrent.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** One bounded, in-memory PDA preparation per verified media/reload generation. No disk schema. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1PdaVisuals {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),task -> {
                var thread=new Thread(task,"racmc-pda-visuals"); thread.setDaemon(true); return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    private static Object source;
    private static long generation=-1,epoch=-1;
    private static CompletableFuture<Rac1PdaAssets> pending;
    private static boolean failed;
    private Rac1PdaVisuals() {}
    /** Called on the client/render thread. Never performs a disc read or waits for an importer. */
    public static Rac1PdaAssets resources() {
        var main=RacMcClient.assets().isReady() ? RacMcClient.assets().content().orElse(null):null;
        var path=RacMcClient.media().configuredPath();
        if (main==null || path.isEmpty() || !RacMcClient.media().isReady() || RacMcClient.media().isChecking()) {
            clear(); return null;
        }
        long current=RacMcClient.media().generation(),reload=Rac1ImportedItemDrawing.epoch();
        if (source!=main || generation!=current || epoch!=reload) {
            clear(); source=main; generation=current; epoch=reload;
            var selected=path.get(); String key=main.discKey();
            try {
                pending=CompletableFuture.supplyAsync(() -> {
                    try (var lease=RacMcClient.media().lease(selected,key)) {
                        if (lease.generation()!=current) throw new java.io.IOException("Stale PDA media generation");
                        return Rac1PdaImporter.read(lease);
                    } catch (Exception failure) { throw new CompletionException(failure); }
                },WORKER);
            } catch (RejectedExecutionException busy) { failed=true; }
        }
        if (failed || pending==null || !pending.isDone()) return null;
        try { return pending.join(); }
        catch (CompletionException failure) {
            failed=true;
            org.slf4j.LoggerFactory.getLogger(Rac1PdaVisuals.class)
                    .warn("Original PDA resources unavailable; no substitute item artwork",failure.getCause());
            return null;
        }
    }
    public static void clear() {
        source=null; generation=epoch=-1; pending=null; failed=false;
        Rac1PdaItemRenderer.clearTextures();
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (source!=null && (!RacMcClient.media().isReady() || RacMcClient.media().isChecking()
                || RacMcClient.media().generation()!=generation || !RacMcClient.assets().isReady()
                || RacMcClient.assets().content().orElse(null)!=source)) clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
