package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.cache.shop.*;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual startup verification + typed lease miss/hit; CPU/hidden GPU, never live latency. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopLeaseTest {
    @TempDir Path temp;
    @Test void startupOnceColdAndRestartSidecarHitRejectStaleAndCorruptData() throws Exception {
        Path path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        Path root=System.getProperty("racmc.shopLeaseCacheRoot")==null ? temp:Path.of(System.getProperty("racmc.shopLeaseCacheRoot"));
        Files.createDirectories(root);
        var validator=new Rac1DiscValidator();
        System.out.println("LEASE PROFILE process="+ProcessHandle.current().pid()+" epoch="+java.time.Instant.now());
        try(var media=new Rac1MediaManager(validator,root.resolve("media.properties"))) {
            long start=System.nanoTime();
            media.start();
            if(media.configuredPath().isEmpty()) media.select(path);
            await(media);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            assertEquals(path,media.configuredPath().orElseThrow());
            double startup=(System.nanoTime()-start)/1e6;
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            String key=Rac1DiscIdentity.key(result.revision(),profile);
            assertEquals(profile.imageSize(),media.bytesRead(),"Complete startup digest still reads every image byte");
            assertEquals(1,media.completedAttempts(),"Exactly one startup verification in this fresh manager/process");
            System.out.println("STARTUP full-validation ms="+startup+" bytes="+media.bytesRead());
            var store=new Rac1ShopCacheStore(root);
            boolean diskHit=Files.exists(store.path(profile.imageSha1(),0));
            System.out.println("LEASE initial existing sidecar="+diskHit);
            var initialProfile=new Rac1ShopImportProfile();
            Rac1ShopPreviewSource initial;
            long generation=media.generation();
            try(var lease=media.lease(path,key)) {
                start=System.nanoTime(); lease.checkValid();
                System.out.println("LEASE identity/generation check ms="+(System.nanoTime()-start)/1e6+" (two change-guard snapshots, no digest)");
                start=System.nanoTime();
                initial=Rac1ShopPreviewSource.load(lease,key,null,root,initialProfile);
                System.out.println("LEASE first source ms="+(System.nanoTime()-start)/1e6+" diskHit="+diskHit+" (includes fixture weapon import)");
                assertFalse(initialProfile.nanos().containsKey("disc-validation"),"Lease import cannot run validator");
                print(initialProfile);
                var hitProfile=new Rac1ShopImportProfile();
                start=System.nanoTime();
                var hit=Rac1ShopPreviewSource.load(lease,key,initial.product().animated(),root,hitProfile);
                System.out.println("LEASE subsequent full source ms="+(System.nanoTime()-start)/1e6);
                assertTrue(hitProfile.nanos().containsKey("shop-sidecar-read"));
                assertFalse(hitProfile.nanos().containsKey("shop-import"),"Valid sidecar must skip shop conversion");
                assertEquals(initial.facts(),hit.facts());
                var selection=new Rac1ShopCachePreparation.Selection(root,path,key,0);
                start=System.nanoTime();
                assertEquals(profile.imageSha1(),Rac1ShopSelectedDiscLoader.load(selection,lease).discSha1());
                System.out.println("LEASE sidecar-only hit ms="+(System.nanoTime()-start)/1e6);
                var prepared=new Rac1ShopPreparation<Rac1ShopPreviewSource>(); Object owner=new Object();
                var ready=prepared.request(owner,key+"|"+generation,() -> initial,Runnable::run);
                for(int i=0;i<3;i++) {
                    start=System.nanoTime();
                    var warm=prepared.request(owner,key+"|"+generation,() -> { throw new AssertionError("Warm import"); },Runnable::run);
                    assertSame(ready,warm); assertSame(initial,warm.join());
                    System.out.println("LEASE prepared warm CPU lookup ms="+(System.nanoTime()-start)/1e6);
                }
                assertThrows(Exception.class,() -> Rac1ShopPreviewSource.load(lease,key+"forged",null,root,new Rac1ShopImportProfile()));
                // Separate owned corruption fixture: keep the valid miss/restart sample intact.
                Path corruptRoot=temp.resolve("corrupt-existing-sidecar");
                var corrupt=new Rac1ShopCacheStore(corruptRoot);
                corrupt.write(initial.shop());
                var target=corrupt.path(profile.imageSha1(),0);
                byte[] good=Files.readAllBytes(target),bad=good.clone(); bad[bad.length-1]^=1;
                Files.write(target.resolveSibling("before-corruption.proof"),good,StandardOpenOption.CREATE_NEW);
                Files.write(target,bad);
                var fallback=Rac1ShopPreviewSource.load(lease,key,initial.product().animated(),corruptRoot,new Rac1ShopImportProfile());
                assertEquals(initial.facts(),fallback.facts());
                assertArrayEquals(bad,Files.readAllBytes(target),"Optional corrupt sidecar must be preserved, not overwritten");
                assertThrows(java.io.IOException.class,() -> Rac1ShopSelectedDiscLoader.load(
                        new Rac1ShopCachePreparation.Selection(corruptRoot,path,key,0),lease),"Strict loader diagnostic retained");
                if("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU"))) try(var hidden=new Rac1ShopOrderedGpuTest.Hidden()) {
                    start=System.nanoTime();
                    try(var presenter=new Rac1ShopActorDrawing.PresenterTarget(initial);
                        var ticker=new Rac1ShopTickerDrawing.Target(initial.led())) {
                        System.out.println("LEASE GPU panel prep ms="+(System.nanoTime()-start)/1e6);
                        start=System.nanoTime(); presenter.frame(0);
                        System.out.println("LEASE GPU first presenter ms="+(System.nanoTime()-start)/1e6);
                    }
                    start=System.nanoTime();
                    var a=Rac1ShopPreviewDrawing.render(initial,new Rac1ShopPreviewOffer(false));
                    System.out.println("LEASE GPU first composition ms="+(System.nanoTime()-start)/1e6+" (hidden, not live)");
                    var b=Rac1ShopPreviewDrawing.render(hit,new Rac1ShopPreviewOffer(false));
                    var c=Rac1ShopPreviewDrawing.render(fallback,new Rac1ShopPreviewOffer(false));
                    assertArrayEquals(a.rgba(),b.rgba()); assertArrayEquals(a.z24(),b.z24());
                    assertArrayEquals(a.rgba(),c.rgba()); assertArrayEquals(a.z24(),c.z24());
                }
                media.invalidateLeases();
                assertFalse(lease.isCurrent());
                assertThrows(java.io.IOException.class,() -> Rac1ShopPreviewSource.load(lease,key,null,root,new Rac1ShopImportProfile()));
            }
            try(var next=media.lease(path,key)) { next.checkValid(); assertNotEquals(generation,next.generation()); }
            assertEquals(1,media.completedAttempts(),"No repeat full validation on miss, hit, warm, fallback or reload");
            System.out.println("LEASE verification count final="+media.completedAttempts()+"; repeated asset verification=0");
        }
    }
    static void await(Rac1MediaManager media) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        while((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<until) Thread.sleep(5);
        assertFalse(media.isChecking()); assertTrue(media.completedAttempts()>0);
    }
    private static void print(Rac1ShopImportProfile profile) {
        new TreeMap<>(profile.nanos()).forEach((k,v) -> System.out.println("LEASE STAGE "+k+" ms="+v/1e6));
        System.out.println("LEASE aggregate asset read bytes="+profile.readBytes()+" calls="+profile.readCalls());
    }
}
