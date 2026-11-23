package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopOrderedGpuTest.*;

/** Synthetic, owned hidden GPU only: no retail reads, Minecraft launch or window focus. */
class Rac1ShopOverlayCoverageTest {
    @Test void exactOpaqueBatchMatchesOrderedColorCoverageAndQuantizedSharedDepth() {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        try (var context=new Hidden(); var gpu=new Rac1ShopOrderedGpu(8,8)) {
            var target=gpu.surface(8,8);
            var a=vertex(-1,-1,100000,0,0,new Rgba(100,50,10,128));
            var b=vertex(3,-1,100000,0,0,new Rgba(100,50,10,128));
            var c=vertex(-1,3,100000,0,0,new Rgba(100,50,10,128));
            var d=vertex(-1,-1,90000,0,0,new Rgba(200,60,30,128));
            var e=vertex(3,-1,90000,0,0,new Rgba(200,60,30,128));
            var f=vertex(-1,3,90000,0,0,new Rgba(200,60,30,128));
            var vertices=java.util.List.of(a,b,c,d,e,f);
            gpu.clearDepth(50000); gpu.clear(target,new Rgba(0,0,0,0),false,0,0);
            gpu.triangle(target,null,MOBY,a,b,c); gpu.triangle(target,null,MOBY,d,e,f);
            var expected=gpu.read(target); var coverage=gpu.readOverlay(target);
            gpu.clearDepth(50000); gpu.clear(target,new Rgba(0,0,0,0),false,0,0);
            gpu.orderedBatch(() -> { gpu.triangle(target,null,MOBY,a,b,c); gpu.triangle(target,null,MOBY,d,e,f); });
            assertArrayEquals(expected.rgba(),gpu.read(target).rgba());
            assertArrayEquals(expected.z24(),gpu.read(target).z24());
            gpu.clearDepth(50000); gpu.clear(target,new Rgba(0,0,0,0),false,0,0);
            gpu.opaqueTriangles(target,null,MOBY,vertices);
            assertArrayEquals(expected.rgba(),gpu.read(target).rgba());
            assertArrayEquals(expected.z24(),gpu.read(target).z24());
            assertArrayEquals(coverage.rgba(),gpu.readOverlay(target).rgba());
            // Logical depth produced by a batch still gates the ordinary destination-read path.
            solid(gpu,target,MOBY,new Rgba(255,0,0,128),80000);
            assertArrayEquals(expected.rgba(),gpu.read(target).rgba());
            try (var cutout=gpu.image(1,1,new byte[]{100,100,100,127},
                    new Rac1ShopMaterials.Sampler(Rac1ShopMaterials.Filter.LINEAR,Rac1ShopMaterials.Address.CLAMP))) {
                assertThrows(IllegalArgumentException.class,() -> gpu.opaqueTriangles(target,cutout,MOBY,vertices));
            }
            context.unfocused();
        }
    }
    @Test void dynamicTickerUsesPointAtlasHalfBrightnessAndOriginalEdgeClipping() {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        byte[] pixels=new byte[64*64*4]; java.util.Arrays.fill(pixels,(byte)128);
        var atlas=new Rac1ShopPreviewSource.Pixels(64,64,pixels);
        var first=new Rac1ShopTickerLayout.Line(java.util.List.of(new Rac1ShopTickerLayout.Glyph(0,8,0,0)),18);
        var moved=new Rac1ShopTickerLayout.Line(java.util.List.of(new Rac1ShopTickerLayout.Glyph(6,8,0,0)),18);
        try (var context=new Hidden()) {
            byte[] a,b;
            try (var target=new Rac1ShopTickerDrawing.Target(atlas)) {
                a=target.frame(first).rgba(); b=target.frame(moved).rgba();
                for (int frame=0;frame<50;frame++) target.frame(first);
            }
            assertEquals(0,a[(10*512+3)*4]&255);
            assertEquals(64,a[(10*512+4)*4]&255);
            assertEquals(255,a[(10*512+4)*4+3]&255);
            assertEquals(0,a[(10*512+18)*4]&255);
            assertFalse(java.util.Arrays.equals(a,b),"Scrolling changes the actual monitor target");
            context.unfocused();
        }
    }
    @Test void separateCoveragePreservesGrayShapesBlackMonitorsAndTransparentWorldHoles() {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        try (var context=new Hidden(); var gpu=new Rac1ShopOrderedGpu(8,8)) {
            var scene=gpu.surface(8,8); var panel=gpu.surface(4,2);
            gpu.clearDepth(0); gpu.clear(scene,new Rgba(0,0,0,0),false,0,0);
            assertEvery(gpu.readOverlay(scene),new Result(new Rgba(0,0,0,0),0));
            solid(gpu,scene,MOBY,new Rgba(24,24,24,128),10);
            // Exactly the old gray backdrop color remains opaque when real geometry wrote it.
            assertEvery(gpu.readOverlay(scene),new Result(new Rgba(24,24,24,255),10));
            gpu.clear(scene,new Rgba(0,0,0,0),false,0,0); gpu.clearDepth(11);
            solid(gpu,scene,MOBY,new Rgba(24,24,24,128),10); // occluded triangle cannot cover world
            assertEvery(gpu.readOverlay(scene),new Result(new Rgba(0,0,0,0),11));
            gpu.clearDepth(0);
            gpu.clear(panel,new Rgba(0,0,0,0),false,0,255); // black monitor is opaque despite GS alpha0
            var copy=new Rac1ShopOrderedGpu.State(0x64,128,0,true,true,false,0,false,0,0,0);
            copy(gpu,scene,panel,copy);
            assertEvery(gpu.readOverlay(scene),new Result(new Rgba(0,0,0,255),0xfffff0));
            gpu.clear(panel,new Rgba(0,0,0,0),false,0,0);
            copy(gpu,scene,panel,copy);
            assertEvery(gpu.readOverlay(scene),new Result(new Rgba(0,0,0,0),0xfffff0));
            context.unfocused();
        }
    }

    @Test void partialAlphaUnpremultipliesOnceWithoutChangingOriginalGsColorOrDepth() {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        try (var context=new Hidden(); var gpu=new Rac1ShopOrderedGpu(8,8)) {
            var scene=gpu.surface(8,8);
            gpu.clearDepth(0); gpu.clear(scene,new Rgba(0,0,0,0),false,0,0);
            var blend=new Rac1ShopOrderedGpu.State(0x44,128,0,true,true,false,0,false,0,0,0);
            solid(gpu,scene,blend,new Rgba(200,100,40,64),10);
            assertEvery(gpu.read(scene),new Result(new Rgba(100,50,20,64),10));
            byte[] overlay=gpu.readOverlay(scene).rgba();
            for (int i=0;i<overlay.length;i+=4) {
                assertEquals(128,overlay[i+3]&255);
                assertEquals(200,overlay[i]&255,1);
                assertEquals(100,overlay[i+1]&255,1);
                assertEquals(40,overlay[i+2]&255,1);
            }
            assertThrows(IllegalArgumentException.class,() -> gpu.clear(scene,new Rgba(0,0,0,0),false,0,256));
            var additive=new Rac1ShopOrderedGpu.State(0x68,32,0,true,true,false,0,false,0,0,0);
            solid(gpu,scene,additive,new Rgba(0,0,0,0),11);
            assertThrows(IllegalStateException.class,() -> gpu.readOverlay(scene),
                    "Do not silently flatten PS2 additive destination reads into standard world alpha");
            context.unfocused();
        }
    }
}
