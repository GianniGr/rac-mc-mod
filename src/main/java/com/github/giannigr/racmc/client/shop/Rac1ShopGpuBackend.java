package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/**
 * Shop-private GL2D backend. Every allocation is local and closeable; no shared texture manager,
 * RenderType, shader or global FX/HUD sampler mutation. Drawing restores all touched GL state.
 * No Minecraft rendering callbacks may run inside a backend draw/state scope.
 */
public final class Rac1ShopGpuBackend implements AutoCloseable {
    private final List<AutoCloseable> owned = new ArrayList<>();
    private long pixels;
    private int program, vao, buffer;
    private boolean closed;

    public Rac1ShopGpuBackend() {
        RenderSystem.assertOnRenderThread();
        try (State ignored = new State()) {
            int vertex = shader(GL_VERTEX_SHADER, """
                    #version 150
                    in vec2 Position; in vec2 UV; in vec4 Color;
                    uniform vec2 Resolution;
                    out vec2 texCoord; out vec4 tint;
                    void main() {
                        gl_Position=vec4(Position.x/Resolution.x*2.0-1.0,1.0-Position.y/Resolution.y*2.0,0.0,1.0);
                        texCoord=UV; tint=Color;
                    }
                    """);
            int fragment = 0;
            try {
                fragment = shader(GL_FRAGMENT_SHADER, """
                        #version 150
                        uniform sampler2D Image;
                        in vec2 texCoord; in vec4 tint;
                        out vec4 fragColor;
                        void main() { fragColor=texture(Image,texCoord)*tint; }
                        """);
                program = glCreateProgram();
                glAttachShader(program, vertex); glAttachShader(program, fragment);
                glBindAttribLocation(program, 0, "Position"); glBindAttribLocation(program, 1, "UV");
                glBindAttribLocation(program, 2, "Color"); glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                    throw new IllegalStateException("Shop shader link: " + glGetProgramInfoLog(program));
            } finally { glDeleteShader(vertex); if (fragment != 0) glDeleteShader(fragment); }
            vao = glGenVertexArrays(); buffer = glGenBuffers();
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, buffer);
            for (int i = 0; i < 3; i++) glEnableVertexAttribArray(i);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 32, 0L);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 32, 8L);
            glVertexAttribPointer(2, 4, GL_FLOAT, false, 32, 16L);
        } catch (RuntimeException | Error failure) { close(); throw failure; }
    }
    public final class Texture implements AutoCloseable {
        private final Rac1ShopGpuBackend owner = Rac1ShopGpuBackend.this;
        private int id;
        private final Sampler sampler;
        private final long area;
        private Texture(int id, Sampler sampler, long area) { this.id = id; this.sampler = sampler; this.area = area; }
        public int id() { check(); if (id == 0) throw new IllegalStateException("Closed shop texture"); return id; }
        public Sampler sampler() { return sampler; }
        @Override public void close() {
            RenderSystem.assertOnRenderThread();
            if (id != 0) { glDeleteTextures(id); id = 0; pixels -= area; }
        }
    }
    public final class Target implements AutoCloseable {
        private final Rac1ShopGpuBackend owner = Rac1ShopGpuBackend.this;
        private int framebuffer, color, depth;
        private final int width, height;
        private Target(int width, int height) { this.width = width; this.height = height; }
        public int framebuffer() { check(); if (framebuffer == 0) throw new IllegalStateException("Closed shop target"); return framebuffer; }
        public int color() { check(); if (color == 0) throw new IllegalStateException("Closed shop target"); return color; }
        public int width() { return width; }
        public int height() { return height; }
        @Override public void close() {
            RenderSystem.assertOnRenderThread();
            if (framebuffer != 0) {
                glDeleteFramebuffers(framebuffer); glDeleteTextures(color); glDeleteRenderbuffers(depth);
                framebuffer = color = depth = 0; pixels -= (long) width * height;
            }
        }
    }
    /** Upload original decoded pixels only. Sampler variants own distinct GL objects. */
    public Texture upload(Rac1Texture source, Sampler sampler) {
        check(); Objects.requireNonNull(sampler);
        long area = budget(source.width(), source.height());
        if (source.pixels().length != area * 4) throw new IllegalArgumentException("Invalid imported shop texture pixels");
        int id = 0;
        try (State ignored = new State()) {
            id = glGenTextures(); glBindTexture(GL_TEXTURE_2D, id);
            ByteBuffer bytes = BufferUtils.createByteBuffer((int)area * 4);
            for (int y = 0; y < source.height(); y++) for (int x = 0; x < source.width(); x++) {
                int argb = source.pixel(x,y);
                bytes.put((byte)(argb >>> 16)).put((byte)(argb >>> 8)).put((byte)argb).put((byte)(argb >>> 24));
            }
            bytes.flip(); glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glPixelStorei(GL_UNPACK_ROW_LENGTH,0); glPixelStorei(GL_UNPACK_SKIP_ROWS,0); glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, source.width(), source.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            if (glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH) != source.width())
                throw new IllegalStateException("Original shop texture allocation failed");
            sampling(sampler);
            Texture texture = new Texture(id, sampler, area);
            owned.add(texture); pixels += area;
            return texture;
        } catch (RuntimeException | Error failure) { if (id != 0) glDeleteTextures(id); throw failure; }
    }
    /** Original monitor scratch extent is512x128. Other bounded extents support resize tests/scene targets. */
    public Target target(int width, int height) {
        check(); long area = budget(width, height);
        Target target = new Target(width, height);
        try (State ignored = new State()) {
            target.color = glGenTextures(); glBindTexture(GL_TEXTURE_2D, target.color);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
            sampling(new Sampler(Filter.POINT, Address.CLAMP));
            target.depth = glGenRenderbuffers(); glBindRenderbuffer(GL_RENDERBUFFER, target.depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
            target.framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target.color, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, target.depth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete original shop scratch target");
            owned.add(target); pixels += area; return target;
        } catch (RuntimeException | Error failure) {
            if (target.framebuffer != 0) glDeleteFramebuffers(target.framebuffer);
            if (target.color != 0) glDeleteTextures(target.color);
            if (target.depth != 0) glDeleteRenderbuffers(target.depth);
            throw failure;
        }
    }
    public void clear(Target target, float r, float g, float b, float a) {
        check(); values(r,g,b,a);
        if (target.owner != this) throw new IllegalArgumentException("Foreign shop target generation");
        try (State ignored = new State()) {
            glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
            glDisable(GL_SCISSOR_TEST); glColorMask(true,true,true,true); glDepthMask(true);
            glDisable(GL_DITHER); glDisable(GL_FRAMEBUFFER_SRGB);
            glClearColor(r,g,b,a); glClearDepth(1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
    }
    /** Four strip vertices, each x,y,u,v,r,g,b,a. Geometry/UVs come from original layout/content. */
    public void draw(Target target, Texture texture, Pass material, int fixed, float[] vertices) {
        check();
        if (target.owner != this || texture.owner != this || vertices.length != 32 || fixed < 0 || fixed > 128
                || !texture.sampler.equals(material.sampler()))
            throw new IllegalArgumentException("Invalid shop geometry/material/sampler");
        values(vertices);
        try (State ignored = new State()) {
            glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
            glViewport(0,0,target.width,target.height);
            glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST);
            glDisable(GL_DITHER); glDisable(GL_FRAMEBUFFER_SRGB);
            glColorMask(true,true,true,true); glDepthMask(false); glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendColor(0,0,0,fixed / 128f);
            int source = material.blend() == Blend.SOURCE_ALPHA ? GL_SRC_ALPHA : GL_CONSTANT_ALPHA;
            int destination = material.blend() == Blend.FIXED_ADD ? GL_ONE
                    : material.blend() == Blend.FIXED_MIX ? GL_ONE_MINUS_CONSTANT_ALPHA : GL_ONE_MINUS_SRC_ALPHA;
            glBlendFuncSeparate(source, destination, GL_ONE, GL_ZERO);
            glUseProgram(program);
            glUniform2f(glGetUniformLocation(program,"Resolution"),target.width,target.height);
            glUniform1i(glGetUniformLocation(program,"Image"),0); glBindSampler(0,0);
            glBindTexture(GL_TEXTURE_2D,texture.id());
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glBufferData(GL_ARRAY_BUFFER,vertices,GL_STREAM_DRAW); glDrawArrays(GL_TRIANGLE_STRIP,0,4);
        }
    }
    private long budget(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096 || owned.size() >= 128
                || pixels + (long)width * height > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Shop GPU resource budget exceeded");
        return (long)width * height;
    }
    private static void sampling(Sampler sampler) {
        int filter = sampler.filter() == Filter.POINT ? GL_NEAREST : GL_LINEAR;
        int address = sampler.address() == Address.REPEAT ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,address); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,address);
    }
    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader,source); glCompileShader(shader);
            if (glGetShaderi(shader,GL_COMPILE_STATUS) == GL_FALSE)
                throw new IllegalStateException("Shop shader compile: " + glGetShaderInfoLog(shader));
            return shader;
        } catch (RuntimeException | Error failure) { glDeleteShader(shader); throw failure; }
    }
    private static void values(float... values) {
        for (float v : values) if (!Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite shop GPU input");
    }
    private void check() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Closed shop GPU backend");
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (int i = owned.size()-1; i >= 0; i--) {
            try { owned.get(i).close(); }
            catch (Exception next) {
                if (failure == null) failure = new IllegalStateException("Original shop GPU disposal failed",next);
                else failure.addSuppressed(next);
            }
        }
        owned.clear();
        if (buffer != 0) glDeleteBuffers(buffer);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        buffer = vao = program = 0;
        if (failure != null) throw failure;
    }
    /** Direct GL is confined to this scope; restoring state also leaves Minecraft's cached state unchanged. */
    private static final class State implements AutoCloseable {
        final int active = glGetInteger(GL_ACTIVE_TEXTURE);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING), renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        final int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB), equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final int unpack = glGetInteger(GL_UNPACK_ALIGNMENT);
        final int unpackRow = glGetInteger(GL_UNPACK_ROW_LENGTH), unpackRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        final int unpackPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS), unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] viewport = new int[4];
        final float[] blendColor = new float[4], clearColor = new float[4];
        final ByteBuffer mask = BufferUtils.createByteBuffer(4);
        final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        final double clearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
        final int[] flags = {GL_BLEND,GL_CULL_FACE,GL_DEPTH_TEST,GL_SCISSOR_TEST,GL_DITHER,GL_FRAMEBUFFER_SRGB};
        final boolean[] enabled = new boolean[flags.length];
        final int texture, sampler;
        State() {
            for (int i = 0; i < flags.length; i++) enabled[i] = glIsEnabled(flags[i]);
            glGetIntegerv(GL_VIEWPORT,viewport); glGetFloatv(GL_BLEND_COLOR,blendColor); glGetFloatv(GL_COLOR_CLEAR_VALUE,clearColor);
            glGetBooleanv(GL_COLOR_WRITEMASK,mask);
            glActiveTexture(GL_TEXTURE0); texture = glGetInteger(GL_TEXTURE_BINDING_2D);
            sampler = glGetInteger(GL_SAMPLER_BINDING);
        }
        @Override public void close() {
            glBindTexture(GL_TEXTURE_2D,texture); glBindSampler(0,sampler); glActiveTexture(active);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
            glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer); glUseProgram(program);
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            glBlendEquationSeparate(equationRgb,equationAlpha); glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            glBlendColor(blendColor[0],blendColor[1],blendColor[2],blendColor[3]);
            glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]); glClearDepth(clearDepth);
            glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0); glDepthMask(depthMask);
            glPixelStorei(GL_UNPACK_ALIGNMENT,unpack);
            glPixelStorei(GL_UNPACK_ROW_LENGTH,unpackRow); glPixelStorei(GL_UNPACK_SKIP_ROWS,unpackRows);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS,unpackPixels); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpackBuffer);
            for (int i = 0; i < flags.length; i++) if (enabled[i]) glEnable(flags[i]); else glDisable(flags[i]);
        }
    }
}
