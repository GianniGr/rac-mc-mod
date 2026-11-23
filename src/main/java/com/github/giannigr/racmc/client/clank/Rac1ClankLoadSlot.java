package com.github.giannigr.racmc.client.clank;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Client-owned optional request fence; workers never publish models or GPU resources themselves. */
public final class Rac1ClankLoadSlot {
    public record Request(Path image,Path cache,String discKey,long mediaGeneration,long resourceGeneration) {
        public Request {
            image=image.toAbsolutePath().normalize();cache=cache.toAbsolutePath().normalize();
            Objects.requireNonNull(discKey);
            if(mediaGeneration<0||resourceGeneration<0)throw new IllegalArgumentException("Invalid generation");
        }
    }
    private Request request;
    private CompletableFuture<Rac1ClankPreparedAssets> pending;
    private boolean finished;
    public Request request(){return request;}
    public void start(Request request,CompletableFuture<Rac1ClankPreparedAssets> pending) {
        this.request=Objects.requireNonNull(request);this.pending=Objects.requireNonNull(pending);finished=false;
    }
    public Rac1ClankPreparedAssets poll(Request current) {
        if(!Objects.equals(request,current)||finished||pending==null||!pending.isDone())return null;
        finished=true;
        var result=pending.join();
        if(!result.matches(current.discKey(),current.resourceGeneration()))
            throw new IllegalStateException("Clank preparation belongs to another resource owner");
        return result;
    }
    public void clear(){request=null;pending=null;finished=false;}
}
