package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.asset.pda.*;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.shop.Rac1ShopPreviewSource;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import java.nio.file.Path;
import java.util.concurrent.*;

/** One local verified generation; original remote graphics and class cues, never vendor speech. */
public record Rac1PdaOriginalSource(Rac1ShopPreviewSource graphics,Rac1PdaRemoteAudio audio) {
    private static final ExecutorService WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),task -> {
                var thread=new Thread(task,"racmc-pda-original"); thread.setDaemon(true); return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    private static Object owner;
    private static long generation=-1;
    private static CompletableFuture<Rac1PdaOriginalSource> prepared;
    public static synchronized CompletableFuture<Rac1PdaOriginalSource> request() {
        var main=RacMcClient.assets().isReady() ? RacMcClient.assets().content().orElse(null):null;
        var path=RacMcClient.media().configuredPath();
        var root=RacMcClient.assetCacheDirectory();
        long current=RacMcClient.media().generation();
        if (main==null || path.isEmpty() || root.isEmpty() || !RacMcClient.media().isReady() || RacMcClient.media().isChecking())
            throw new IllegalStateException("Verified selected media and ready local cache required");
        if (owner==main && generation==current && prepared!=null) return prepared;
        owner=main; generation=current;
        prepared=CompletableFuture.supplyAsync(() -> {
            try (var lease=RacMcClient.media().lease(path.get(),main.discKey())) {
                if (lease.generation()!=current) throw new java.io.IOException("PDA remote source generation changed");
                return load(lease,main.discKey(),main.blaster(),root.get());
            } catch (Exception failure) { throw new CompletionException(failure); }
        },WORKER);
        return prepared;
    }
    /** Synchronous production importer; tests use this exact path, not a parallel availability-only loader. */
    public static Rac1PdaOriginalSource load(Rac1MediaLease lease,String discKey,Rac1AnimatedAsset mainProduct,
                                             Path cacheRoot) throws Exception {
        var program=Rac1PdaRemoteProgram.read(lease);
        var audio=Rac1PdaRemoteAudio.read(lease,program);
        var graphics=Rac1ShopPreviewSource.loadRemoteGraphics(lease,discKey,mainProduct,cacheRoot);
        if (graphics.voice()!=null) throw new IllegalStateException("PDA must not import spoken vendor data");
        lease.checkValid(); return new Rac1PdaOriginalSource(graphics,audio);
    }
    public static synchronized void clear() { owner=null; generation=-1; prepared=null; }
}
