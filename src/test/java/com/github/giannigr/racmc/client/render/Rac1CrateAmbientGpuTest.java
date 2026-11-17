package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Opt-in, isolated GPU sampler: a never-shown, unfocused 1x1 context, not a Minecraft client. */
class Rac1CrateAmbientGpuTest {
    private static final float[] TEXTURE = {0.4F, 0.3F, 0.2F, 1.0F};
    private static final float[] WORLD_LIGHT = {0.8F, 0.7F, 0.6F, 1.0F};

    @Test
    void everyFaceFlashesWithoutChangingUnsaturatedSidesTexturesOrWorldLight() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHADER_GPU")),
                "Opt in with RACMC_TEST_SHADER_GPU=1 on a desktop with OpenGL 3.2");
        assertTrue(glfwInit(), "Isolated GLFW initialization");
        long window = NULL;
        int normal = 0, flash = 0, vao = 0, vbo = 0, fbo = 0;
        int[] textures = new int[4];
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1, 1, "RaC isolated material sampler", NULL, NULL);
            assertNotEquals(NULL, window, "Never-shown GL context");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Isolated GPU: " + glGetString(GL_RENDERER));
            normal = program(vanilla("core/rendertype_entity_cutout_no_cull.vsh"));
            try (var stream = getClass().getResourceAsStream(
                    "/assets/racmc/shaders/core/rac1_crate_ambient.vsh")) {
                assertNotNull(stream);
                flash = program(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            vao = glGenVertexArrays();
            glBindVertexArray(vao);
            vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, new float[]{-1, -1, 0, 3, -1, 0, -1, 3, 0}, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L);
            glVertexAttrib4f(1, 1, 1, 1, 1); // Untinted vertex color.
            glVertexAttrib2f(2, 0.5F, 0.5F);
            glVertexAttribI2i(3, 0, 0); // Overlay coordinate.
            glVertexAttribI2i(4, 0, 0); // World-light coordinate.
            textures[3] = texture(new float[4]);
            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[3], 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            textures[0] = texture(TEXTURE);
            textures[1] = texture(new float[]{0, 0, 0, 1}); // No overlay.
            textures[2] = texture(WORLD_LIGHT);
            glViewport(0, 0, 1, 1);
            glDisable(GL_DITHER);
            for (int unit = 0; unit < 3; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, textures[unit]);
            }
            for (int lighting = 0; lighting < 4; lighting++) {
                float level = lighting % 2 == 0 ? 1.0F : 0.25F;
                float[] worldLight = {level, level, level, 1};
                float secondLightY = lighting < 2 ? 1 : -1;
                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, textures[2]);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT, worldLight);
                float previousAngledGreen = -1;
                for (float[] face : new float[][]{
                        {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                        {(float) Math.sqrt(0.84), 0.4F, 0},
                        {(float) Math.sqrt(0.75), 0.5F, 0},
                        {(float) (0.2 / Math.sqrt(0.53)), 0, (float) (-0.7 / Math.sqrt(0.53))}}) {
                    glVertexAttrib3f(5, face[0], face[1], face[2]);
                    float[] off = sample(normal, secondLightY);
                    float[] on = sample(flash, secondLightY);
                    double diffuse = diffuse(face, secondLightY);
                    double sideBudget = 0.6 * Math.sqrt(0.53 / 1.53);
                    double visibleDiffuse = Math.min(0.6, diffuse);
                    visibleDiffuse -= 0.75 * Math.max(0, visibleDiffuse - sideBudget);
                    boolean changed = false;
                    double differenceSquared = 0, baselineSquared = 0;
                    for (int c = 0; c < 3; c++) {
                        double ambient = (c == 0 ? 200.0 : 64.0) / 255.0;
                        double factor = Math.min(1, ambient + visibleDiffuse);
                        assertEquals(TEXTURE[c] * worldLight[c] * factor, on[c], 1e-5,
                                "Ambient + monotonic diffuse adaptation, channel " + c);
                        assertEquals(TEXTURE[c] * worldLight[c] * Math.min(1, 0.4 + diffuse),
                                off[c], 1e-5, "Unchanged normal lighting");
                        if (diffuse <= sideBudget + 1e-6) {
                            assertEquals(TEXTURE[c] * worldLight[c] * Math.min(1, ambient + diffuse),
                                    on[c], 1e-5, "Accepted side/unlit-bottom appearance unchanged");
                        }
                        changed |= Math.abs(on[c] - off[c]) > 1e-4;
                        differenceSquared += (on[c] - off[c]) * (on[c] - off[c]);
                        baselineSquared += off[c] * off[c];
                    }
                    assertTrue(changed, "Visible flash for normal " + java.util.Arrays.toString(face));
                    assertArrayEquals(off, sample(normal, secondLightY), 1e-6F, "Exact off-phase restoration");
                    double greenDrop = (off[1] - on[1]) / off[1];
                    double rgbContrast = Math.sqrt(differenceSquared / baselineSquared);
                    if (Math.abs(face[1]) == 1) {
                        assertTrue(greenDrop >= (lighting < 2 ? 0.30 : 0.25),
                                "Quantified horizontal-phase contrast floor");
                        assertTrue(rgbContrast >= 0.20, "Horizontal RGB contrast signal");
                    } else if (face[1] > 0) {
                        assertTrue(on[1] > previousAngledGreen, "Preserve monotonic bevel shading");
                        previousAngledGreen = on[1];
                    }
                    System.out.printf(java.util.Locale.ROOT,
                            "lighting=%d world=%.2f normal=%s diffuse=%.6f greenDrop=%.4f rgbContrast=%.4f off=%s flash=%s%n",
                            lighting, level, java.util.Arrays.toString(face), diffuse,
                            greenDrop, rgbContrast,
                            java.util.Arrays.toString(off), java.util.Arrays.toString(on));
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (window != NULL) {
                glDeleteProgram(normal);
                glDeleteProgram(flash);
                glDeleteBuffers(vbo);
                glDeleteVertexArrays(vao);
                glDeleteFramebuffers(fbo);
                glDeleteTextures(textures);
                GL.setCapabilities(null);
                glfwMakeContextCurrent(NULL);
                glfwDestroyWindow(window); // Only this test's hidden window.
            }
            glfwTerminate(); // Process-local GLFW; never touches another process's client.
        }
    }

    private static double diffuse(float[] n, float secondLightY) {
        double length = Math.sqrt(1.53);
        return 0.6 * (Math.max(0, (0.2 * n[0] + n[1] - 0.7 * n[2]) / length)
                + Math.max(0, (-0.2 * n[0] + secondLightY * n[1] + 0.7 * n[2]) / length));
    }

    private static int texture(float[] rgba) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, rgba);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return id;
    }

    private static float[] sample(int program, float secondLightY) {
        glUseProgram(program);
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, identity);
        glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
        float length = (float) Math.sqrt(1.53);
        glUniform3f(glGetUniformLocation(program, "Light0_Direction"), 0.2F / length, 1 / length, -0.7F / length);
        glUniform3f(glGetUniformLocation(program, "Light1_Direction"),
                -0.2F / length, secondLightY / length, 0.7F / length);
        glUniform1f(glGetUniformLocation(program, "FogStart"), 100);
        glUniform1f(glGetUniformLocation(program, "FogEnd"), 200);
        glUniform4f(glGetUniformLocation(program, "FogColor"), 0, 0, 0, 0);
        glUniform1i(glGetUniformLocation(program, "FogShape"), 0);
        for (int i = 0; i < 3; i++) glUniform1i(glGetUniformLocation(program, "Sampler" + i), i);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        FloatBuffer pixels = BufferUtils.createFloatBuffer(4);
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, pixels);
        float[] result = new float[4];
        pixels.get(result);
        return result;
    }

    private static int program(String vertex) throws IOException {
        int v = shader(GL_VERTEX_SHADER, vertex);
        int f = shader(GL_FRAGMENT_SHADER, vanilla("core/rendertype_entity_cutout_no_cull.fsh"));
        int program = glCreateProgram();
        glAttachShader(program, v);
        glAttachShader(program, f);
        String[] attributes = {"Position", "Color", "UV0", "UV1", "UV2", "Normal"};
        for (int i = 0; i < attributes.length; i++) glBindAttribLocation(program, i, attributes[i]);
        glLinkProgram(program);
        glDeleteShader(v);
        glDeleteShader(f);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        return program;
    }

    private static int shader(int stage, String source) throws IOException {
        for (String include : new String[]{"light.glsl", "fog.glsl"}) {
            source = source.replace("#moj_import <" + include + ">",
                    vanilla("include/" + include).replaceAll("(?m)^#version[^\\r\\n]*", ""));
        }
        int shader = glCreateShader(stage);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static String vanilla(String relative) throws IOException {
        Path jar = Path.of(System.getProperty("racmc.projectDir"), "build", "moddev", "artifacts",
                "neoforge-21.1.265-client-extra-aka-minecraft-resources.jar");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("assets/minecraft/shaders/" + relative);
            assertNotNull(entry, relative);
            try (var stream = zip.getInputStream(entry)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
