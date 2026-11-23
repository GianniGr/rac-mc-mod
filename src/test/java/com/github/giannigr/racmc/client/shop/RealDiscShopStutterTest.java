package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact dc32542 reference versus candidate; no client, audio device, extra GPU wait or time threshold. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopStutterTest {
    @TempDir Path temp;
    @Test void pairedStaticAndDynamicWorkPreservesEveryPixelDepthAndPrimitiveWithDiagnosticControl() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        Path path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try(var media=new Rac1MediaManager(validator,temp.resolve("media.properties"))) {
            media.select(path); RealDiscShopLeaseTest.await(media);
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            Rac1ShopPreviewSource source;
            try(var lease=media.lease(path,Rac1DiscIdentity.key(result.revision(),profile))) {
                source=Rac1ShopPreviewSource.load(lease,lease.discKey(),null,temp,new Rac1ShopImportProfile());
            }
            try(var hidden=new Rac1ShopOrderedGpuTest.Hidden()) {
                var report=new StringBuilder("STUTTER PAIRED hidden work, not live cadence; paired clocks include existing readback\n");
                try(var reference=new Rac1ShopActorDrawing.PresenterTarget(source,true,true,false);
                    var candidate=new Rac1ShopActorDrawing.PresenterTarget(source,true,true,true)) {
                    Assumptions.assumeTrue(reference.usesTextureBarrier() && candidate.usesTextureBarrier());
                    for(int warm=0;warm<4;warm++) { reference.frame(2,warm*.05f); candidate.frame(2,warm*.05f); }
                    int[] sequences={4,6,5,7,9,8};
                    for(int round=0;round<3;round++) for(int control=0;control<2;control++) {
                        boolean diagnostic=(round+control)%2==1;
                        if(diagnostic) { System.setProperty("racmc.shop.frameProfile","true"); Rac1ShopFrameProfile.open(); }
                        try {
                            for(int pair=0;pair<12;pair++) {
                                int sequence=sequences[pair%6]; float time=.35f+(pair/6)*.05f;
                                Rac1ShopOrderedGpu.Readback a,b; long start,before,after;
                                if((round+pair)%2==0) {
                                    start=System.nanoTime(); a=reference.frame(sequence,time); before=System.nanoTime()-start;
                                    start=System.nanoTime(); b=candidate.frame(sequence,time); after=System.nanoTime()-start;
                                } else {
                                    start=System.nanoTime(); b=candidate.frame(sequence,time); after=System.nanoTime()-start;
                                    start=System.nanoTime(); a=reference.frame(sequence,time); before=System.nanoTime()-start;
                                }
                                assertArrayEquals(a.rgba(),b.rgba()); assertArrayEquals(a.z24(),b.z24());
                                var old=reference.drawCounts(); var fast=candidate.drawCounts();
                                assertEquals(old.ordered(),fast.ordered()); assertEquals(old.opaque(),fast.opaque());
                                assertEquals(old.triangles(),fast.triangles());
                                assertTrue(fast.bindingSetups()<old.bindingSetups(),"Must reduce setup, never primitives/barriers");
                                report.append("STUTTER sample context=presenter diagnostic=").append(diagnostic)
                                        .append(" round=").append(round).append(" pair=").append(pair)
                                        .append(" referenceNs=").append(before).append(" candidateNs=").append(after)
                                        .append(" referenceBindings=").append(old.bindingSetups())
                                        .append(" candidateBindings=").append(fast.bindingSetups())
                                        .append(" primitives=").append(fast.triangles()).append('\n');
                            }
                        } finally {
                            if(diagnostic) {
                                Rac1ShopFrameProfile.finish("HIDDEN_DIAGNOSTIC_CONTROL");
                                System.clearProperty("racmc.shop.frameProfile");
                            }
                        }
                    }
                }
                // First pair includes cold composition; later alternating pairs are warmed/repeatable.
                for(int pair=0;pair<8;pair++) {
                    var offer=new Rac1ShopPreviewOffer(pair%2==1);
                    var mode=pair<4 ? Rac1ShopClientFlow.Mode.BROWSE:pair<6 ? Rac1ShopClientFlow.Mode.CONFIRM:Rac1ShopClientFlow.Mode.MAXED;
                    var ui=new Rac1ShopPreviewDrawing.Presentation(mode,offer.ammo() ? 25:1,offer.ammo() ? 25:2500);
                    Rac1ShopOrderedGpu.Readback a,b; long start,before,after;
                    if(pair%2==0) {
                        start=System.nanoTime(); a=Rac1ShopPreviewDrawing.render(source,offer,ui,false); before=System.nanoTime()-start;
                        start=System.nanoTime(); b=Rac1ShopPreviewDrawing.render(source,offer,ui,true); after=System.nanoTime()-start;
                    } else {
                        start=System.nanoTime(); b=Rac1ShopPreviewDrawing.render(source,offer,ui,true); after=System.nanoTime()-start;
                        start=System.nanoTime(); a=Rac1ShopPreviewDrawing.render(source,offer,ui,false); before=System.nanoTime()-start;
                    }
                    assertArrayEquals(a.rgba(),b.rgba()); assertArrayEquals(a.z24(),b.z24());
                    report.append("STUTTER sample context=composition diagnostic=false pair=").append(pair)
                            .append(" mode=").append(mode).append(" ammo=").append(offer.ammo())
                            .append(" referenceNs=").append(before).append(" candidateNs=").append(after).append('\n');
                }
                System.out.print(report);
            }
            assertEquals(1,media.completedAttempts());
        }
    }
}
