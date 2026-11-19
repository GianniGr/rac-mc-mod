package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankImporter;
import com.github.giannigr.racmc.asset.clank.Rac1HydroAsset;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.render.Rac1ItemTextureLease;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

/** Optional local609 only, through the existing media manager; no config/asset upload or bundled art. */
final class Rac1HydroResources {
    private static Rac1AnimatedAsset model;
    private static CompletableFuture<Rac1HydroAsset> pending;
    private static String requested;
    private static long generation;
    private static final Rac1ItemTextureLease<ResourceLocation> TEXTURES=new Rac1ItemTextureLease<>(
            (index,texture)->Rac1ClientTextures.register("clank_hydro_"+index,texture),Rac1ClientTextures::release);
    private Rac1HydroResources() {}
    static void update(boolean needed) {
        if(!RacMcClient.allowsWorldEntry()){clear();return;}
        var content=RacMcClient.assets().content();if(content.isEmpty())return;
        String key=content.get().discKey();
        if(requested!=null&&!requested.equals(key))clear();
        if(needed&&requested==null&&RacMcClient.media().configuredPath().isPresent()) {
            requested=key;var image=RacMcClient.media().configuredPath().orElseThrow();
            pending=CompletableFuture.supplyAsync(()->{
                try {var asset=Rac1ClankImporter.readHydro(image);
                    if(!asset.discKey().equals(key))throw new java.io.IOException("Changed Hydro source");
                    return asset;
                }catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
            });
        }
        if(pending!=null&&pending.isDone()) {
            var completed=pending;pending=null;
            try {model=completed.join().copyModel();}
            catch(Exception failure){model=null;RacMcMod.LOGGER.warn("Local optional Hydro unavailable: {}",failure.toString());}
        }
    }
    static Rac1AnimatedAsset model(){return model;}
    static ResourceLocation[] textures() {
        return model==null?new ResourceLocation[0]:TEXTURES.get(model,generation,model.textures()).toArray(ResourceLocation[]::new);
    }
    static void clear(){TEXTURES.clear();generation++;model=null;pending=null;requested=null;Rac1HydroPlayback.clear();}
}
