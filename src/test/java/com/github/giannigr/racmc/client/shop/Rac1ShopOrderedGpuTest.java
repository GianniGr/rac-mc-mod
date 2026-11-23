package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static com.github.giannigr.racmc.client.shop.Rac1ShopGsPixelProgram.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/** One owned never-shown 1x1 context in this test JVM only, not any running game process. */
class Rac1ShopOrderedGpuTest {
    static final Rac1ShopOrderedGpu.State MOBY=new Rac1ShopOrderedGpu.State(0x44,128,96,false,false,false,0,false,0,0,0);
    @ParameterizedTest @ValueSource(booleans={false,true})
    void hiddenIntegerGpuMatchesCpuMasksOverlapSharedAliasAndLifecycle(boolean accelerated) throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_C1_GPU")),"Explicit owned C1 GPU opt-in");
        try (var context=new Hidden()) {
            int external=glGenTextures(); glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D,external);
            glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND); glEnable(GL_SCISSOR_TEST);
            glEnable(GL_CLIP_DISTANCE0); glPolygonMode(GL_FRONT_AND_BACK,GL_LINE);
            glPixelStorei(GL_PACK_SWAP_BYTES,GL_TRUE); glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_TRUE);
            glViewport(7,8,11,13); glScissor(1,2,3,4);
            try (var gpu=new Rac1ShopOrderedGpu(8,8)) {
                if (accelerated) Assumptions.assumeTrue(gpu.enableTextureBarrier(),"Optional texture barrier unavailable");
                var scene=gpu.surface(8,8); var rtt=gpu.surface(4,2);
                assertThrows(IllegalArgumentException.class,() -> gpu.image(1,1,new byte[4],new Sampler(null,Address.CLAMP)));
                assertThrows(IllegalStateException.class,() -> gpu.read(scene));
                int cases=0;
                for (int alpha:new int[]{0,13,64,95,96,128,255})
                    for (int dz:new int[]{9,10,11}) {
                        Rgba src=new Rgba(200,40,0,alpha),dst=new Rgba(100,80,1,23);
                        gpu.clearDepth(dz); gpu.clear(scene,dst,false,0);
                        solid(gpu,scene,MOBY,src,10);
                        var expected=fragment(src,dst,10,dz,0,false);
                        assertEvery(gpu.read(scene),expected); cases++;
                    }
                var masked=new Rac1ShopOrderedGpu.State(0x44,128,96,false,false,true,0xff0000ff,false,0,0,0);
                gpu.clearDepth(10); gpu.clear(scene,new Rgba(100,80,1,23),false,0);
                solid(gpu,scene,masked,new Rgba(200,40,0,128),11);
                assertEvery(gpu.read(scene),fragment(new Rgba(200,40,0,128),new Rgba(100,80,1,23),11,10,0xff0000ff,true));
                for (int alpha:new int[]{64,95,96,128}) {
                    try (var image=gpu.image(1,1,new byte[]{(byte)200,80,64,(byte)alpha},new Sampler(Filter.LINEAR,Address.CLAMP))) {
                        gpu.clearDepth(10); gpu.clear(scene,new Rgba(100,40,0,23),false,0);
                        gpu.triangle(scene,image,MOBY,v(-1,-1,11,128),v(3,-1,11,128),v(-1,3,11,128));
                        assertEvery(gpu.read(scene),fragment(new Rgba(200,80,64,alpha),new Rgba(100,40,0,23),11,10,0,false));
                    }
                }
                for (var address:Address.values()) {
                    try (var image=gpu.image(2,1,new byte[]{10,10,10,(byte)128,(byte)200,(byte)200,(byte)200,(byte)128},
                            new Sampler(Filter.POINT,address))) {
                        gpu.clearDepth(0); gpu.clear(scene,new Rgba(0,0,0,0),false,0);
                        Rgba neutral=new Rgba(128,128,128,128);
                        gpu.triangle(scene,image,MOBY,vertex(-1,-1,10,1.25f,.5f,neutral),
                                vertex(3,-1,10,1.25f,.5f,neutral),vertex(-1,3,10,1.25f,.5f,neutral));
                        int value=address==Address.REPEAT ? 10:200;
                        assertEvery(gpu.read(scene),new Result(new Rgba(value,value,value,128),10));
                    }
                }
                for (int blend:new int[]{0x64,0x68}) {
                    gpu.clearDepth(0); gpu.clear(scene,new Rgba(51,51,51,23),false,0);
                    var fixedState=new Rac1ShopOrderedGpu.State(blend,32,0,true,true,false,0,false,0,0,0);
                    solid(gpu,scene,fixedState,new Rgba(204,204,204,0),10);
                    int value=blend==0x64 ? 89:102;
                    assertEvery(gpu.read(scene),new Result(new Rgba(value,value,value,0),10));
                }
                // A failure followed by an equal-Z pass must not be globally reordered.
                gpu.clearDepth(10); gpu.clear(scene,new Rgba(20,20,20,7),false,0);
                solid(gpu,scene,MOBY,new Rgba(120,120,120,64),11);
                solid(gpu,scene,MOBY,new Rgba(50,50,50,128),10);
                assertEvery(gpu.read(scene),new Result(new Rgba(50,50,50,128),10));
                // A smaller RTT clear aliases only TOP-LEFT scene Z, not a separate private Z attachment.
                gpu.clear(rtt,new Rgba(1,2,3,4),true,3);
                var shared=gpu.read(scene).z24();
                for (int y=0;y<8;y++) for (int x=0;x<8;x++)
                    assertEquals(y<2 && x<4 ? 3:10,shared[y*8+x]);
                gpu.clearDepth(0);
                var fixed=new Rac1ShopOrderedGpu.State(0x64,128,0,true,true,false,0,false,0,0,0);
                copy(gpu,scene,rtt,fixed);
                var copied=gpu.read(scene);
                assertEvery(copied,new Result(new Rgba(1,2,3,4),0xfffff0));
                assertThrows(IllegalArgumentException.class,() -> gpu.copyTriangle(scene,scene,MOBY,v(-1,-1,10,128),v(3,-1,10,128),v(-1,3,10,128)));
                int retired=rtt.colorName(); rtt.close();
                assertFalse(glIsTexture(retired),"Dispose before any replacement name can be reused");
                assertThrows(IllegalStateException.class,rtt::colorName);
                var replacement=gpu.surface(2,2); replacement.close(); replacement.close();
                assertThrows(IllegalArgumentException.class,() -> gpu.surface(9,8));
                var lifecycle=new Rac1ShopResources.Lifecycle();
                var first=new java.util.concurrent.atomic.AtomicInteger();
                var resized=new java.util.concurrent.atomic.AtomicInteger();
                var partial=new java.util.concurrent.atomic.AtomicInteger();
                lifecycle.ensure("c1-disc-fixture",8,8,owner -> {
                    var g=owner.own(new Rac1ShopOrderedGpu(8,8)); first.set(g.surface(8,8).colorName());
                });
                lifecycle.ensure("c1-disc-fixture",8,8,owner -> fail("Same generation must not allocate per frame"));
                lifecycle.ensure("c1-disc-fixture",4,4,owner -> {
                    assertFalse(glIsTexture(first.get()),"Retire before replacement can reuse native names");
                    var g=owner.own(new Rac1ShopOrderedGpu(4,4)); resized.set(g.surface(4,4).colorName());
                });
                lifecycle.reload(); assertFalse(glIsTexture(resized.get()));
                assertThrows(Exception.class,() -> lifecycle.ensure("c1-other-disc",4,4,owner -> {
                    var g=owner.own(new Rac1ShopOrderedGpu(4,4)); partial.set(g.surface(4,4).colorName());
                    throw new Exception("Owned C1 partial factory failure fixture");
                }));
                assertFalse(glIsTexture(partial.get())); lifecycle.logout(); lifecycle.close();
                System.out.printf("C1 hardware integer proof: %d alpha/depth cases, FRAME/ZMSK, destination alpha, overlap, shared RTT alias/copy and disposal pass%n",cases);
            } finally {
                assertEquals(GL_TEXTURE5,glGetInteger(GL_ACTIVE_TEXTURE));
                assertEquals(external,glGetInteger(GL_TEXTURE_BINDING_2D));
                assertTrue(glIsEnabled(GL_DEPTH_TEST)); assertTrue(glIsEnabled(GL_BLEND)); assertTrue(glIsEnabled(GL_SCISSOR_TEST));
                assertTrue(glIsEnabled(GL_CLIP_DISTANCE0));
                int[] vp=new int[4]; glGetIntegerv(GL_VIEWPORT,vp); assertArrayEquals(new int[]{7,8,11,13},vp);
                int[] polygon=new int[2]; glGetIntegerv(GL_POLYGON_MODE,polygon); assertEquals(GL_LINE,polygon[0]);
                assertTrue(glGetBoolean(GL_PACK_SWAP_BYTES)); assertTrue(glGetBoolean(GL_UNPACK_SWAP_BYTES));
                glDeleteTextures(external);
            }
            assertEquals(GL_NO_ERROR,glGetError()); context.unfocused();
        }
    }
    static void solid(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.State state,Rgba color,int z) {
        gpu.triangle(target,null,state,vertex(-1,-1,z,0,1,color),vertex(3,-1,z,2,1,color),vertex(-1,3,z,0,-1,color));
    }
    static void copy(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopOrderedGpu.Surface source,Rac1ShopOrderedGpu.State state) {
        Rgba neutral=new Rgba(128,128,128,128);
        gpu.copyTriangle(target,source,state,vertex(-1,-1,0xfffff0,0,1,neutral),vertex(3,-1,0xfffff0,2,1,neutral),vertex(-1,3,0xfffff0,0,-1,neutral));
    }
    static Rac1ShopOrderedGpu.Vertex v(float x,float y,int z,int alpha) { return vertex(x,y,z,0,0,new Rgba(128,128,128,alpha)); }
    static Rac1ShopOrderedGpu.Vertex vertex(float x,float y,int z,float s,float t,Rgba c) {
        return new Rac1ShopOrderedGpu.Vertex(x,y,(float)(z/16777216.0*2-1),1,s,t,c.r(),c.g(),c.b(),c.a(),255);
    }
    static void assertEvery(Rac1ShopOrderedGpu.Readback read,Result expected) {
        byte[] rgba=read.rgba(); int[] z=read.z24(); Rgba c=expected.color();
        for (int i=0;i<z.length;i++) {
            assertEquals(c.r(),rgba[i*4]&255,"red pixel"+i); assertEquals(c.g(),rgba[i*4+1]&255);
            assertEquals(c.b(),rgba[i*4+2]&255); assertEquals(c.a(),rgba[i*4+3]&255);
            assertEquals(expected.depth24(),z[i],"depth pixel"+i);
        }
    }
    static final class Hidden implements AutoCloseable {
        private long window;
        Hidden() {
            assertTrue(glfwInit());
            glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3); glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            window=glfwCreateWindow(1,1,"P35 owned C1-A proof - never shown",NULL,NULL);
            assertNotEquals(NULL,window); unfocused(); glfwMakeContextCurrent(window); GL.createCapabilities();
            String renderer=glGetString(GL_RENDERER);
            assertFalse(renderer.toLowerCase(java.util.Locale.ROOT).matches(".*(software|llvmpipe|softpipe|swiftshader|gdi generic|lavapipe).*"));
            System.out.printf("P35 OWN hidden C1-A: %s / %s / %s visible=0 focused=0%n",glGetString(GL_VENDOR),renderer,glGetString(GL_VERSION));
        }
        void unfocused() {
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_VISIBLE));
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_FOCUSED));
        }
        @Override public void close() {
            unfocused(); GL.setCapabilities(null); glfwMakeContextCurrent(NULL);
            glfwDestroyWindow(window); window=NULL; glfwTerminate();
        }
    }
}
