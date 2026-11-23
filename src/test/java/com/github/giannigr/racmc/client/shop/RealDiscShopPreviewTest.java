package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/** Exact runtime source-loader/drawing path, owned hidden context only; not a live Minecraft check. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopPreviewTest {
    @Test void selectedDiscPreviewUsesPrimaryFactsAndIndependentWeaponAmmoFrames() throws Exception {
        Path path=Path.of(System.getProperty("racmc.isoImage"));
        var validator=new Rac1DiscValidator();
        String key;
        try (var result=validator.inspect(path)) {
            assertTrue(result.isSupported());
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.image().revision()).findFirst().orElseThrow();
            key=Rac1DiscIdentity.key(result.image().revision(),profile);
        }
        Rac1ShopPreviewSource source;
        String leaseRoot=System.getProperty("racmc.shopLeaseCacheRoot");
        if (leaseRoot!=null) {
            Path root=Path.of(leaseRoot); Files.createDirectories(root);
            try(var media=new com.github.giannigr.racmc.iso.Rac1MediaManager(validator,root.resolve("proof-media.properties"))) {
                media.select(path); RealDiscShopLeaseTest.await(media);
                try(var lease=media.lease(path,key)) {
                    source=Rac1ShopPreviewSource.load(lease,key,null,root,new Rac1ShopImportProfile());
                }
                assertEquals(1,media.completedAttempts(),"Proof fast path does not repeat validation");
            }
        } else source=Rac1ShopPreviewSource.load(path,key);
        var weaponPlacement=Rac1ShopHologram.placement(source,new Rac1ShopPreviewOffer(false));
        var ammoPlacement=Rac1ShopHologram.placement(source,new Rac1ShopPreviewOffer(true));
        assertArrayEquals(new float[]{-1,.05f,1.1f},ammoPlacement.position(),1e-6f,
                "Rigid class1006 keeps its distinct source position");
        assertEquals(0,weaponPlacement.position()[0],1e-6);
        assertEquals(1.5f,weaponPlacement.position()[2],1e-6);
        var weaponMesh=Rac1ShopActorDrawing.pose(source.product(),Rac1ShopActorDrawing.initial(source.product()),0);
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for (var t:weaponMesh.triangles()) if (!Rac1ShopActorDrawing.degenerate(weaponMesh,t))
            for (int index:new int[]{t.a(),t.b(),t.c()}) {
                var v=weaponMesh.vertices().get(index);
                float y=Rac1ShopActorDrawing.rotate(weaponPlacement.rotation(),v.x(),v.y(),v.z())[1]+weaponPlacement.position()[1];
                min=Math.min(min,y); max=Math.max(max,y);
            }
        assertEquals(0,(min+max)*.5,1e-6,"Drawn weapon bounds center on pad axis, not its asymmetric pivot");
        System.out.println("Hologram weapon fit="+Arrays.toString(weaponPlacement.position())
                +" horizontal bounds="+min+"/"+max+"; ammo="+Arrays.toString(ammoPlacement.position()));
        assertTrue(source.voice().ready(),source.voice().issue());
        var voicePoses=voiceProof(source);
        var physical=new com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose(source.body());
        var closed=physical.stage(com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose.Stage.CLOSED,0,List.of());
        var fit=com.github.giannigr.racmc.block.Rac1VendorFit.of(closed.positions()).inWorld();
        float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for (var stage:com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose.Stage.values())
            for (int frame=0;frame<=48;frame++) {
                float seconds=frame/60f;
                var posed=physical.stage(stage,seconds,List.of());
                for (int at=0;at<posed.positions().length;at+=3) {
                    float x=posed.positions()[at]*fit.scale()+fit.x();
                    float y=posed.positions()[at+1]*fit.scale()+fit.y();
                    float z=posed.positions()[at+2]*fit.scale()+fit.z();
                    bounds[0]=Math.min(bounds[0],x); bounds[1]=Math.min(bounds[1],y); bounds[2]=Math.min(bounds[2],z);
                    bounds[3]=Math.max(bounds[3],x); bounds[4]=Math.max(bounds[4],y); bounds[5]=Math.max(bounds[5],z);
                }
            }
        System.out.println("Physical closed-fit pose envelope="+Arrays.toString(bounds));
        assertTrue(bounds[0]>=-2 && bounds[3]<=3 && bounds[2]>=-2 && bounds[5]<=3
                        && bounds[1]>=com.github.giannigr.racmc.client.render.Rac1VendorRenderer.MIN_ANIMATED_Y && bounds[4]<=6,
                "Physical source pose exceeds old render bound: "+Arrays.toString(bounds));
        assertEquals(new Rac1ShopPreviewOffer.Facts(2500,1,25,200,100),source.facts());
        assertEquals(5,source.icons().size());
        assertFalse(Arrays.equals(source.icons().get(0).rgba(),source.icons().get(2).rgba()));
        assertEquals(source.body().textures().length,source.textures().size());
        assertEquals(256*128*4,source.font().length);
        assertTrue(printable(source.text().get(source.ids().buy())).endsWith("Buy"));
        assertTrue(printable(source.text().get(source.ids().exit())).endsWith("Exit"));
        assertTrue(printable(source.text().get(source.ui().howMany())).toLowerCase(Locale.ROOT).contains("many"));
        assertTrue(printable(source.text().get(source.ui().purchase())).toLowerCase(Locale.ROOT).contains("purchase"));
        var popupRect=Rac1ShopPreviewDrawing.popup(source);
        assertTrue(popupRect.width()>60 && popupRect.height()>60,"Actual popup bone-projected extent, not fixture placement");
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")),"Owned hidden GPU opt-in");
        Path output=Path.of(Objects.requireNonNull(System.getProperty("racmc.shopC1ProofOutput")));
        assertTrue(Files.isDirectory(output));
        String region=source.pal() ? "pal":"ntsc";
        try (var hidden=new Rac1ShopOrderedGpuTest.Hidden()) {
            var weapon=Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(false));
            var ammo=Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(true));
            var confirmation=Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(false),
                    new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.CONFIRM,1,2500));
            var quantity=Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(true),
                    new Rac1ShopPreviewDrawing.Presentation(Rac1ShopClientFlow.Mode.CONFIRM,25,25));
            Rac1ShopOrderedGpu.Readback presenter,animated;
            boolean reference=Boolean.getBoolean("racmc.shopOrderedReference");
            try (var target=new Rac1ShopActorDrawing.PresenterTarget(source,!reference)) {
                presenter=target.frame(0);
                long start=System.nanoTime();
                animated=target.frame(12);
                System.out.println("Presenter persistent warm frame ms="+(System.nanoTime()-start)/1_000_000);
                for (int frame=1;frame<=6;frame++) target.frame(frame*3);
                if (Boolean.getBoolean("racmc.shopProfile")) {
                    System.out.println("Presenter texture barrier="+target.usesTextureBarrier());
                    long[] nanos=new long[24];
                    for (int frame=0;frame<nanos.length;frame++) {
                        long before=System.nanoTime();
                        target.frame((frame*3)%60);
                        nanos[frame]=System.nanoTime()-before;
                    }
                    Arrays.sort(nanos);
                    System.out.println("Presenter warm24 wall ms min/median/p95/max="
                            +nanos[0]/1e6+"/"+nanos[12]/1e6+"/"+nanos[22]/1e6+"/"+nanos[23]/1e6
                            +" (512x128, same persistent target, synchronous overlay readback, 7 warmups)");
                }
            }
            if (!reference) try (var ordered=new Rac1ShopActorDrawing.PresenterTarget(source,false);
                                  var fast=new Rac1ShopActorDrawing.PresenterTarget(source)) {
                assertArrayEquals(ordered.frame(0).rgba(),presenter.rgba(),"Barrier preserves ordered frame0 RGBA");
                assertArrayEquals(ordered.frame(12).rgba(),animated.rgba(),"Barrier preserves ordered frame12 RGBA");
                for (int frame:new int[]{3,57,600,612,1200,1212}) {
                    var a=ordered.frame(frame); var b=fast.frame(frame);
                    assertArrayEquals(a.rgba(),b.rgba(),"Exact ordered coverage/RGBA at reference frame"+frame);
                    assertArrayEquals(a.z24(),b.z24(),"Exact ordered Z24 at reference frame"+frame);
                }
            }
            assertFalse(Arrays.equals(presenter.rgba(),animated.rgba()),"Actual source actor palette advances");
            try (var fast=new Rac1ShopActorDrawing.PresenterTarget(source);
                 var ordered=new Rac1ShopActorDrawing.PresenterTarget(source,false)) {
                for (var entry:voicePoses.entrySet()) {
                    var pose=entry.getValue();
                    var frame=fast.frame(pose.sequence(),pose.seconds());
                    var referenceFrame=ordered.frame(pose.sequence(),pose.seconds());
                    assertArrayEquals(referenceFrame.rgba(),frame.rgba(),"Provisional speech pose retains ordered pixels");
                    assertArrayEquals(referenceFrame.z24(),frame.z24(),"Provisional speech pose retains ordered depth");
                    write(output.resolve("resume23-"+region+"-speaking-"+entry.getKey()+".png"),frame);
                }
            }
            write(output.resolve("resume16-"+region+"-presenter-0.png"),presenter);
            write(output.resolve("resume16-"+region+"-presenter-12.png"),animated);
            var ticker=new Rac1ShopTicker(source.shop().sceneTables().led(),source.shop().text(),
                    source.shop().sceneTables().tickerIds(),bound -> 0);
            ticker.advance(180);
            var led=Rac1ShopTickerDrawing.render(source.led(),ticker.line());
            compose(output.resolve("resume16-"+region+"-combined-weapon.png"),source,weapon,led,animated);
            compose(output.resolve("resume16-"+region+"-combined-ammo.png"),source,ammo,led,animated);
            assertFalse(Arrays.equals(weapon.rgba(),ammo.rgba()),"Original icon changes the rendered runtime preview");
            assertFalse(Arrays.equals(weapon.rgba(),confirmation.rgba()),"Actual popup and source confirmation text render");
            assertFalse(Arrays.equals(ammo.rgba(),quantity.rgba()),"Original quantity text/control/total render");
            assertArrayEquals(weapon.rgba(),Rac1ShopPreviewDrawing.render(source,new Rac1ShopPreviewOffer(false)).rgba(),
                    "Second viewer/offer cannot contaminate first scene");
            write(output.resolve("resume11-"+region+"-weapon.png"),weapon);
            write(output.resolve("resume11-"+region+"-ammo.png"),ammo);
            write(output.resolve("resume13-"+region+"-confirmation.png"),confirmation);
            write(output.resolve("resume13-"+region+"-quantity.png"),quantity);
            Files.write(output.resolve("resume11-"+region+"-preview.txt"),List.of(
                    "Runtime source-loader/drawing paths; source-GPU composition fixture, NOT live Minecraft or complete original UI.",
                    "Selected disc identity="+key+"; source English preview; buy="+source.ids().buy()+" exit="+source.ids().exit(),
                    "Primary source facts="+source.facts()+"; guarded GiveItem new-ownership max(current,initial) and AddAmmo clamp/overflow.",
                    "Owner-approved server arrow adaptation:1unit=1ordinary arrow, shop allowance200, new weapon fill-to100; GPU images execute no commerce.",
                    "Follow-on UI: primary popup roles="+source.ui()+"; source popup bone rect="+popupRect,
                    "Quantity image explicitly selects25 units/25bolts as a proof fixture, NOT a bundle.",
                    "Single inventory-derived offer: original HUD60015 weapon frame0 / ammo frame2; actual purchases use the separate server-issued quote path.",
                    "Owned hidden GL="+glGetString(GL_VENDOR)+" / "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION),
                    "World camera untouched; original cut-eye projection; source count1 arm pose; source Novalis lights; transparent primitive coverage (not a world screenshot).",
                    "Product and actual class12 idle animation targets/LED are included; separate runtime world fold/observer bridge is NOT pictured by this fixture.",
                    "Missing: live-world screenshot/lighting, full CRT/backdrop/HUD/audio; first hologram spin frame only; T21/owner gates remain."),
                    StandardOpenOption.CREATE_NEW);
            hidden.unfocused(); assertEquals(GL_NO_ERROR,glGetError());
        }
    }
    private static Map<Integer,Rac1ShopPresenter.Frame> voiceProof(Rac1ShopPreviewSource source) throws Exception {
        Map<Integer,Rac1ShopPresenter.Frame> poses=new LinkedHashMap<>();
        for (int variant=0;variant<2;variant++) {
            int chosen=variant; int[] now={0}; List<Integer> played=new ArrayList<>();
            // Device-free channel-lifetime simulation using only decoded sample count, no PCM output/playback.
            var owner=new Rac1ShopVoicePlayback(() -> true,(clip,gain) -> {
                played.add(clip.index());
                int end=now[0]+(int)Math.ceil(clip.frames()*28.0/clip.rate()*60);
                return new Rac1ShopVoicePlayback.Handle() {
                    boolean closed;
                    public boolean active() { return !closed && now[0]<end; }
                    public void close() { closed=true; }
                };
            });
            try (var p=new Rac1ShopPresenter(source.presenter().animated().animations(),source.voice(),owner,n -> chosen)) {
                boolean receipt=false;
                for (int tick=0;tick<20000 && played.size()<3;tick++) {
                    now[0]+=3;
                    var frame=p.tick(3,true);
                    if (frame.attempted() && frame.streamId()>=10000) poses.putIfAbsent(frame.streamId()-10000,frame);
                    if (!receipt && !played.isEmpty()) { p.purchaseAccepted(); receipt=true; }
                }
                assertEquals(List.of(variant*18,12+variant*18,6+variant*18),played,
                        "All three source pairs run from decoded header clocks without a native sound manager");
            }
        }
        assertEquals(6,poses.size());
        for (var entry:poses.entrySet())
            System.out.println("PROVISIONAL English index="+entry.getKey()+" sequence="+entry.getValue().sequence()
                    +" threshold-cross seconds="+entry.getValue().seconds()+"; simulated channel only, no audio export");
        return poses;
    }
    private static String printable(byte[] bytes) {
        StringBuilder out=new StringBuilder();
        for (byte b:bytes) if ((b&255)>=32 && (b&255)<=126) out.append((char)b);
        return out.toString();
    }
    private static void write(Path path,Rac1ShopOrderedGpu.Readback frame) throws Exception {
        save(path,image(frame));
    }
    private static BufferedImage image(Rac1ShopOrderedGpu.Readback frame) {
        var image=new BufferedImage(frame.width(),frame.height(),BufferedImage.TYPE_INT_ARGB);
        byte[] b=frame.rgba();
        for (int y=0;y<frame.height();y++) for (int x=0;x<frame.width();x++) {
            int at=(y*frame.width()+x)*4;
            image.setRGB(x,y,((b[at+3]&255)<<24)|((b[at]&255)<<16)|((b[at+1]&255)<<8)|(b[at+2]&255));
        }
        return image;
    }
    private static void compose(Path path,Rac1ShopPreviewSource source,Rac1ShopOrderedGpu.Readback base,
                                Rac1ShopOrderedGpu.Readback led,Rac1ShopOrderedGpu.Readback presenter) throws Exception {
        var output=image(base); var graphics=output.createGraphics();
        try {
            for (int s:new int[]{0,2}) {
                var r=Rac1ShopPreviewDrawing.monitor(source,s);
                graphics.drawImage(image(s==0 ? led:presenter),(int)r.x(),(int)r.y(),(int)(r.x()+r.width()),(int)(r.y()+r.height()),
                        0,0,Math.min(512,(int)Math.ceil(r.width())),Math.min(128,(int)Math.ceil(r.height())),null);
            }
        } finally { graphics.dispose(); }
        save(path,output);
    }
    private static void save(Path path,BufferedImage image) throws Exception {
        // CREATE_NEW stream prevents accidental replacement of prior proof images.
        try (var stream=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)) { assertTrue(ImageIO.write(image,"png",stream)); }
    }
}
