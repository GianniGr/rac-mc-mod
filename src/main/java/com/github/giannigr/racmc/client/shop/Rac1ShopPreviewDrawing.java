package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose;
import com.github.giannigr.racmc.asset.*;
import java.util.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/**
 * One bounded original cut-view preview generation, not the complete compositor.
 * Source Novalis lighting/static pose remain fixtures, not live Minecraft scene lighting.
 */
public final class Rac1ShopPreviewDrawing {
    private static final float[] ID={1,0,0,0,1,0,0,0,1};
    private Rac1ShopPreviewDrawing() {}
    public record Presentation(Rac1ShopClientFlow.Mode mode,int quantity,long total) {
        public static Presentation browse() { return new Presentation(Rac1ShopClientFlow.Mode.BROWSE,1,0); }
        public boolean popup() { return mode==Rac1ShopClientFlow.Mode.CONFIRM || mode==Rac1ShopClientFlow.Mode.WAIT_BUY
                || mode==Rac1ShopClientFlow.Mode.DENIED || mode==Rac1ShopClientFlow.Mode.MAXED; }
    }
    public static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer) {
        return render(source,offer,Presentation.browse());
    }
    public static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer,Presentation ui) {
        return render(source,offer,ui,true);
    }
    /** Reference mode is the exact per-primitive snapshot/ping-pong path from dc32542. */
    static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer,Presentation ui,boolean optimized) {
        return render(source,offer,ui,optimized,false);
    }
    /** Verified remote path: direct open pose, no world hologram/cone, PDA price and original strike-through. */
    public static Rac1ShopOrderedGpu.Readback renderRemote(Rac1ShopPreviewSource source,Presentation ui) {
        return render(source,new Rac1ShopPreviewOffer(true),ui,true,true);
    }
    /** Shared original shell only: unverified Bomb retail product art stays absent, not Blaster-substituted. */
    public static Rac1ShopOrderedGpu.Readback renderCatalog(Rac1ShopPreviewSource source,Presentation ui,boolean portable) {
        return render(source,new Rac1ShopPreviewOffer(true),ui,true,portable,true);
    }
    private static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer,
                                                     Presentation ui,boolean optimized,boolean remote) {
        return render(source,offer,ui,optimized,remote,false);
    }
    private static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource source,Rac1ShopPreviewOffer offer,
                                                     Presentation ui,boolean optimized,boolean remote,boolean catalog) {
        int height=source.pal() ? 448:416;
        float tanY=.63f*(source.pal() ? .756f:.775f);
        var open=new Rac1ShopKioskPose(source.body()).stage(Rac1ShopKioskPose.Stage.OPEN,0,
                Rac1ShopKioskPose.monitorArms(source.shop().sceneTables(),1));
        var posed=source.normal().pose(11,source.normal().originalPalette(open.skinning()));
        try (var gpu=new Rac1ShopOrderedGpu(512,height,true,optimized)) {
            if (!optimized) return compose(gpu,source,offer,ui,height,tanY,open,posed,remote,catalog);
            gpu.enableTextureBarrier();
            var result=new Rac1ShopOrderedGpu.Readback[1];
            gpu.orderedBatch(() -> result[0]=compose(gpu,source,offer,ui,height,tanY,open,posed,remote,catalog));
            return result[0];
        }
    }
    private static Rac1ShopOrderedGpu.Readback compose(Rac1ShopOrderedGpu gpu,Rac1ShopPreviewSource source,
            Rac1ShopPreviewOffer offer,Presentation ui,int height,float tanY,Rac1ShopKioskPose.Pose open,
             Rac1ShopNormalGeometry.Posed posed,boolean remote,boolean catalog) {
            var scene=gpu.surface(512,height); var text=gpu.surface(512,128);
            gpu.clearDepth(0);
            // Minecraft adaptation: live world is composed behind actual primitive coverage.
            // No source/world snapshot and no RGB chroma-key/background replacement.
            gpu.clear(scene,new Rgba(0,0,0,0),false,0,0);
            // Source above-pad model is distinct from the weapon shown inside the item panel.
            var holo=Rac1ShopHologram.placement(source,offer);
            var hologram=offer.ammo() ? source.ammo():source.product();
             if (!remote && !catalog) Rac1ShopActorDrawing.draw(gpu,scene,source,hologram,offer.ammo() ? 0:Rac1ShopActorDrawing.initial(hologram),
                     0,holo.rotation(),holo.position(),false);
            var images=new ArrayList<Rac1ShopOrderedGpu.Image>();
            for (var p:source.textures()) images.add(gpu.image(p.width(),p.height(),p.rgba(),new Sampler(Filter.LINEAR,Address.REPEAT)));
            float[] record=source.light().vendorLightRecord(),directions=new float[9],colors=new float[12];
            for (int k=0;k<2;k++) {
                System.arraycopy(record,k*8,colors,k*4,4);
                System.arraycopy(Rac1ShopLightingProgram.toLight(ID,Arrays.copyOfRange(record,k*8+4,k*8+7)),0,directions,k*3,3);
            }
            var fog=source.light().worldFog();
            var state=new Rac1ShopOrderedGpu.State(0x44,128,96,false,false,false,0,true,fog.red(),fog.green(),fog.blue());
            var vertices=new ArrayList<Rac1ShopOrderedGpu.Vertex>();
            for (var v:posed.vertices()) {
                int[] rgba=Rac1ShopLightingProgram.normal(v.nx(),v.ny(),v.nz(),directions,colors,
                        source.light().vendorAmbient(),128,new int[]{v.red(),v.green(),v.blue(),v.alpha()});
                float d=3.8f-v.x();
                if (d<=0) throw new IllegalArgumentException("Preview source behind original cut camera");
                double n=32,f=745472;
                double z=8388096-8388080*((f+n)/(f-n)-2*f*n/((f-n)*d*1024));
                vertices.add(new Rac1ShopOrderedGpu.Vertex(v.y()/.63f,(v.z()-1.5f)/tanY,(float)((z/16777216*2-1)*d),
                        d,v.s(),v.t(),rgba[0],rgba[1],rgba[2],rgba[3],Rac1ShopLightingProgram.fogByte(d*1024,fog)));
            }
            for (var t:posed.triangles())
                gpu.triangle(scene,t.selector()==-1 ? null:images.get(t.selector()),state,vertices.get(t.a()),vertices.get(t.b()),vertices.get(t.c()));
            var font=source.shop().graphics().fonts().get("small");
            var atlas=gpu.image(256,128,source.font(),new Sampler(Filter.LINEAR,Address.CLAMP));
            var p=source.icons().get(offer.iconFrame());
            var icon=gpu.image(p.width(),p.height(),p.rgba(),new Sampler(Filter.LINEAR,Address.CLAMP));
            for (int s:new int[]{1,3,4,5}) {
                var q=Rac1ShopLayout.monitorVendor(source.body(),open.chain(),s,source.shop().sceneTables().margins());
                var end=q.opposite();
                double[] a=pixel(q.corner(),tanY,height),b=pixel(end,tanY,height);
                double x=Math.min(a[0],b[0]),y=Math.min(a[1],b[1]),w=Math.abs(a[0]-b[0]),h=Math.abs(a[1]-b[1]);
                if (w<=0 || h<=0) throw new IllegalArgumentException("Preview source monitor extent");
                gpu.clear(text,new Rgba(0,0,0,0),true,0,255);
                if (catalog && (s==1 || s==5)) {
                    // No guessed original Bomb model, icon, name, panel pose or starter offer.
                } else if (s==1) {
                    var placement=source.shop().sceneTables().blasterPanel();
                    var origin=source.presentation().panelBase();
                    for (int i=0;i<3;i++) origin[i]+=placement[4+i];
                    if (!offer.ammo()) origin[2]+=placement[7];
                    Rac1ShopActorDrawing.draw(gpu,text,source,source.product(),Rac1ShopActorDrawing.initial(source.product()),
                            0,Arrays.copyOf(placement,3),origin,true);
                    line(gpu,text,atlas,font,source.text().get(20039),6,8,0);
                    if (offer.ammo()) line(gpu,text,atlas,font,source.text().get(20317),24,24,0);
                     line(gpu,text,atlas,font,ascii(offer.ammo() ? 1:2500),118,101,2);
                     if (remote) {
                         int oldWidth=Rac1ShopGlyphLayout.line(font,ascii(1),
                                 Rac1ShopPrototypeTextIds.GS_TEXT_COLOR,true).advance();
                         for (int strike=0;strike<7;strike++)
                             quad(gpu,text,null,null,117-oldWidth+strike,109,oldWidth+6-strike*2,3,
                                     0,0,0,0,new Rgba(68,149,149,32+strike*16),false);
                         line(gpu,text,atlas,font,ascii(10),118,85,2);
                     }
                } else if (s==5) {
                    // One-product strip, frame0 weapon/frame2 ammo; original icon display extent48x48.
                    quad(gpu,text,icon,null,12,6,48,48,0,0,1,1,new Rgba(128,128,128,128),false);
                } else {
                    boolean confirm=ui.mode()==Rac1ShopClientFlow.Mode.CONFIRM || ui.mode()==Rac1ShopClientFlow.Mode.WAIT_BUY;
                    boolean back=ui.mode()==Rac1ShopClientFlow.Mode.DENIED || ui.mode()==Rac1ShopClientFlow.Mode.MAXED
                            || ui.mode()==Rac1ShopClientFlow.Mode.FAILURE;
                    byte[] bytes=source.text().get(s==3 ? (confirm ? source.ui().yes():back ? source.ui().back():source.ids().buy())
                            :(confirm || back ? source.ui().no():source.ids().exit()));
                    int tw=Math.min(512,(int)Math.ceil(w)),th=Math.min(128,(int)Math.ceil(h));
                    var window=new Rac1ShopTextWindow.Window(0,0,tw,th,tw/2,th/2,16,true,true);
                    var layout=Rac1ShopTextWindow.layout(font,bytes,window);
                    for (var line:layout.lines()) if (line.visible())
                        for (var g:Rac1ShopGlyphLayout.line(font,line.bytes(),Rac1ShopPrototypeTextIds.GS_TEXT_COLOR,true).glyphs()) {
                            int c=g.gsColor();
                            quad(gpu,text,atlas,null,line.x()+g.x(),line.y()+g.y(),g.width(),g.height(),
                                    g.u()/256f,g.v()/128f,(g.u()+g.width())/256f,(g.v()+g.height())/128f,
                                    new Rgba(c&255,c>>>8&255,c>>>16&255,c>>>24&255),false);
                        }
                }
                quad(gpu,scene,null,text,x,y,w,h,0,0,(float)(w/512),(float)(h/128),new Rgba(128,128,128,128),true);
            }
            if (ui.popup() && !catalog) {
                var popup=source.shop().models().popup();
                var seq=Arrays.stream(popup.animations()).filter(a -> a.name().equals("sequence_1")).findFirst().orElseThrow();
                var skin=Rac1AnimationEvaluator.evaluate(popup.skeleton(),seq,0,1);
                var geometry=source.popup().pose(1137,source.popup().originalPalette(skin));
                float[] dirs=new float[9],cols=new float[12];
                System.arraycopy(Rac1ShopLightingProgram.toLight(ID,Arrays.copyOf(source.light().menuDirection(),3)),0,dirs,0,3);
                System.arraycopy(source.light().menuColor(),0,cols,0,4);
                var pv=new ArrayList<Rac1ShopOrderedGpu.Vertex>();
                for (var v:geometry.vertices()) {
                    int[] c=Rac1ShopLightingProgram.normal(v.nx(),v.ny(),v.nz(),dirs,cols,new int[]{16,16,16},128,
                            new int[]{v.red(),v.green(),v.blue(),v.alpha()});
                    float d=3.8f-v.x(); double n=32,f=745472;
                    double z=8388096-8388080*((f+n)/(f-n)-2*f*n/((f-n)*d*1024));
                    pv.add(new Rac1ShopOrderedGpu.Vertex(v.y()/.63f,(v.z()-1.5f)/tanY,(float)((z/16777216*2-1)*d),
                            d,v.s(),v.t(),c[0],c[1],c[2],c[3],Rac1ShopLightingProgram.fogByte(d*1024,fog)));
                }
                for (var t:geometry.triangles()) gpu.triangle(scene,null,state,pv.get(t.a()),pv.get(t.b()),pv.get(t.c()));
                var rect=popup(source);
                gpu.clear(text,new Rgba(0,0,0,0),true,0,255);
                if (ui.mode()==Rac1ShopClientFlow.Mode.DENIED || ui.mode()==Rac1ShopClientFlow.Mode.MAXED) {
                    byte[] bytes=source.text().get(ui.mode()==Rac1ShopClientFlow.Mode.DENIED ? source.ids().denied():source.ui().maxed());
                    window(gpu,text,atlas,font,bytes,(int)rect.width(),(int)rect.height(),(int)rect.height()/2);
                } else if (offer.ammo()) {
                    quantityText(gpu,text,atlas,source,rect,ui.quantity(),ui.total());
                } else {
                    line(gpu,text,atlas,font,source.text().get(source.ui().purchase()),6,6,0);
                    line(gpu,text,atlas,font,source.text().get(source.ui().yes()),2,32,0);
                    line(gpu,text,atlas,font,source.text().get(source.ui().no()),2,62,0);
                }
                quad(gpu,scene,null,text,rect.x(),rect.y(),rect.width(),rect.height(),0,0,
                        (float)(rect.width()/512),(float)(rect.height()/128),new Rgba(128,128,128,128),true);
            }
            return gpu.readOverlay(scene);
    }
    private static void quantityText(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface text,Rac1ShopOrderedGpu.Image atlas,
                                     Rac1ShopPreviewSource source,Rac1ShopLayout.Rectangle rect,int quantity,long total) {
        var font=source.shop().graphics().fonts().get("small");
        window(gpu,text,atlas,font,source.text().get(source.ui().howMany()),(int)rect.width(),(int)rect.height(),6);
        line(gpu,text,atlas,font,ascii(quantity),quantity>=100 ? 23:quantity>=10 ? 30:37,50,0);
        line(gpu,text,atlas,font,"<".getBytes(java.nio.charset.StandardCharsets.US_ASCII),12,50,0);
        line(gpu,text,atlas,font,">".getBytes(java.nio.charset.StandardCharsets.US_ASCII),64,50,0);
        line(gpu,text,atlas,font,ascii(total),64,76,2);
    }
    /** Only the original quantity text RTT changes while repeating; no kiosk/actor rebuild. */
    public static final class QuantityTarget implements AutoCloseable {
        private final Rac1ShopPreviewSource source;
        private final Rac1ShopOrderedGpu gpu;
        private final Rac1ShopOrderedGpu.Surface target;
        private final Rac1ShopOrderedGpu.Image atlas;
        private final Rac1ShopLayout.Rectangle rect;
        public QuantityTarget(Rac1ShopPreviewSource source) {
            this.source=source;
            gpu=new Rac1ShopOrderedGpu(512,128);
            try {
                target=gpu.surface(512,128);
                atlas=gpu.image(256,128,source.font(),new Sampler(Filter.LINEAR,Address.CLAMP));
                rect=popup(source);
            } catch (RuntimeException | Error failure) { gpu.close(); throw failure; }
        }
        public Rac1ShopOrderedGpu.Readback frame(int quantity,long total) {
            if (quantity<1 || quantity>200 || total!=quantity) throw new IllegalArgumentException("Original unit ammo price required");
            gpu.beginFrame(0); gpu.clear(target,new Rgba(0,0,0,0),true,0,255);
            quantityText(gpu,target,atlas,source,rect,quantity,total);
            return gpu.readOverlay(target);
        }
        @Override public void close() { gpu.close(); }
    }
    public static Rac1ShopLayout.Rectangle monitor(Rac1ShopPreviewSource source,int screen) {
        var pose=new Rac1ShopKioskPose(source.body()).stage(Rac1ShopKioskPose.Stage.OPEN,0,Rac1ShopKioskPose.monitorArms(source.shop().sceneTables(),1));
        return rect(source,Rac1ShopLayout.monitorVendor(source.body(),pose.chain(),screen,source.shop().sceneTables().margins()));
    }
    public static Rac1ShopLayout.Rectangle popup(Rac1ShopPreviewSource source) {
        var actor=source.shop().models().popup();
        var seq=Arrays.stream(actor.animations()).filter(a -> a.name().equals("sequence_1")).findFirst().orElseThrow();
        float[] chain=Rac1AnimationEvaluator.evaluateChain(actor.skeleton(),seq,0);
        Rac1ShopLayout.Point[] p=new Rac1ShopLayout.Point[3];
        for (int i=0;i<3;i++) {
            int[] list=actor.attachmentChain(i); int at=list[list.length-1]*16;
            p[i]=new Rac1ShopLayout.Point(chain[at+12],chain[at+13],chain[at+14]);
        }
        var a=p[1].minus(p[0]); var b=p[2].minus(p[0]); double al=a.length(),bl=b.length();
        var ua=a.times(1/al); var ub=b.times(1/bl);
        return rect(source,new Rac1ShopLayout.Quad(p[0].plus(ua.times(.03)).plus(ub.times(.04)),ua.times(al-.06),ub.times(bl-.04)));
    }
    private static Rac1ShopLayout.Rectangle rect(Rac1ShopPreviewSource source,Rac1ShopLayout.Quad q) {
        int h=source.pal() ? 448:416; float ty=.63f*(source.pal() ? .756f:.775f);
        var a=pixel(q.corner(),ty,h); var b=pixel(q.opposite(),ty,h);
        return new Rac1ShopLayout.Rectangle(Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.abs(a[0]-b[0]),Math.abs(a[1]-b[1]));
    }
    private static byte[] ascii(long value) { return Long.toString(value).getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    private static void window(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image atlas,
                               com.github.giannigr.racmc.asset.hud.Rac1HudFont font,byte[] bytes,int w,int h,int y) {
        var layout=Rac1ShopTextWindow.layout(font,bytes,new Rac1ShopTextWindow.Window(0,0,w-8,h-8,w/2,y,16,true,false));
        for (var line:layout.lines()) if (line.visible()) line(gpu,target,atlas,font,line.bytes(),line.x(),line.y(),0);
    }
    private static void line(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image atlas,
                             com.github.giannigr.racmc.asset.hud.Rac1HudFont font,byte[] bytes,double x,double y,int align) {
        var layout=Rac1ShopGlyphLayout.line(font,bytes,Rac1ShopPrototypeTextIds.GS_TEXT_COLOR,true);
        if (align==2) x-=layout.advance();
        for (var g:layout.glyphs()) {
            int c=g.gsColor();
            quad(gpu,target,atlas,null,x+g.x(),y+g.y(),g.width(),g.height(),g.u()/256f,g.v()/128f,
                    (g.u()+g.width())/256f,(g.v()+g.height())/128f,new Rgba(c&255,c>>>8&255,c>>>16&255,c>>>24&255),false);
        }
    }
    private static double[] pixel(Rac1ShopLayout.Point p,float tanY,int height) {
        double d=3.8-p.x();
        if (d<=0) throw new IllegalArgumentException("Preview monitor behind original cut camera");
        return new double[]{(-p.z()/.63/d+1)*256,(1-(p.y()-1.5)/tanY/d)*height/2};
    }
    private static void quad(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Image image,
                             Rac1ShopOrderedGpu.Surface source,double x,double y,double w,double h,
                             float s0,float t0,float s1,float t1,Rgba color,boolean copy) {
        var state=new Rac1ShopOrderedGpu.State(copy ? 0x64:0x44,128,0,true,true,false,0,false,0,0,0);
        var a=vertex(target,x,y,s0,t0,color); var b=vertex(target,x+w,y,s1,t0,color);
        var c=vertex(target,x,y+h,s0,t1,color); var d=vertex(target,x+w,y+h,s1,t1,color);
        if (copy) { gpu.copyTriangle(target,source,state,a,b,c); gpu.copyTriangle(target,source,state,c,b,d); }
        else { gpu.triangle(target,image,state,a,b,c); gpu.triangle(target,image,state,c,b,d); }
    }
    private static Rac1ShopOrderedGpu.Vertex vertex(Rac1ShopOrderedGpu.Surface target,double x,double y,float s,float t,Rgba c) {
        return new Rac1ShopOrderedGpu.Vertex((float)(2*x/target.width()-1),(float)(1-2*y/target.height()),
                (float)(0xfffff0/16777216.0*2-1),1,s,t,c.r(),c.g(),c.b(),c.a(),255);
    }
}
