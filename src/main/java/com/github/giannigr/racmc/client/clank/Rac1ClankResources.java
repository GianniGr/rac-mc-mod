package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.resources.ResourceLocation;

/** Optional prewarming after the existing gate. No model decode, pose preparation or ISO IO in item rendering. */
public final class Rac1ClankResources {
    private static final Map<Role,Rac1ItemTextureLease<ResourceLocation>> LEASES=new EnumMap<>(Role.class);
    private static final Map<Role,ResourceLocation[]> TEXTURES=new EnumMap<>(Role.class);
    private static final Set<Module> DRAWN=EnumSet.noneOf(Module.class);
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1),task->{
                var thread=new Thread(task,"racmc-clank-preparation");thread.setDaemon(true);return thread;
            });
    private static final Rac1ClankLoadSlot SLOT=new Rac1ClankLoadSlot();
    private static Rac1ClankPreparedAssets prepared;
    private static long generation,started;
    private static boolean textureFailure;
    private Rac1ClankResources() {}

    /** Client tick only: scheduling/publication are memory-only; at most one role uploads per tick. */
    public static void update(boolean needed) {
        var request=selection();
        if(request==null) {
            if(SLOT.request()!=null||prepared!=null)clear();
            return;
        }
        if(!request.equals(SLOT.request())) {
            if(!needed&&SLOT.request()==null)return;
            resetResources();
            request=selection();
            if(request==null)return;
            var captured=request;
            WORK.getQueue().clear(); // One running job plus at most one latest queued job.
            started=System.nanoTime();
            SLOT.start(captured,CompletableFuture.supplyAsync(()->{
                try {
                    return Rac1ClankLocalLoader.load(captured,r->RacMcClient.media().lease(r.image(),r.discKey()),
                            (stage,nanos)->RacMcMod.LOGGER.debug("Clank load {}: {} ms",stage,nanos/1e6));
                } catch(Exception failure) {throw new CompletionException(failure);}
            },WORK));
        }
        try {
            var result=SLOT.poll(request);
            if(result!=null)prepared=result;
        } catch(RuntimeException failure) {
            RacMcMod.LOGGER.warn("Wearable Clank local preparation unavailable: {}",failure.toString());
        }
        if(prepared!=null&&!textureFailure)warmOneRole();
    }
    private static Rac1ClankLoadSlot.Request selection() {
        if(!RacMcClient.allowsWorldEntry()||RacMcClient.media().isChecking())return null;
        var content=RacMcClient.assets().content();var cache=RacMcClient.assetCacheDirectory();
        var image=RacMcClient.media().configuredPath();
        return content.isEmpty()||cache.isEmpty()||image.isEmpty()?null:
                new Rac1ClankLoadSlot.Request(image.get(),cache.get(),content.get().discKey(),
                        RacMcClient.media().generation(),generation);
    }
    private static void warmOneRole() {
        for(var role:Role.values()) {
            if(TEXTURES.containsKey(role))continue;
            long start=System.nanoTime();
            try {
                var asset=prepared.model(role);
                var lease=LEASES.computeIfAbsent(role,r->new Rac1ItemTextureLease<>(
                        (index,texture)->Rac1ClientTextures.register("clank_"+r.name().toLowerCase(Locale.ROOT)+"_"+index,texture),
                        Rac1ClientTextures::release));
                TEXTURES.put(role,lease.get(asset,generation,asset.textures()).toArray(ResourceLocation[]::new));
                RacMcMod.LOGGER.debug("Clank client texture registration {}: {} ms",role,(System.nanoTime()-start)/1e6);
                if(TEXTURES.size()==Role.values().length)
                    RacMcMod.LOGGER.debug("Clank optional models ready after {} ms (not a presented GUI frame)",
                            (System.nanoTime()-started)/1e6);
            } catch(RuntimeException failure) {
                textureFailure=true;TEXTURES.clear();LEASES.values().forEach(Rac1ItemTextureLease::clear);
                RacMcMod.LOGGER.warn("Clank optional texture preparation unavailable: {}",failure.toString());
            }
            return;
        }
    }
    private static boolean ready() {
        var request=SLOT.request();
        return prepared!=null&&!textureFailure&&TEXTURES.size()==Role.values().length&&request!=null
                &&request.equals(selection())
                &&prepared.matches(request.discKey(),generation);
    }
    static Rac1AnimatedAsset model(Role role){return ready()?prepared.model(role):null;}
    static Rac1ClankPreparedAssets.Item item(Module module){return ready()?prepared.item(module):null;}
    static ResourceLocation[] textures(Role role){return ready()?TEXTURES.get(role):new ResourceLocation[0];}
    static void iconDrawn(Module module) {
        if(DRAWN.add(module))RacMcMod.LOGGER.debug(
                "Clank {} first item draw callback after {} ms (not display latency)",module,(System.nanoTime()-started)/1e6);
    }
    public static void reload(){clear();}
    private static void resetResources() {
        Rac1ClankItemRenderer.clear();LEASES.values().forEach(Rac1ItemTextureLease::clear);
        TEXTURES.clear();DRAWN.clear();prepared=null;textureFailure=false;SLOT.clear();generation++;
    }
    public static void clear(){resetResources();WORK.getQueue().clear();}
}
