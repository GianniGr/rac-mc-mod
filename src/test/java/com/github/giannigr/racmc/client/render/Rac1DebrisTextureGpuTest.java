package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** One hidden/unfocused GPU sampler, not a Minecraft client or scene/engine-lightmap test.
 * Reads a permitted converted cache only. Exports no images or asset bytes. */
class Rac1DebrisTextureGpuTest {
    @Test void cachedMaterialPatchesActuallySampleOriginalRgbAndRespectLightMultiplier() throws Exception {
        String path = System.getenv("RACMC_TEST_DEBRIS_CACHE");
        Assumptions.assumeTrue("1".equals(System.getenv("RACMC_TEST_SHADER_GPU")) && path != null,
                "Opt in with RACMC_TEST_SHADER_GPU=1 and RACMC_TEST_DEBRIS_CACHE");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        String key = new String(bytes, Rac1CacheCodec.HEADER_SIZE + header.getInt(28),
                header.getInt(16), StandardCharsets.UTF_8);
        var cache = Rac1CacheCodec.read(bytes, key, Rac1CacheStore.SCHEMA_VERSION);
        var meshes = new LinkedHashMap<String, Rac1DebrisMesh>();
        for (var definition : Rac1Crates.definitions()) {
            var crate = cache.crate(definition.key());
            meshes.put(definition.key(), new Rac1DebrisMesh(crate.mesh().positions(),
                    crate.mesh().uvs(), crate.mesh().indices(), crate.mesh().materials(), crate.textures()));
        }
        var body = cache.vendor().body();
        meshes.put("gadgetron_vendor", new Rac1DebrisMesh(Rac1VendorPose.closed(cache.vendor()).positions(),
                body.mesh().uvs(), body.mesh().indices(), body.mesh().materials(), body.textures()));
        assertTrue(glfwInit());
        long window = NULL;
        int program = 0, vao = 0, fbo = 0, target = 0, image = 0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1, 1, "Private imported block debris sampler", NULL, NULL);
            assertNotEquals(NULL, window);
            glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("Private debris sampler GPU: " + glGetString(GL_RENDERER));
            int vs = shader(GL_VERTEX_SHADER, """
                    #version 330 core
                    void main() {
                        vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
                        gl_Position=vec4(p*2.0-1.0,0,1);
                    }
                    """);
            int fs = shader(GL_FRAGMENT_SHADER, """
                    #version 330 core
                    uniform sampler2D material;
                    uniform vec2 uv;
                    uniform float light;
                    out vec4 color;
                    void main() { color=texture(material,uv)*vec4(vec3(0.6*light),1); }
                    """);
            program = glCreateProgram(); glAttachShader(program, vs); glAttachShader(program, fs);
            glLinkProgram(program); glDeleteShader(vs); glDeleteShader(fs);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            vao = glGenVertexArrays(); glBindVertexArray(vao);
            target = glGenTextures(); glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, (FloatBuffer)null);
            fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            image = glGenTextures(); glBindTexture(GL_TEXTURE_2D, image);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glViewport(0,0,1,1); glDisable(GL_BLEND); glDisable(GL_DITHER);
            int samples = 0;
            for (var entry : meshes.entrySet()) {
                int chromatic = 0;
                var materials = new HashSet<Integer>();
                for (int i = 0; i < 24; i++) {
                    var patch = entry.getValue().sample(null, (i + .5) / 24);
                    assertNotNull(patch, entry.getKey());
                    var texture = entry.getValue().texture(patch.material());
                    materials.add(patch.material());
                    ByteBuffer pixels = BufferUtils.createByteBuffer(texture.width() * texture.height() * 4);
                    for (int y = 0; y < texture.height(); y++) for (int x = 0; x < texture.width(); x++) {
                        int argb = texture.pixel(x, y);
                        pixels.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >> 24));
                    }
                    pixels.flip();
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture.width(), texture.height(), 0,
                            GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    float u = (patch.u0() + patch.u1()) / 2, v = (patch.v0() + patch.v1()) / 2;
                    float[] expected = linear(texture, u, v);
                    if (Math.abs(expected[0] - expected[2]) > .02 || Math.abs(expected[0] - expected[1]) > .02)
                        chromatic++;
                    glUniform2f(glGetUniformLocation(program, "uv"), u, v);
                    for (float light : new float[]{1, .25f}) {
                        glUniform1f(glGetUniformLocation(program, "light"), light);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                        FloatBuffer readback = BufferUtils.createFloatBuffer(4);
                        glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,readback);
                        for (int c = 0; c < 3; c++)
                            assertEquals(expected[c] * .6f * light, readback.get(c), .004,
                                    entry.getKey() + " material " + patch.material() + " RGB channel " + c);
                        assertEquals(expected[3], readback.get(3), .004);
                        samples++;
                    }
                }
                assertTrue(chromatic > 0, entry.getKey() + " must sample non-placeholder colored material");
                System.out.println(entry.getKey() + ": 24 imported UV patches, materials=" + materials
                        + ", non-gray patches=" + chromatic + "; light1/.25 RGB/alpha readback passed");
            }
            assertEquals(240, samples);
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (window != NULL) {
                glDeleteProgram(program); glDeleteVertexArrays(vao); glDeleteFramebuffers(fbo);
                glDeleteTextures(target); glDeleteTextures(image);
                GL.setCapabilities(null); glfwMakeContextCurrent(NULL); glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }

    private static int shader(int type, String source) {
        int id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id);
        assertEquals(GL_TRUE, glGetShaderi(id, GL_COMPILE_STATUS), glGetShaderInfoLog(id));
        return id;
    }

    private static float[] linear(Rac1Texture texture, float u, float v) {
        double x = u * texture.width() - .5, y = v * texture.height() - .5;
        int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
        double fx = x - x0, fy = y - y0;
        float[] result = new float[4];
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) {
            int argb = texture.pixel(Math.floorMod(x0 + dx, texture.width()),
                    Math.floorMod(y0 + dy, texture.height()));
            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
            int[] channels = {(argb >>> 16)&255, (argb >>> 8)&255, argb&255, (argb >>> 24)&255};
            for (int c = 0; c < 4; c++) result[c] += (float)(channels[c] / 255.0 * weight);
        }
        return result;
    }
}
