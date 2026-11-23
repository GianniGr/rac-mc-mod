package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded hidden-context work isolation, not live frame pacing or native audio. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopStageProfileTest {
    @TempDir Path temp;
    @Test void isolateTwentyFourDynamicTicksAndFirstStaticComposition() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        var validator=new Rac1DiscValidator();
        Path path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        try(var media=new Rac1MediaManager(validator,temp.resolve("media.properties"))) {
            media.select(path); RealDiscShopLeaseTest.await(media);
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            String key=Rac1DiscIdentity.key(result.revision(),profile);
            Rac1ShopPreviewSource source;
            try(var lease=media.lease(path,key)) { source=Rac1ShopPreviewSource.load(lease,key,null,temp,new Rac1ShopImportProfile()); }
            try(var hidden=new Rac1ShopOrderedGpuTest.Hidden()) {
                System.setProperty("racmc.shop.frameProfile","true");
                Rac1ShopFrameProfile.open();
                try {
                    long start=Rac1ShopFrameProfile.begin();
                    try(var presenter=new Rac1ShopActorDrawing.PresenterTarget(source);
                        var ticker=new Rac1ShopTickerDrawing.Target(source.led())) {
                        Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.PANEL_CREATE,start);
                        var motion=new Rac1ShopLocalMotion();
                        var line=new Rac1ShopTicker(source.shop().sceneTables().led(),source.shop().text(),
                                source.shop().sceneTables().tickerIds(),bound -> 0);
                        for(int i=0;i<24;i++) {
                            Rac1ShopFrameProfile.frameStart();
                            Rac1ShopFrameProfile.phase(motion.tick(i<18).phase().name());
                            start=Rac1ShopFrameProfile.begin(); presenter.frame(i*3L);
                            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.PRESENTER,start);
                            line.advance(3);
                            start=Rac1ShopFrameProfile.begin(); ticker.frame(line.line());
                            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.TICKER,start);
                            if(i==0) {
                                start=Rac1ShopFrameProfile.begin();
                                var a=Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(false));
                                Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.COMPOSITION,start);
                                assertEquals(512,a.width());
                            }
                            Rac1ShopFrameProfile.frameEnd();
                        }
                    }
                } finally {
                    Rac1ShopFrameProfile.finish("HIDDEN_FIXTURE_NOT_LIVE_NO_MINECRAFT_UPLOAD");
                    System.clearProperty("racmc.shop.frameProfile");
                }
                try(var cached=new Rac1ShopActorDrawing.PresenterTarget(source,true,true);
                    var reference=new Rac1ShopActorDrawing.PresenterTarget(source,true,false)) {
                    // Warm both programs/pose paths, then alternate ordering to reduce JIT/order bias.
                    cached.frame(0); reference.frame(0);
                    long[] before=new long[12],after=new long[12];
                    int[] sequences={4,6,5,7,9,8};
                    for(int i=0;i<12;i++) {
                        Rac1ShopOrderedGpu.Readback a,b;
                        long start;
                        if(i%2==0) {
                            start=System.nanoTime(); a=reference.frame(sequences[i%6],.35f); before[i]=System.nanoTime()-start;
                            start=System.nanoTime(); b=cached.frame(sequences[i%6],.35f); after[i]=System.nanoTime()-start;
                        } else {
                            start=System.nanoTime(); b=cached.frame(sequences[i%6],.35f); after[i]=System.nanoTime()-start;
                            start=System.nanoTime(); a=reference.frame(sequences[i%6],.35f); before[i]=System.nanoTime()-start;
                        }
                        assertArrayEquals(a.rgba(),b.rgba()); assertArrayEquals(a.z24(),b.z24());
                    }
                    System.out.println("UNIFORM PAIRED hidden only referenceNs="+java.util.Arrays.toString(before)
                            +" cachedNs="+java.util.Arrays.toString(after)+"; 12 RGBA/Z exact pairs, no live frame claim");
                }
                assertEquals(1,media.completedAttempts());
            }
        }
    }
}
