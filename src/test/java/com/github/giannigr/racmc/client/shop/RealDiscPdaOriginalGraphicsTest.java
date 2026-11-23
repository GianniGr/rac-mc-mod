package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.pda.*;
import com.github.giannigr.racmc.client.pda.*;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Local source/hidden-GPU checks only; no scene export or interactive client/server. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscPdaOriginalGraphicsTest {
    @TempDir Path temp;
    @Test void remoteGraphicsHaveOriginalFontsIconsStringsAndNoSpokenPayload() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try (var media=new Rac1MediaManager(validator,temp.resolve("pda-original-graphics.properties"))) {
            media.start(); media.select(path);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while ((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            String key=Rac1DiscIdentity.key(result.revision(),profile);
            try (var lease=media.lease(path,key)) {
                var remote=Rac1PdaOriginalSource.load(lease,key,null,temp.resolve("local-cache"));
                var source=remote.graphics();
                assertNull(source.voice()); assertNull(source.deniedCue());
                assertEquals(11,source.body().classNumber());
                assertEquals(5,source.icons().size());
                assertNotNull(source.shop().graphics().fonts().get("small"));
                for (int id:source.ui().required()) assertTrue(source.text().get(id).length>0);
                var oldFailure=assertThrows(IllegalArgumentException.class,() ->
                        new Rac1ShopTicker(source.shop().sceneTables().led(),source.text(),
                                source.shop().sceneTables().tickerIds(),bound -> 0));
                assertTrue(oldFailure.getMessage().contains("20321"),"Reproduce the owner's exact production text-bag mismatch");
                assertEquals(71,remote.audio().clips().get(Rac1PdaRemoteAudio.Role.OPEN).cueId());
                assertEquals(70,remote.audio().clips().get(Rac1PdaRemoteAudio.Role.DENIED).cueId());
                assertEquals(74,remote.audio().clips().get(Rac1PdaRemoteAudio.Role.CLOSED).cueId());
                System.out.println("PDA ORIGINAL GRAPHICS "+lease.revision()+" class11/source remote, original small font/60015 icons/regional strings; no speech");
                if ("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU"))) try (var hidden=new Rac1ShopOrderedGpuTest.Hidden()) {
                    productionPanels(remote);
                    var warm=Rac1PdaOriginalSource.load(lease,key,source.product().animated(),temp.resolve("local-cache"));
                    productionPanels(warm);
                    System.out.println("PDA PRODUCTION CONSTRUCTION "+lease.revision()
                            +" cold+warm exact importer and all24 weighted ticker choices/presenter/LED targets pass; OPEN71 DENIED70 CLOSED74");
                    var browse=Rac1ShopPreviewDrawing.renderRemote(source,Rac1ShopPreviewDrawing.Presentation.browse());
                    var quote=Rac1ShopPreviewDrawing.renderRemote(source,new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.CONFIRM,25,250));
                    assertEquals(512,browse.width()); assertEquals(source.pal() ? 448:416,browse.height());
                    assertEquals(browse.rgba().length,quote.rgba().length);
                    assertFalse(Arrays.equals(browse.rgba(),quote.rgba()));
                    assertTrue(nonzeroAlpha(browse.rgba())>100);
                    assertEquals(source.pal() ? "71a5866dc030b346fa62d885a217b94f3c5f0ed2bec1c39e7c5e417000650371"
                            :"2f9bed086d5abfd3a44b1b5947a1e0f245a899839e741c790d6c47596f73ca30",hash(browse.rgba()),
                            "Static remote ink/render must remain identical to the retained isolated candidate, not console parity");
                    var catalog=Rac1ShopPreviewDrawing.renderCatalog(source,Rac1ShopPreviewDrawing.Presentation.browse(),true);
                    var catalogQuote=Rac1ShopPreviewDrawing.renderCatalog(source,
                            new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.CONFIRM,7,175),true);
                    assertTrue(nonzeroAlpha(catalog.rgba())>100);
                    assertFalse(Arrays.equals(browse.rgba(),catalog.rgba()),"Bomb shell must not contain substituted Blaster product");
                    assertFalse(Arrays.equals(catalog.rgba(),catalogQuote.rgba()),"Original Yes/No edges remain visible");
                    var kioskCatalog=Rac1ShopPreviewDrawing.renderCatalog(source,Rac1ShopPreviewDrawing.Presentation.browse(),false);
                    assertArrayEquals(catalog.rgba(),kioskCatalog.rgba(),"Neither shell may insert an unverified product hologram");
                    System.out.println("PDA/BOMB CATALOG GPU shell="+hash(catalog.rgba())+" quote="+hash(catalogQuote.rgba())
                            +"; no Blaster product substitution; exact MC quote is supplemental, not retail art");
                    System.out.println("PDA ORIGINAL GPU browse="+hash(browse.rgba())+" quote25/250="+hash(quote.rgba())
                            +" coverage="+nonzeroAlpha(browse.rgba())+"; no native/live pixel acceptance claim");
                    // Actual kiosk production loader remains speech-enabled and keeps its own cue70 path.
                    var kiosk=Rac1ShopPreviewSource.load(lease,key,source.product().animated(),temp.resolve("local-cache"),
                            Rac1ShopImportProfile.disabled());
                    assertNotNull(kiosk.voice());
                    try (var session=new Rac1ShopPresenterSession(() -> true,(clip,gain) -> new Rac1ShopVoicePlayback.Handle() {
                        public boolean active() { return false; } // no device; exercise muted/drained production lifecycle
                        public void close() {}
                    }); var target=new Rac1ShopActorDrawing.PresenterTarget(kiosk)) {
                        session.attach(kiosk.presenter().animated().animations(),kiosk.voice(),bound -> 0);
                        for (boolean blaster:new boolean[]{false,true,false,true}) {
                            session.select(blaster);
                            var hashes=new HashSet<String>();
                            for (int step=0;step<6;step++) {
                                var f=session.tick(120,true);
                                hashes.add(hash(target.frame(f.sequence(),f.seconds()).rgba()));
                                assertNotEquals(Rac1ShopVoicePlayback.State.CLOSED,session.voice().state());
                            }
                            assertTrue(hashes.size()>1,"Actual kiosk presenter rasters must evolve after each navigation");
                        }
                        System.out.println("KIOSK PRODUCTION NAVIGATION "+lease.revision()
                                +" both directions/repeated selections; real presenter rasters evolve; muted voice stays live; no playback");
                    }
                    assertNotNull(kiosk.deniedCue()); assertEquals(70,kiosk.deniedCue().cueId());
                    var weapon=new Rac1ShopPreviewOffer(false);
                    assertEquals(0,weapon.iconFrame()); assertEquals(2500,weapon.displayedPrice(kiosk.facts()));
                    var weaponFrame=Rac1ShopPreviewDrawing.render(kiosk,weapon);
                    var weaponConfirm=Rac1ShopPreviewDrawing.render(kiosk,weapon,
                            new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.CONFIRM,1,2500));
                    var ammoFrame=Rac1ShopPreviewDrawing.render(kiosk,new Rac1ShopPreviewOffer(true));
                    var maxed=Rac1ShopPreviewDrawing.render(kiosk,new Rac1ShopPreviewOffer(true),
                            new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.MAXED,1,0));
                    assertFalse(Arrays.equals(weaponFrame.rgba(),ammoFrame.rgba()));
                    assertFalse(Arrays.equals(weaponFrame.rgba(),weaponConfirm.rgba()));
                    assertFalse(Arrays.equals(ammoFrame.rgba(),maxed.rgba()));
                    assertTrue(nonzeroAlpha(maxed.rgba())>100);
                    System.out.println("KIOSK PRODUCTION "+lease.revision()+" weapon2500="+hash(weaponFrame.rgba())
                            +" confirmation="+hash(weaponConfirm.rgba())+" Maxed="+hash(maxed.rgba())+"; cue70 preserved");
                }
                assertEquals(1,media.completedAttempts());
            }
        }
    }
    private static void productionPanels(Rac1PdaOriginalSource source) {
        var ids=source.graphics().shop().sceneTables().tickerIds();
        for (int i=0;i<ids.length;i++) {
            final int selection=i;
            try (var panels=new Rac1PdaOriginalPanels(source,bound -> selection)) {
                assertEquals(ids[i],panels.tickerId());
                var first=panels.presenterFrame();
                assertEquals(512,first.width());
                panels.advance(12);
                assertFalse(Arrays.equals(first.rgba(),panels.presenterFrame().rgba()),
                        "Actual remote idle target must change, not merely return a512-wide frame");
                var line=panels.tickerFrame();
                assertEquals(512,line.width()); assertEquals(128,line.height());
                if (i==0) {
                    panels.advance(108);
                    var visible=panels.tickerFrame();
                    assertTrue(nonzeroAlpha(visible.rgba())>0,"Ticker must actually contain visible source glyph pixels");
                    panels.advance(12);
                    assertFalse(Arrays.equals(visible.rgba(),panels.tickerFrame().rgba()),
                            "Production remote ticker must scroll, not just allocate");
                }
            }
        }
    }
    private static int nonzeroAlpha(byte[] rgba) { int n=0; for (int i=3;i<rgba.length;i+=4) if (rgba[i]!=0) n++; return n; }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
