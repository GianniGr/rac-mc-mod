package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudAssets;
import com.github.giannigr.racmc.asset.shop.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopCachePreparationTest {
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> pending = new ArrayDeque<>();
        public void execute(Runnable task) { pending.add(task); }
        void complete() { pending.remove().run(); }
    }
    private Rac1ShopCachePreparation.Selection selection(int language) {
        return new Rac1ShopCachePreparation.Selection(Path.of("synthetic-cache"),Path.of("synthetic-disc"),
                "synthetic|"+"a".repeat(40),language);
    }
    private Rac1ShopAssets assets(int language) {
        return new Rac1ShopAssets("a".repeat(40),language,Rac1HudAssets.empty(),new Rac1ShopText(Map.of()));
    }
    @Test void duplicateTicksReuseSingleJobAndImportedResourcesDoNotImplyReadiness() {
        var executor = new ManualExecutor();
        var preparation = new Rac1ShopCachePreparation(executor,s -> assets(s.language()));
        for(int i=0;i<100;i++) preparation.update(selection(0));
        assertEquals(1,executor.pending.size()); assertTrue(preparation.importedResources().isEmpty());
        executor.complete(); preparation.update(selection(0));
        var imported=preparation.importedResources().orElseThrow();
        assertFalse(Rac1ShopReadiness.inspect(imported,null).ready());
        for(int i=0;i<100;i++) preparation.update(selection(0));
        assertSame(imported,preparation.importedResources().orElseThrow()); assertTrue(executor.pending.isEmpty());
    }
    @Test void selectionChurnHasNoQueueAndDiscardsStaleCompletion() {
        var executor = new ManualExecutor();
        var preparation = new Rac1ShopCachePreparation(executor,s -> assets(s.language()));
        preparation.update(selection(0));
        for(int i=0;i<100;i++) preparation.update(selection(i%2==0 ? 2 : 3));
        assertEquals(1,executor.pending.size());
        executor.complete(); preparation.update(selection(3));
        assertTrue(preparation.importedResources().isEmpty());
        assertEquals(1,executor.pending.size());
        executor.complete(); preparation.update(selection(3));
        assertEquals(3,preparation.importedResources().orElseThrow().language());
    }
    @Test void reloadLogoutAndMainReplacementInvalidateEvenSameIdentity() {
        var executor = new ManualExecutor();
        var preparation = new Rac1ShopCachePreparation(executor,s -> assets(s.language()));
        preparation.update(selection(0)); preparation.invalidate(); preparation.update(selection(0));
        executor.complete(); preparation.update(selection(0));
        assertTrue(preparation.importedResources().isEmpty()); assertEquals(1,executor.pending.size());
        executor.complete(); preparation.update(selection(0));
        assertTrue(preparation.importedResources().isPresent());
        preparation.invalidate(); preparation.update(null);
        assertTrue(preparation.importedResources().isEmpty()); assertTrue(executor.pending.isEmpty());
    }
    @Test void failureDoesNotRetryPerTickAndStaleFailureCannotPoisonNewSelection() {
        var executor = new ManualExecutor();
        var preparation = new Rac1ShopCachePreparation(executor,s -> { throw new IOException("retained failure"); });
        preparation.update(selection(0)); executor.complete(); preparation.update(selection(0));
        assertTrue(preparation.error().orElseThrow().contains("retained failure"));
        for(int i=0;i<100;i++) preparation.update(selection(0));
        assertTrue(executor.pending.isEmpty());
        preparation.invalidate(); preparation.update(selection(0)); preparation.update(selection(2));
        executor.complete(); preparation.update(selection(2));
        assertTrue(preparation.error().isEmpty()); assertEquals(1,executor.pending.size());
    }
    @Test void wrongIdentityAndExecutorRejectionNeverPublish() {
        var executor = new ManualExecutor();
        var preparation = new Rac1ShopCachePreparation(executor,s -> assets(2));
        preparation.update(selection(0)); executor.complete(); preparation.update(selection(0));
        assertTrue(preparation.importedResources().isEmpty()); assertTrue(preparation.error().isPresent());
        var rejected = new Rac1ShopCachePreparation(task -> { throw new IllegalStateException("rejected"); },s -> assets(0));
        rejected.update(selection(0));
        assertTrue(rejected.error().orElseThrow().contains("rejected"));
        assertThrows(IllegalArgumentException.class,() -> selection(1));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopCachePreparation.Selection(Path.of("."),
                Path.of("."),"../../bad",0));
    }
}
