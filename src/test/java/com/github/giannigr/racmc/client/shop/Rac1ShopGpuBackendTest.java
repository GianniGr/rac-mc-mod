package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Opt-in OWN never-shown/unfocused context, not a game client; synthetic original-format pixels only. */
class Rac1ShopGpuBackendTest {
    @Test void actualSamplerFixedBlendStateRestorationAndNativeDisposal() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHOP_GPU")), "Coordinated opt-in hidden shop GPU test");
        assertTrue(glfwInit());
        long window = NULL; int externalTexture = 0, externalSampler = 0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1,1,"P35 owned hidden shop GPU regression",NULL,NULL);
            assertNotEquals(NULL,window);
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_VISIBLE));
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_FOCUSED));
            glfwMakeContextCurrent(window); GL.createCapabilities();
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            String renderer = glGetString(GL_RENDERER);
            System.out.printf("P35 OWN hidden1x1 GPU: vendor=%s renderer=%s version=%s GLSL=%s visible=%d focused=%d%n",
                    glGetString(GL_VENDOR),renderer,glGetString(GL_VERSION),glGetString(GL_SHADING_LANGUAGE_VERSION),
                    glfwGetWindowAttrib(window,GLFW_VISIBLE),glfwGetWindowAttrib(window,GLFW_FOCUSED));
            assertFalse(renderer.toLowerCase(java.util.Locale.ROOT).matches(".*(software|llvmpipe|softpipe|swiftshader|gdi generic|lavapipe).*"),
                    "Software rendering is not hardware GPU evidence; do not silently fall back");
            externalTexture = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,externalTexture);
            externalSampler = glGenSamplers(); glBindSampler(0,externalSampler);
            glActiveTexture(GL_TEXTURE3); glEnable(GL_DEPTH_TEST); glViewport(7,8,11,13);
            var backend = new Rac1ShopGpuBackend();
            var target = backend.target(2,2);
            var texture = backend.upload(new Rac1Texture(1,1,new byte[]{0,(byte)204,0,0}),
                    Rac1ShopMaterials.Pass.LED_NOISE.sampler());
            var clamped = backend.upload(new Rac1Texture(1,1,new byte[]{(byte)255,0,0,0}),
                    Rac1ShopMaterials.Pass.GLASS.sampler());
            int clampImage = clamped.id();
            int image = texture.id(), fbo = target.framebuffer(), color = target.color();
            backend.clear(target,.2f,0,0,1);
            backend.draw(target,texture,Rac1ShopMaterials.Pass.LED_NOISE,32,new float[]{
                    0,0,0,0,1,1,1,1, 0,2,0,1,1,1,1,1, 2,0,1,0,1,1,1,1, 2,2,1,1,1,1,1,1});
            assertEquals(GL_TEXTURE3,glGetInteger(GL_ACTIVE_TEXTURE));
            assertTrue(glIsEnabled(GL_DEPTH_TEST));
            int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT,viewport);
            assertArrayEquals(new int[]{7,8,11,13},viewport);
            glActiveTexture(GL_TEXTURE0);
            assertEquals(externalTexture,glGetInteger(GL_TEXTURE_BINDING_2D));
            assertEquals(externalSampler,glGetInteger(GL_SAMPLER_BINDING));
            glBindTexture(GL_TEXTURE_2D,image);
            assertEquals(GL_NEAREST,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER));
            assertEquals(GL_REPEAT,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S));
            assertEquals(GL_REPEAT,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T));
            System.out.printf("shop native noise sampler: min=%d mag=%d wrapS=%d wrapT=%d%n",
                    glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER),glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER),
                    glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S),glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T));
            glBindTexture(GL_TEXTURE_2D,clampImage);
            assertEquals(GL_LINEAR,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER));
            assertEquals(GL_CLAMP_TO_EDGE,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S));
            assertEquals(GL_CLAMP_TO_EDGE,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T));
            System.out.printf("shop native glass sampler: min=%d mag=%d wrapS=%d wrapT=%d%n",
                    glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER),glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER),
                    glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S),glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T));
            glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo);
            ByteBuffer pixels = BufferUtils.createByteBuffer(16);
            glReadPixels(0,0,2,2,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            for (int i = 0; i < 4; i++) assertEquals(102,pixels.get(i*4)&255,1);
            System.out.printf("shop fixed-add readback: sourceR=204 sourceAlpha=0 destinationR=51 FIX=32/128 actualRed=%s expected=102%n",
                    java.util.Arrays.toString(java.util.stream.IntStream.range(0,4).map(i -> pixels.get(i*4)&255).toArray()));
            backend.close(); backend.close();
            assertFalse(glIsTexture(image)); assertFalse(glIsTexture(clampImage)); assertFalse(glIsTexture(color)); assertFalse(glIsFramebuffer(fbo));
            assertThrows(IllegalStateException.class,texture::id);

            var lifecycle = new Rac1ShopResources.Lifecycle();
            AtomicInteger first = new AtomicInteger(), resized = new AtomicInteger(), failed = new AtomicInteger();
            var retired = new java.util.concurrent.atomic.AtomicReference<Rac1ShopGpuBackend.Target>();
            lifecycle.ensure("disc-a",512,128,o -> {
                var gpu = o.own(new Rac1ShopGpuBackend()); var firstTarget = gpu.target(512,128);
                first.set(firstTarget.color()); retired.set(firstTarget);
            });
            lifecycle.ensure("disc-a",512,128,o -> fail("No per-frame native allocation"));
            lifecycle.ensure("disc-a",1024,256,o -> {
                assertFalse(glIsTexture(first.get()),"Old native texture must be deleted BEFORE replacement allocation can reuse its name");
                var gpu = o.own(new Rac1ShopGpuBackend()); resized.set(gpu.target(512,128).color());
            });
            assertThrows(IllegalStateException.class,() -> retired.get().color());
            System.out.println("shop resize disposal checked before replacement allocation; GL name reuse is allowed");
            lifecycle.reload(); assertFalse(glIsTexture(resized.get()));
            assertThrows(Exception.class, () -> lifecycle.ensure("disc-b",512,128,o -> {
                var gpu = o.own(new Rac1ShopGpuBackend()); failed.set(gpu.target(512,128).color());
                throw new Exception("Injected partial original scene creation failure");
            }));
            assertFalse(glIsTexture(failed.get())); lifecycle.logout(); lifecycle.close();
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_VISIBLE));
            assertEquals(GLFW_FALSE,glfwGetWindowAttrib(window,GLFW_FOCUSED));
            System.out.println("shop native lifecycle: state restored; resize/reload/partial-failure/disposal checked; own window never shown/focused");
            assertEquals(GL_NO_ERROR,glGetError());
        } finally {
            if (window != NULL) {
                glBindSampler(0,0); glDeleteSamplers(externalSampler); glDeleteTextures(externalTexture);
                GL.setCapabilities(null); glfwMakeContextCurrent(NULL); glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }
}
