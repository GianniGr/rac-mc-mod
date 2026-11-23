package com.github.giannigr.racmc.client.shop;

import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopOrderedGpuTest.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;

class Rac1ShopScopedBindingsTest {
    record Proof(Rac1ShopOrderedGpu.Readback pixels,Rac1ShopOrderedGpu.DrawCounts counts) {}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void scopedStateMatchesReferenceAcrossMaterialClearOpaqueCopyReadAndExceptions(boolean barrier) throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")));
        try(var hidden=new Hidden()) {
            glEnable(GL_BLEND); glEnable(GL_SCISSOR_TEST); glViewport(7,8,11,13);
            var reference=draw(false,barrier);
            var fast=draw(true,barrier);
            assertArrayEquals(reference.pixels.rgba(),fast.pixels.rgba());
            assertArrayEquals(reference.pixels.z24(),fast.pixels.z24());
            assertEquals(reference.counts.ordered(),fast.counts.ordered());
            assertEquals(reference.counts.opaque(),fast.counts.opaque());
            assertEquals(reference.counts.triangles(),fast.counts.triangles());
            if(barrier) assertTrue(fast.counts.bindingSetups()<reference.counts.bindingSetups());
            assertTrue(glIsEnabled(GL_BLEND)); assertTrue(glIsEnabled(GL_SCISSOR_TEST));
            var viewport=new int[4]; glGetIntegerv(GL_VIEWPORT,viewport); assertArrayEquals(new int[]{7,8,11,13},viewport);
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }
    private Proof draw(boolean fast,boolean barrier) {
        try(var gpu=new Rac1ShopOrderedGpu(8,8,true,fast)) {
            if(barrier) Assumptions.assumeTrue(gpu.enableTextureBarrier());
            var scene=gpu.surface(8,8); var rtt=gpu.surface(4,2);
            var image=gpu.image(1,1,new byte[]{80,40,20,(byte)128},new Sampler(Filter.LINEAR,Address.CLAMP));
            gpu.beginFrame(0); gpu.clear(scene,new Rgba(10,20,30,5),false,0,0);
            gpu.clear(rtt,new Rgba(1,2,3,4),false,0,255);
            gpu.orderedBatch(() -> {
                for(int i=0;i<12;i++) gpu.triangle(scene,image,MOBY,v(-1,-1,10,128),v(3,-1,10,128),v(-1,3,10,128));
                gpu.orderedBatch(() -> solid(gpu,scene,MOBY,new Rgba(20,80,120,64),11));
                gpu.read(scene); // Read scope must not leave a trusted binding behind.
                gpu.clear(rtt,new Rgba(3,2,1,4),true,3);
                gpu.opaqueTriangles(scene,image,MOBY,List.of(v(-1,-1,10,128),v(3,-1,10,128),v(-1,3,10,128)));
                solid(gpu,scene,MOBY,new Rgba(40,60,100,96),11);
                var fixed=new Rac1ShopOrderedGpu.State(0x64,128,0,true,true,false,0,false,0,0,0);
                copy(gpu,scene,rtt,fixed);
                solid(gpu,scene,MOBY,new Rgba(60,80,100,128),0xfffff1);
                var temporary=gpu.image(1,1,new byte[]{1,2,3,4},new Sampler(Filter.POINT,Address.CLAMP));
                temporary.close();
                solid(gpu,scene,MOBY,new Rgba(100,80,60,128),0xfffff2);
                assertThrows(IllegalStateException.class,() -> gpu.orderedBatch(() -> {
                    solid(gpu,scene,MOBY,new Rgba(100,80,60,128),0xfffff2);
                    throw new IllegalStateException("Caught nested fixture failure");
                }));
                solid(gpu,scene,MOBY,new Rgba(100,80,60,128),0xfffff2);
            });
            assertThrows(IllegalStateException.class,() -> gpu.orderedBatch(() -> {
                solid(gpu,scene,MOBY,new Rgba(100,80,60,128),0xfffff2);
                throw new IllegalStateException("Fixture callback failure");
            }));
            solid(gpu,scene,MOBY,new Rgba(20,40,60,128),0xfffff3);
            return new Proof(gpu.read(scene),gpu.drawCounts());
        }
    }
}
