package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.clank.ClankFixtures;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class Rac1ClankLoadSlotTest {
    private static Rac1ClankLoadSlot.Request request(long media,long resource) {
        return new Rac1ClankLoadSlot.Request(Path.of("source.iso"),Path.of("cache"),ClankFixtures.disc(0),media,resource);
    }
    @Test void lateCompletionCannotReplaceReselectionOrResourceReloadRequest() throws Exception {
        var slot=new Rac1ClankLoadSlot();var old=new CompletableFuture<Rac1ClankPreparedAssets>();
        var next=new CompletableFuture<Rac1ClankPreparedAssets>();var a=request(1,1);var b=request(2,2);
        slot.start(a,old);assertNull(slot.poll(a));slot.clear();slot.start(b,next);
        old.complete(Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),1));assertNull(slot.poll(b));
        var ready=Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),2);next.complete(ready);
        assertNull(slot.poll(a));assertSame(ready,slot.poll(b));assertNull(slot.poll(b));
    }
    @Test void failureIsReportedOnceAndOnlyLifecycleResetRetries() throws Exception {
        var slot=new Rac1ClankLoadSlot();var key=request(1,1);
        slot.start(key,CompletableFuture.failedFuture(new IllegalStateException("failed optional input")));
        assertThrows(CompletionException.class,()->slot.poll(key));assertNull(slot.poll(key));
        slot.clear();assertNull(slot.request());
        slot.start(key,CompletableFuture.completedFuture(Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),1)));
        assertNotNull(slot.poll(key));
    }
    @Test void discResourceAndCacheOwnerAreAllPartOfPublicationIdentity() throws Exception {
        var slot=new Rac1ClankLoadSlot();var a=request(1,1);
        slot.start(a,CompletableFuture.completedFuture(Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),1)));
        var other=new Rac1ClankLoadSlot.Request(a.image(),Path.of("other-cache"),a.discKey(),1,1);
        assertNull(slot.poll(other));assertNotNull(slot.poll(a));
        slot.start(a,CompletableFuture.completedFuture(Rac1ClankPreparedAssets.prepare(ClankFixtures.bundle(),2)));
        assertThrows(IllegalStateException.class,()->slot.poll(a));
        assertThrows(IllegalArgumentException.class,()->request(-1,1));
    }
}
