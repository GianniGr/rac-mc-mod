package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.*;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Optional hidden-context imported-orb/path raster smoke test, NOT Minecraft or PS2 fidelity certification. */
class Rac1NanotechGpuTest {
    @Test void originalOrbRasterizesFreeAndFanThenRetiresAllEight() throws Exception {
        String iso = System.getProperty("racmc.isoImage", "");
        Assumptions.assumeTrue(!iso.isBlank() && "1".equals(System.getenv("RACMC_TEST_SHADER_GPU")));
        try (var disc = new Rac1DiscValidator().inspect(Path.of(iso))) {
            assertTrue(disc.isSupported());
            String sourceHash = disc.image().revision() == com.github.giannigr.racmc.iso.Rac1DiscRevision.NTSC_U_V1_00
                    ? "aee9275384f739c9b24cd5de6f7e200292db7b01" : "ef304c641c7cd426ba492707dda5974679befbc0";
            var banks = Rac1EffectBankImporter.read(Rac1LevelAssets.load(disc.image(), 1), sourceHash);
            var texture = banks.particles().decode(39);
            var tables = Rac1NanotechImporter.read(disc.image());
            var base = new EffectSignal.Point(0, 0, 0);
            var camera = new EffectSignal.Point(0, 2, 10);
            assertEquals(3, Rac1NanotechVisuals.geometry(tables, base, camera, 0, 42).size());
            assertTrue(glfwInit());
            long window = 0;
            int program = 0, vao = 0, vbo = 0, tex = 0;
            try {
                glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
                glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3); glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                window = glfwCreateWindow(256, 256, "isolated nanotech paths", 0, 0);
                assertNotEquals(0, window); glfwMakeContextCurrent(window); GL.createCapabilities();
                System.out.println("Nanotech isolated GPU: " + glGetString(GL_RENDERER));
                int v = shader(GL_VERTEX_SHADER, "#version 330 core\nlayout(location=0) in vec2 p;layout(location=1) in vec2 uv;out vec2 t;void main(){gl_Position=vec4(p,0,1);t=uv;}");
                int f = shader(GL_FRAGMENT_SHADER, "#version 330 core\nin vec2 t;uniform sampler2D orb;out vec4 c;void main(){c=texture(orb,t);}");
                program = glCreateProgram(); glAttachShader(program, v); glAttachShader(program, f);
                glLinkProgram(program); glDeleteShader(v); glDeleteShader(f);
                assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS));
                glUseProgram(program); glUniform1i(glGetUniformLocation(program, "orb"), 0);
                vao = glGenVertexArrays(); glBindVertexArray(vao); vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
                glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
                tex = glGenTextures(); glBindTexture(GL_TEXTURE_2D, tex);
                var pixels = BufferUtils.createByteBuffer(texture.width() * texture.height() * 4);
                for (int argb : texture.colours()) pixels.put((byte)(argb >>> 16)).put((byte)(argb >>> 8))
                        .put((byte)argb).put((byte)(argb >>> 24));
                pixels.flip();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture.width(), texture.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glViewport(0, 0, 256, 256); glDisable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE);
                for (double age : new double[]{-1, 10, 81}) {
                    var points = age < 0 ? Rac1NanotechVisuals.freeOrbs(base, 0, 42)
                            : Rac1NanotechVisuals.attracting(base, new EffectSignal.Point(2, 1, 0), age, 42);
                    var vertices = BufferUtils.createFloatBuffer(8 * 6 * 4);
                    int count = 0;
                    for (var p : points) {
                        if (p == null) continue;
                        assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
                        for (float[] corner : new float[][]{{-1,-1,0,0},{1,-1,1,0},{1,1,1,1},{-1,-1,0,0},{1,1,1,1},{-1,1,0,1}})
                            vertices.put((float)p.x() / 8 + corner[0] * .04f).put((float)p.y() / 8 + corner[1] * .04f)
                                    .put(corner[2]).put(corner[3]);
                        count++;
                    }
                    glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
                    vertices.flip(); glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW); glDrawArrays(GL_TRIANGLES, 0, count * 6);
                    var result = BufferUtils.createByteBuffer(256 * 256 * 4);
                    glReadPixels(0, 0, 256, 256, GL_RGBA, GL_UNSIGNED_BYTE, result);
                    long energy = 0;
                    for (int i = 0; i < result.limit(); i += 4) energy += (result.get(i)&255) + (result.get(i+1)&255) + (result.get(i+2)&255);
                    if (age > 80) { assertEquals(0, count); assertEquals(0, energy); }
                    else { assertEquals(8, count); assertTrue(energy > 0); }
                    System.out.println("Nanotech age=" + age + " visible=" + count + " RGB-energy=" + energy);
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                if (window != 0) {
                    glDeleteTextures(tex); glDeleteBuffers(vbo); glDeleteVertexArrays(vao); glDeleteProgram(program);
                    GL.setCapabilities(null); glfwMakeContextCurrent(0); glfwDestroyWindow(window);
                }
                glfwTerminate();
            }
        }
    }
    private static int shader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }
}
