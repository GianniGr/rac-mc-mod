package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipFile;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Reuses the isolated P25 GPU-test pattern: never-shown/unfocused context, NOT a game client.
 * Optional private RGBA cap fixture is read only; synthetic pixels are the committed default.
 * Use test --rerun when changing the opt-in environment/fixture (Gradle does not track it). */
class Rac1HudEdgeSamplingGpuTest {
    @Test void fullExtentCapUvsDoNotWrapOpaqueEdgeIntoOutsideTransparentColumns() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHADER_GPU")),
                "Opt in with RACMC_TEST_SHADER_GPU=1");
        byte[] rgba = cap();
        assertTrue(glfwInit());
        long window = NULL;
        int program = 0, vao = 0, vbo = 0, fbo = 0, image = 0, target = 0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1, 1, "RaC isolated HUD edge sampler", NULL, NULL);
            assertNotEquals(NULL, window);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            // The production sampler enforces Minecraft's render-thread contract too.
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            System.out.println("Isolated HUD GPU: " + glGetString(GL_RENDERER));
            program = program();
            vao = glGenVertexArrays(); glBindVertexArray(vao);
            vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
            // Two 64x64 cap quads. Second is the original 180-degree cap.
            // UV endpoints stay exactly 0..1, like GuiGraphics.blit; no crop/inset.
            glBufferData(GL_ARRAY_BUFFER, new float[]{
                -1,-1,0, 0,1,  0,-1,0, 1,1,  0,1,0, 1,0,
                -1,-1,0, 0,1,  0,1,0, 1,0,  -1,1,0, 0,0,
                0,-1,0, 1,0,  1,-1,0, 0,0,  1,1,0, 0,1,
                0,-1,0, 1,0,  1,1,0, 0,1,  0,1,0, 1,1
            }, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0L);
            glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12L);
            target = glGenTextures(); glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 128, 64, 0, GL_RGBA, GL_FLOAT, (FloatBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            image = glGenTextures(); glBindTexture(GL_TEXTURE_2D, image);
            ByteBuffer input = BufferUtils.createByteBuffer(rgba.length); input.put(rgba).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 32, 32, 0, GL_RGBA, GL_UNSIGNED_BYTE, input);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            assertEquals(GL_REPEAT, glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S));
            assertEquals(GL_REPEAT, glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T));
            glViewport(0, 0, 128, 64);
            glDisable(GL_DITHER); glDisable(GL_BLEND); glDisable(GL_CULL_FACE);
            glUseProgram(program);
            float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
            glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
            glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, identity);
            glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            float[] before = render();
            int oldLeft = visible(before, 0), oldRight = visible(before, 127);
            System.out.printf("REPEAT ghost pixels at outside columns: left=%d right=%d%n", oldLeft, oldRight);
            assertTrue(oldLeft > 10 && oldRight > 10, "Reproduce both original outside lines");
            // Actual production state setter; no test-only replacement shader or UV fix.
            Rac1HudSampling.clampBoundTexture();
            assertEquals(GL_CLAMP_TO_EDGE, glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S));
            assertEquals(GL_CLAMP_TO_EDGE, glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T));
            float[] after = render();
            assertEquals(0, visible(after, 0), "Unrotated outside edge remains transparent");
            assertEquals(0, visible(after, 127), "Rotated outside edge remains transparent");
            for (int y = 0; y < 64; y++) for (int x = 1; x < 127; x++) {
                if (x == 63 || x == 64) continue; // u=1 joins also stop borrowing transparent opposite edge.
                int at = (y * 128 + x) * 4;
                for (int c = 0; c < 4; c++)
                    assertEquals(before[at + c], after[at + c], 1e-6F, "Non-boundary pixels unchanged");
            }
            System.out.println("CLAMP_TO_EDGE ghost pixels: left=0 right=0; all non-boundary pixels unchanged");
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (window != NULL) {
                glDeleteProgram(program); glDeleteBuffers(vbo); glDeleteVertexArrays(vao);
                glDeleteFramebuffers(fbo); glDeleteTextures(image); glDeleteTextures(target);
                GL.setCapabilities(null); glfwMakeContextCurrent(NULL); glfwDestroyWindow(window);
            }
            glfwTerminate(); // This test process only; never another process's owner window.
        }
    }

    private static byte[] cap() throws IOException {
        String fixture = System.getenv("RACMC_TEST_HUD_PANEL_RGBA");
        if (fixture != null && !fixture.isBlank()) {
            byte[] bytes = Files.readAllBytes(Path.of(fixture));
            assertEquals(32 * 32 * 4, bytes.length);
            System.out.println("Read-only private original-cap pixel fixture");
            return bytes;
        }
        byte[] bytes = new byte[32 * 32 * 4];
        for (int y = 1; y < 31; y++) for (int x = 0; x < 32; x++) {
            int at = (y * 32 + x) * 4;
            bytes[at] = bytes[at + 1] = bytes[at + 2] = 8;
            bytes[at + 3] = (byte) (x < 19 ? 0 : 192);
        }
        return bytes;
    }
    private static float[] render() {
        glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES, 0, 12);
        FloatBuffer pixels = BufferUtils.createFloatBuffer(128 * 64 * 4);
        glReadPixels(0, 0, 128, 64, GL_RGBA, GL_FLOAT, pixels);
        float[] result = new float[pixels.remaining()]; pixels.get(result); return result;
    }
    private static int visible(float[] pixels, int x) {
        int count = 0;
        for (int y = 0; y < 64; y++) if (pixels[(y * 128 + x) * 4 + 3] > .1F) count++;
        return count;
    }
    private static int program() throws IOException {
        int vertex = shader(GL_VERTEX_SHADER, vanilla("position_tex.vsh"));
        int fragment = shader(GL_FRAGMENT_SHADER, vanilla("position_tex.fsh"));
        int program = glCreateProgram();
        glAttachShader(program, vertex); glAttachShader(program, fragment);
        glBindAttribLocation(program, 0, "Position"); glBindAttribLocation(program, 1, "UV0");
        glLinkProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        return program;
    }
    private static int shader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }
    private static String vanilla(String file) throws IOException {
        Path jar = Path.of(System.getProperty("racmc.projectDir"), "build/moddev/artifacts",
                "neoforge-21.1.265-client-extra-aka-minecraft-resources.jar");
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("assets/minecraft/shaders/core/" + file);
            assertNotNull(entry);
            try (var stream = zip.getInputStream(entry)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
