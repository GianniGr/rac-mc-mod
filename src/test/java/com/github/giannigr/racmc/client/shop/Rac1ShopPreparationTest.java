package com.github.giannigr.racmc.client.shop;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopPreparationTest {
    @Test void approachAndTwoScreensShareOneImportWithoutAnySceneOrTransaction() {
        var cache=new Rac1ShopPreparation<String>();
        Object generation=new Object();
        var jobs=new ArrayList<Runnable>();
        var count=new AtomicInteger();
        var a=cache.request(generation,"disc-path",() -> { count.incrementAndGet(); return "ready"; },jobs::add);
        var b=cache.request(generation,"disc-path",() -> "wrong",jobs::add);
        assertSame(a,b); assertEquals(1,jobs.size()); assertEquals(0,count.get());
        jobs.removeFirst().run();
        assertEquals("ready",a.join()); assertEquals(1,count.get());
        assertSame(a,cache.request(generation,"disc-path",() -> "wrong",jobs::add));
        assertTrue(jobs.isEmpty(),"Warm screen must not reimport");
    }
    @Test void worldReloadPathOrGenerationReplacementDetachesWithoutCancellingOldOwners() {
        var cache=new Rac1ShopPreparation<String>();
        var jobs=new ArrayList<Runnable>();
        Object generation=new Object();
        var old=cache.request(generation,"a",() -> "old",jobs::add);
        cache.clear();
        var next=cache.request(generation,"a",() -> "new",jobs::add);
        assertNotSame(old,next); assertFalse(old.isCancelled());
        jobs.forEach(Runnable::run);
        assertEquals("old",old.join()); assertEquals("new",next.join());
        assertNotSame(next,cache.request(new Object(),"a",() -> "generation",Runnable::run));
        assertNotSame(next,cache.request(generation,"b",() -> "path",Runnable::run));
    }
    @Test void failedCancelledOrRejectedImportDoesNotPoisonRetry() {
        var cache=new Rac1ShopPreparation<String>(); Object generation=new Object();
        var failed=cache.request(generation,"a",() -> { throw new IllegalStateException("fixture"); },Runnable::run);
        assertTrue(failed.isCompletedExceptionally());
        assertEquals("retry",cache.request(generation,"a",() -> "retry",Runnable::run).join());
        cache.clear();
        assertThrows(RejectedExecutionException.class,() -> cache.request(generation,"a",() -> "not run",
                task -> { throw new RejectedExecutionException("fixture"); }));
        assertEquals("retry2",cache.request(generation,"a",() -> "retry2",Runnable::run).join());
    }
}
