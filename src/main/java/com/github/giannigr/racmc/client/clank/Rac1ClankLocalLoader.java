package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.clank.*;
import com.github.giannigr.racmc.iso.*;
import java.io.IOException;
import java.util.function.ObjLongConsumer;

/** Worker-only bounded feature adapter. Existing sidecar first; no redundant path validation. */
public final class Rac1ClankLocalLoader {
    public enum Stage { SIDECAR, IMPORT, CACHE_WRITE, PREPARE }
    @FunctionalInterface public interface LeaseSource {
        Rac1MediaLease open(Rac1ClankLoadSlot.Request request) throws IOException;
    }
    private Rac1ClankLocalLoader() {}
    public static Rac1ClankPreparedAssets load(Rac1ClankLoadSlot.Request request,LeaseSource source,
                                               ObjLongConsumer<Stage> timings) throws Exception {
        try(var image=source.open(request)) {
            if(image.generation()!=request.mediaGeneration()||!image.discKey().equals(request.discKey()))
                throw new IOException("Clank media selection changed");
            image.checkValid();
            var store=new Rac1ClankSidecarStore(request.cache());
            Rac1ClankAssets assets;
            long start=System.nanoTime();
            try {
                assets=store.read(request.discKey());
                timings.accept(Stage.SIDECAR,System.nanoTime()-start);
            } catch(IOException miss) {
                timings.accept(Stage.SIDECAR,System.nanoTime()-start);
                image.checkValid();start=System.nanoTime();
                assets=Rac1ClankImporter.read(image);
                timings.accept(Stage.IMPORT,System.nanoTime()-start);
                image.checkValid();start=System.nanoTime();
                try {store.write(assets);}
                catch(IOException writeFailure) {
                    // Validated in-memory assets can still be used; never write another namespace.
                    RacMcMod.LOGGER.debug("Clank optional sidecar write unavailable: {}",writeFailure.toString());
                }
                timings.accept(Stage.CACHE_WRITE,System.nanoTime()-start);
            }
            image.checkValid();start=System.nanoTime();
            var prepared=Rac1ClankPreparedAssets.prepare(assets,request.resourceGeneration(),
                    (stage,nanos)->RacMcMod.LOGGER.debug("Clank CPU {}: {} ms",stage,nanos/1e6));
            timings.accept(Stage.PREPARE,System.nanoTime()-start);
            image.checkValid();
            return prepared;
        }
    }
}
