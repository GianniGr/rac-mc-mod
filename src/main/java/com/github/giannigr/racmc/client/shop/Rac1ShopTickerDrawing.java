package com.github.giannigr.racmc.client.shop;

import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/** Dynamic monitor0 only. Never redraw the entire static kiosk for each scroll step. */
public final class Rac1ShopTickerDrawing {
    private Rac1ShopTickerDrawing() {}
    public static Rac1ShopOrderedGpu.Readback render(Rac1ShopPreviewSource.Pixels atlas,Rac1ShopTickerLayout.Line line) {
        try (var target=new Target(atlas)) { return target.frame(line); }
    }
    public static final class Target implements AutoCloseable {
        private final Rac1ShopOrderedGpu gpu;
        private final Rac1ShopOrderedGpu.Surface target;
        private final Rac1ShopOrderedGpu.Image image;
        public Target(Rac1ShopPreviewSource.Pixels atlas) {
            if (atlas.width()!=64 || atlas.height()!=64) throw new IllegalArgumentException("Original LED atlas required");
            gpu=new Rac1ShopOrderedGpu(512,128);
            try { target=gpu.surface(512,128); image=gpu.image(64,64,atlas.rgba(),new Sampler(Filter.POINT,Address.CLAMP)); }
            catch (RuntimeException | Error failure) { gpu.close(); throw failure; }
        }
        public Rac1ShopOrderedGpu.Readback frame(Rac1ShopTickerLayout.Line line) {
            gpu.beginFrame(0); gpu.clear(target,new Rgba(0,0,0,0),false,0,255);
            gpu.orderedBatch(() -> {
                for (var glyph:line.glyphs()) {
                    if (glyph.x()+18<=4 || glyph.x()>=226) continue;
                    double left=Math.max(4,glyph.x()),right=Math.min(226,glyph.x()+18);
                    float s0=(float)(glyph.u()+(left-glyph.x())*.5)/64;
                    float s1=(float)(glyph.u()+(right-glyph.x())*.5)/64;
                    var state=new Rac1ShopOrderedGpu.State(0x44,128,0,true,true,false,0,false,0,0,0);
                    var a=vertex(left,glyph.y(),s0,glyph.v()/64f);
                    var b=vertex(right,glyph.y(),s1,glyph.v()/64f);
                    var c=vertex(left,glyph.y()+18,s0,(glyph.v()+9)/64f);
                    var d=vertex(right,glyph.y()+18,s1,(glyph.v()+9)/64f);
                    gpu.triangle(target,image,state,a,b,c); gpu.triangle(target,image,state,c,b,d);
                }
            });
            return gpu.readOverlay(target);
        }
        @Override public void close() { gpu.close(); }
    }
    private static Rac1ShopOrderedGpu.Vertex vertex(double x,double y,float s,float t) {
        return new Rac1ShopOrderedGpu.Vertex((float)(2*x/512-1),(float)(1-2*y/128),.99f,1,s,t,64,64,64,128,255);
    }
}
