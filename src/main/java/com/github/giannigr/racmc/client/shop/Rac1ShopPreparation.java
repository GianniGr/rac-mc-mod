package com.github.giannigr.racmc.client.shop;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** One ephemeral generation, no disk cache, screen ownership, session or transaction state. */
public final class Rac1ShopPreparation<T> {
    private Object generation;
    private String key;
    private CompletableFuture<T> future;
    public synchronized CompletableFuture<T> request(Object generation,String key,Supplier<T> loader,Executor executor) {
        Objects.requireNonNull(generation); Objects.requireNonNull(key);
        if (this.generation==generation && key.equals(this.key) && future!=null
                && !future.isCancelled() && !future.isCompletedExceptionally()) return future;
        var next=CompletableFuture.supplyAsync(loader,executor);
        this.generation=generation; this.key=key; future=next;
        return next;
    }
    /** Detach only. An old screen must never cancel another screen's shared import. */
    public synchronized void clear() { generation=null; key=null; future=null; }
}
