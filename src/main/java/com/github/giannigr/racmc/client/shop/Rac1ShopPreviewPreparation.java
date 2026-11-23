package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.RacMcClient;
import java.nio.file.Path;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** CPU-only preparation on looking at an in-reach kiosk. Never opens a shop or starts sound/motion. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ShopPreviewPreparation {
    private static final ExecutorService LOADER=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new SynchronousQueue<>(),task -> {
                var thread=new Thread(task,"racmc-shop-preview"); thread.setDaemon(true); return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    private static final Rac1ShopPreparation<Rac1ShopPreviewSource> prepared=new Rac1ShopPreparation<>();
    private static Object world,content;
    private static Path selected;
    private static long retryAt;
    private static long mediaGeneration=-1;
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(Rac1ShopPreviewPreparation.class);
    private Rac1ShopPreviewPreparation() {}
    public static CompletableFuture<Rac1ShopPreviewSource> request(Path path,Rac1CacheContent cache,String discKey) {
        var media=RacMcClient.media();
        long generation=media.generation();
        var root=RacMcClient.assetCacheDirectory().orElse(null);
        return prepared.request(cache,path.toAbsolutePath().normalize()+"|"+discKey+"|"+generation,() -> {
            long start=System.nanoTime();
            try (var lease=media.lease(path,discKey)) {
                if (lease.generation()!=generation) throw new java.io.IOException("Shop preparation generation changed");
                var source=Rac1ShopPreviewSource.load(lease,discKey,cache.blaster(),root,Rac1ShopImportProfile.disabled());
                lease.checkValid();
                LOGGER.info("Gadgetron CPU source preparation ms={} (verified lease; no repeat ISO hash; no screen/audio/motion)",
                        (System.nanoTime()-start)/1e6);
                return source;
            } catch (Exception failure) { throw new CompletionException(failure); }
        },LOADER);
    }
    public static void clear() { prepared.clear(); world=null; content=null; selected=null; retryAt=0; mediaGeneration=-1; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        var cache=RacMcClient.assets().content().orElse(null);
        var path=RacMcClient.media().configuredPath().orElse(null);
        long generation=RacMcClient.media().generation();
        if (world!=mc.level || content!=cache || mediaGeneration!=generation || !java.util.Objects.equals(selected,path)
                || !RacMcClient.assets().isReady() || !RacMcClient.media().isReady()) {
            clear(); world=mc.level; content=cache; selected=path; mediaGeneration=generation;
        }
        if (mc.level==null || mc.player==null || mc.screen!=null || cache==null || path==null
                || !RacMcClient.assets().isReady() || !RacMcClient.media().isReady() || RacMcClient.media().isChecking()
                || mc.level.getGameTime()<retryAt) return;
        if (mc.hitResult instanceof BlockHitResult hit && mc.level.hasChunkAt(hit.getBlockPos())
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof Rac1VendorBlockEntity
                && mc.player.canInteractWithBlock(hit.getBlockPos(),0)) {
            retryAt=mc.level.getGameTime()+20;
            try { request(path,cache,cache.discKey()); }
            catch (RejectedExecutionException busy) { /* Old generation still finishing; bounded retry next second. */ }
        }
    }
}
