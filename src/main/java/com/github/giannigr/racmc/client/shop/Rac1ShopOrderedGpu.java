package com.github.giannigr.racmc.client.shop;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.opengl.GL33C.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/**
 * C1-A prototype only: owned integer color, shared logical GS Z24 and ordered destination reads.
 * No Minecraft callback, global renderer, scene/input bridge or context creation.
 * Coordinates/UVs are explicit; logical surfaces share the arena's TOP-LEFT Z region.
 */
public final class Rac1ShopOrderedGpu implements AutoCloseable {
    public record Vertex(float x,float y,float z,float w,float s,float t,
                         float red,float green,float blue,float alpha,float fog) {
        public Vertex {
            for (float v:new float[]{x,y,z,w,s,t,red,green,blue,alpha,fog})
                if (!Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite C1 vertex");
            if (w<=0 || Math.abs(s)>16 || Math.abs(t)>16 || red<0 || red>255 || green<0 || green>255
                    || blue<0 || blue>255 || alpha<0 || alpha>255 || fog<0 || fog>255)
                throw new IllegalArgumentException("Unsupported C1 vertex domain");
        }
    }
    /** All fields explicit, including fixture-only ALWAYS tests and fog disable. GS masks are AABBGGRR. */
    public record State(int blend,int fixed,int alphaReference,boolean alphaAlways,
                        boolean depthAlways,boolean zMasked,int frameMask,
                        boolean fogEnabled,int fogRed,int fogGreen,int fogBlue) {
        public State {
            if ((blend!=0x44 && blend!=0x64 && blend!=0x68) || fixed<0 || fixed>128
                    || alphaReference<0 || alphaReference>255 || (fogRed|fogGreen|fogBlue)<0
                    || fogRed>255 || fogGreen>255 || fogBlue>255)
                throw new IllegalArgumentException("Unsupported C1 GS state");
        }
    }
    public record Readback(int width,int height,byte[] rgba,int[] z24) {
        public Readback { rgba=rgba.clone(); z24=z24.clone(); }
        @Override public byte[] rgba() { return rgba.clone(); }
        @Override public int[] z24() { return z24.clone(); }
    }
    private final Thread thread=Thread.currentThread();
    private final GLCapabilities capabilities=GL.getCapabilities();
    private final int width,height;
    private final List<Surface> surfaces=new ArrayList<>();
    private final List<Image> images=new ArrayList<>();
    private final int[] depth=new int[2], depthFbo=new int[2];
    private int currentDepth,program,vao,buffer,mrt;
    private int opaqueProgram,depthProgram,hardwareDepth;
    // These three owned programs are linked once and never relinked during this object's life.
    private final Map<Integer,Map<String,Integer>> uniformLocations=new HashMap<>();
    private final boolean cacheUniformLocations;
    private final boolean scopedBindings;
    private Surface boundSurface;
    private State boundState;
    private int boundImage,boundColor,boundDepth,bindingSetups;
    private int batchDepth;
    private final int clipDistances=glGetInteger(GL_MAX_CLIP_DISTANCES);
    private boolean closed,depthInitialized;
    private long texels;
    private long triangles;

    public Rac1ShopOrderedGpu(int width,int height) {
        this(width,height,true);
    }
    /** Package-local reference seam for paired GPU fixtures, never a client setting. */
    Rac1ShopOrderedGpu(int width,int height,boolean cacheUniformLocations) {
        this(width,height,cacheUniformLocations,true);
    }
    /** Exact dc32542 binding path remains available to paired fixtures. */
    Rac1ShopOrderedGpu(int width,int height,boolean cacheUniformLocations,boolean scopedBindings) {
        this.cacheUniformLocations=cacheUniformLocations;
        this.scopedBindings=scopedBindings;
        if (!capabilities.OpenGL33 || width<1 || height<1 || width>1024 || height>1024)
            throw new IllegalArgumentException("C1 requires bounded GL3.3 arena");
        this.width=width; this.height=height;
        try (Scope ignored=new Scope()) {
            program=program();
            opaqueProgram=opaqueProgram();
            depthProgram=depthProgram();
            hardwareDepth=glGenRenderbuffers(); glBindRenderbuffer(GL_RENDERBUFFER,hardwareDepth);
            glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,width,height);
            vao=glGenVertexArrays(); buffer=glGenBuffers();
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            int[] sizes={4,2,4,1}; int offset=0;
            for (int i=0;i<sizes.length;i++) {
                glEnableVertexAttribArray(i);
                glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,44,offset);
                offset+=sizes[i]*4;
            }
            for (int i=0;i<2;i++) {
                depth[i]=integerTexture(GL_R32UI,GL_RED_INTEGER);
                depthFbo[i]=framebuffer(depth[i]);
            }
            texels=3L*width*height;
            mrt=glGenFramebuffers();
        } catch (RuntimeException | Error failure) { close(); throw failure; }
    }
    public final class Surface implements AutoCloseable {
        private final Rac1ShopOrderedGpu owner=Rac1ShopOrderedGpu.this;
        private final int logicalWidth,logicalHeight;
        private final int[] color=new int[2], fbo=new int[2], coverage=new int[2], coverageFbo=new int[2];
        private int current;
        private boolean initialized,disposed,overlaySupported=true;
        private Surface(int w,int h) { logicalWidth=w; logicalHeight=h; }
        public int width() { return logicalWidth; }
        public int height() { return logicalHeight; }
        public int colorName() { live(); return color[current]; }
        private void live() { check(); if (disposed) throw new IllegalStateException("Closed C1 surface"); }
        @Override public void close() {
            context();
            invalidateBindings();
            if (disposed) return;
            disposed=true;
            // Do not keep a deleted surface's storage alive through the shared MRT attachment.
            if (mrt!=0) try (Scope ignored=new Scope()) {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,mrt);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,0,0);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,0,0);
            }
            for (int i=0;i<2;i++) {
                if (fbo[i]!=0) glDeleteFramebuffers(fbo[i]); if (color[i]!=0) glDeleteTextures(color[i]);
                if (coverageFbo[i]!=0) glDeleteFramebuffers(coverageFbo[i]);
                if (coverage[i]!=0) glDeleteTextures(coverage[i]);
            }
            if (surfaces.remove(this)) texels-=4L*width*height;
        }
    }
    public final class Image implements AutoCloseable {
        private final Rac1ShopOrderedGpu owner=Rac1ShopOrderedGpu.this;
        private int id;
        private final int area;
        private final boolean opaque;
        private Image(int id,int area,boolean opaque) { this.id=id; this.area=area; this.opaque=opaque; }
        public int name() { check(); if (id==0) throw new IllegalStateException("Closed C1 source image"); return id; }
        public boolean opaque() { name(); return opaque; }
        @Override public void close() {
            context();
            invalidateBindings();
            if (id==0) return;
            glDeleteTextures(id); id=0;
            if (images.remove(this)) texels-=area;
        }
    }
    public Surface surface(int w,int h) {
        check();
        invalidateBindings();
        if (w<1 || h<1 || w>width || h>height || surfaces.size()>=8
                || texels+4L*width*height>16*1024*1024)
            throw new IllegalArgumentException("C1 surface budget/extent");
        Surface out=new Surface(w,h);
        try (Scope ignored=new Scope()) {
            for (int i=0;i<2;i++) {
                out.color[i]=integerTexture(GL_RGBA8UI,GL_RGBA_INTEGER); out.fbo[i]=framebuffer(out.color[i]);
                out.coverage[i]=integerTexture(GL_R8UI,GL_RED_INTEGER); out.coverageFbo[i]=framebuffer(out.coverage[i]);
            }
            surfaces.add(out); texels+=4L*width*height; return out;
        } catch (RuntimeException | Error failure) { out.close(); throw failure; }
    }
    /** Original RGBA bytes, unexpanded GS alpha. LINEAR is the approved C1-A filter approximation. */
    public Image image(int w,int h,byte[] rgba,Sampler sampler) {
        check();
        invalidateBindings();
        if (sampler==null || sampler.filter()==null || sampler.address()==null)
            throw new IllegalArgumentException("Explicit C1 filter/address required");
        long area=(long)w*h;
        if (w<1 || h<1 || w>1024 || h>1024 || rgba.length!=area*4 || images.size()>=32
                || texels+area>16*1024*1024)
            throw new IllegalArgumentException("C1 image budget/extent");
        int id=0;
        try (Scope ignored=new Scope()) {
            id=glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,id);
            unpack();
            ByteBuffer bytes=BufferUtils.createByteBuffer(rgba.length).put(rgba); bytes.flip();
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
            int filter=sampler.filter()==Filter.LINEAR ? GL_LINEAR:GL_NEAREST;
            int wrap=sampler.address()==Address.REPEAT ? GL_REPEAT:GL_CLAMP_TO_EDGE;
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,wrap);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,wrap);
            boolean opaque=true;
            for (int p=3;p<rgba.length;p+=4) if ((rgba[p]&255)!=128) { opaque=false; break; }
            Image result=new Image(id,(int)area,opaque); images.add(result); texels+=area;
            return result;
        } catch (RuntimeException | Error failure) { if (id!=0) glDeleteTextures(id); throw failure; }
    }
    /** Explicit initial logical Z; surfaces must be explicitly cleared separately. */
    public void beginFrame(int z) {
        check();
        if (batchDepth!=0) throw new IllegalStateException("Cannot restart a frame inside a draw list");
        triangles=0;
        orderedDraws=0; opaqueDraws=0;
        bindingSetups=0;
        clearDepth(z);
    }
    public void clearDepth(int z) {
        check(); depth(z);
        invalidateBindings();
        try (Scope ignored=new Scope()) {
            defaults(); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,depthFbo[currentDepth]);
            glClearBufferuiv(GL_COLOR,0,new int[]{z,0,0,0});
            depthInitialized=true;
        }
    }
    /** A target clear aliases its TOP-LEFT logical region in the shared scene Z, when requested. */
    public void clear(Surface surface,Rac1ShopGsPixelProgram.Rgba rgba,boolean clearZ,int z) {
        clear(surface,rgba,clearZ,z,Math.min(255,rgba.a()*255/128));
    }
    /** Independent Minecraft overlay coverage; never infer background from an RGB value. */
    public void clear(Surface surface,Rac1ShopGsPixelProgram.Rgba rgba,boolean clearZ,int z,int coverage) {
        own(surface); Objects.requireNonNull(rgba); depth(z);
        invalidateBindings();
        if (coverage<0 || coverage>255) throw new IllegalArgumentException("Invalid overlay coverage");
        if (clearZ && !depthInitialized) throw new IllegalStateException("Initialize entire shared Z first");
        try (Scope ignored=new Scope()) {
            defaults();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,surface.fbo[surface.current]);
            glClearBufferuiv(GL_COLOR,0,new int[]{rgba.r(),rgba.g(),rgba.b(),rgba.a()});
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,surface.coverageFbo[surface.current]);
            glClearBufferuiv(GL_COLOR,0,new int[]{coverage,0,0,0});
            surface.initialized=true;
            surface.overlaySupported=true;
            if (clearZ) {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,depthFbo[currentDepth]);
                glEnable(GL_SCISSOR_TEST); glScissor(0,height-surface.logicalHeight,surface.logicalWidth,surface.logicalHeight);
                glClearBufferuiv(GL_COLOR,0,new int[]{z,0,0,0});
            }
        }
    }
    /** Exactly one source primitive; null image is the original -1 neutral-MODULATE identity, not fake art. */
    public void triangle(Surface surface,Image image,State state,Vertex a,Vertex b,Vertex c) {
        if (image!=null && image.owner!=this) throw new IllegalArgumentException("Foreign C1 source image");
        draw(surface,image,null,state,a,b,c);
    }
    public void copyTriangle(Surface destination,Surface source,State state,Vertex a,Vertex b,Vertex c) {
        own(source);
        if (destination==source || !source.initialized) throw new IllegalArgumentException("C1 feedback/uninitialized source");
        draw(destination,null,source,state,a,b,c);
    }
    /** One state-restore boundary for a shop-only draw list; no arbitrary GL/Minecraft callbacks inside. */
    public void orderedBatch(Runnable drawList) {
        check(); Objects.requireNonNull(drawList);
        try (Scope ignored=batchDepth==0 ? new Scope():null) {
            if (batchDepth==0) invalidateBindings();
            batchDepth++;
            try { drawList.run(); }
            catch (RuntimeException | Error failure) { invalidateBindings(); throw failure; }
            finally { if (--batchDepth==0) invalidateBindings(); }
        }
    }
    private boolean textureBarrier;
    /** Exact same-pixel read/modify/write only; GL3.3/reference callers retain ping-pong copies. */
    public boolean enableTextureBarrier() {
        check();
        invalidateBindings();
        textureBarrier=capabilities.OpenGL45 || capabilities.GL_ARB_texture_barrier;
        return textureBarrier;
    }
    /**
     * Exact opaque subset of the ordered program. No destination color read is needed
     * at source alpha128. Initialize hardware depth from the shared logical Z before
     * each contiguous run, quantize output to the same Z24, and update both in place.
     * Filtered/cutout/translucent textures and all masks/additive paths stay ordered.
     */
    public void opaqueTriangles(Surface surface,Image image,State state,List<Vertex> vertices) {
        own(surface);
        invalidateBindings(); // The opaque path mutates owned MRT attachments, not just bindings.
        if (!surface.initialized || !depthInitialized) throw new IllegalStateException("Initialize opaque target");
        if (image!=null && (image.owner!=this || !image.opaque()))
            throw new IllegalArgumentException("Opaque source required");
        if (state.blend()!=0x44 || state.frameMask()!=0 || state.zMasked() || state.depthAlways()
                || state.alphaReference()>128 || vertices.size()%3!=0 || vertices.size()>60000
                || vertices.stream().anyMatch(v -> v.alpha()!=128))
            throw new IllegalArgumentException("Outside exact opaque subset");
        if (vertices.isEmpty()) return;
        opaqueDraws++;
        triangles+=vertices.size()/3;
        if (triangles>200000) throw new IllegalStateException("Bounded prototype draw budget exhausted");
        try (Scope ignored=new Scope()) {
            defaults(); glEnable(GL_DEPTH_TEST); glDepthMask(true); glDepthFunc(GL_ALWAYS);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,mrt);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,surface.color[surface.current],0);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,0,0);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,0,0);
            glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,hardwareDepth);
            glDrawBuffers(new int[]{GL_NONE});
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete opaque C1 target");
            glViewport(0,height-surface.logicalHeight,surface.logicalWidth,surface.logicalHeight);
            glBindVertexArray(vao);
            glUseProgram(depthProgram); bind(2,depth[currentDepth]);
            glUniform1i(location(depthProgram,"LogicalZ"),2);
            glDrawArrays(GL_TRIANGLES,0,3);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,depth[currentDepth],0);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,surface.coverage[surface.current],0);
            glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2});
            glDepthFunc(GL_GEQUAL); glUseProgram(opaqueProgram); bind(0,image==null ? 0:image.name());
            glUniform1i(location(opaqueProgram,"Image"),0);
            glUniform1i(location(opaqueProgram,"Textured"),image==null ? 0:1);
            glUniform1i(location(opaqueProgram,"FogEnabled"),state.fogEnabled() ? 1:0);
            glUniform3f(location(opaqueProgram,"FogColor"),state.fogRed(),state.fogGreen(),state.fogBlue());
            glBindBuffer(GL_ARRAY_BUFFER,buffer);
            float[] data=new float[vertices.size()*11]; int at=0;
            for (var v:vertices) for (float value:new float[]{v.x,v.y,v.z,v.w,v.s,v.t,v.red,v.green,v.blue,v.alpha,v.fog})
                data[at++]=value;
            glBufferData(GL_ARRAY_BUFFER,data,GL_STREAM_DRAW);
            glDrawArrays(GL_TRIANGLES,0,vertices.size());
            glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,0);
        }
    }
    private void draw(Surface surface,Image image,Surface source,State state,Vertex a,Vertex b,Vertex c) {
        orderedDraws++;
        own(surface); Objects.requireNonNull(state); Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(c);
        if (!surface.initialized || !depthInitialized) throw new IllegalStateException("Explicit C1 color/Z initialization required");
        if (state.blend()==0x68 || (state.frameMask()&0xffffff)!=0 || source!=null && !source.overlaySupported)
            surface.overlaySupported=false;
        int imageName=image==null ? 0:image.name();
        if (++triangles>200000) throw new IllegalStateException("Bounded prototype draw budget exhausted; create a new owned frame");
        try (Scope ignored=batchDepth==0 ? new Scope():null) {
            // ARB_texture_barrier permits a single same-texel fetch/write per attachment
            // between barriers. One triangle per draw, no MSAA, no filtered destination
            // lookup; framebuffer-copy primitives deliberately retain the reference path.
            boolean feedback=textureBarrier && source==null;
            int nextColor=feedback ? surface.current:surface.current^1,nextDepth=feedback ? currentDepth:currentDepth^1;
            boolean reuse=scopedBindings && batchDepth>0 && feedback && boundSurface==surface
                    && boundImage==imageName && boundColor==nextColor && boundDepth==nextDepth && state.equals(boundState);
            if (!reuse) { invalidateBindings(); defaults(); }
            if (feedback) {
                if (capabilities.OpenGL45) org.lwjgl.opengl.GL45C.glTextureBarrier();
                else org.lwjgl.opengl.ARBTextureBarrier.glTextureBarrier();
            } else {
                blit(surface.fbo[surface.current],surface.fbo[nextColor]);
                blit(surface.coverageFbo[surface.current],surface.coverageFbo[nextColor]);
                blit(depthFbo[currentDepth],depthFbo[nextDepth]);
            }
            if (!reuse) {
                bindingSetups++;
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,mrt);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,surface.color[nextColor],0);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,depth[nextDepth],0);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,surface.coverage[nextColor],0);
                glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2});
                if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Incomplete C1 integer MRT");
                glViewport(0,height-surface.logicalHeight,surface.logicalWidth,surface.logicalHeight);
                glUseProgram(program);
                bind(0,imageName); bind(1,surface.color[surface.current]); bind(2,depth[currentDepth]);
                bind(3,source==null ? 0:source.color[source.current]);
                bind(4,surface.coverage[surface.current]); bind(5,source==null ? 0:source.coverage[source.current]);
                uniform("Image",0); uniform("PreviousColor",1); uniform("PreviousZ",2); uniform("SourceTarget",3);
                uniform("PreviousCoverage",4); uniform("SourceCoverage",5);
                uniform("TextureMode",source!=null ? 2:image!=null ? 1:0);
                glUniform2i(location(program,"SourceExtent"),source==null ? 1:source.logicalWidth,source==null ? 1:source.logicalHeight);
                uniform("ArenaHeight",height); uniform("Blend",state.blend); uniform("Fixed",state.fixed);
                uniform("AlphaReference",state.alphaReference); uniform("AlphaAlways",state.alphaAlways ? 1:0);
                uniform("DepthAlways",state.depthAlways ? 1:0); uniform("ZMasked",state.zMasked ? 1:0);
                glUniform1ui(location(program,"FrameMask"),state.frameMask);
                uniform("FogEnabled",state.fogEnabled ? 1:0);
                glUniform3f(location(program,"FogColor"),state.fogRed,state.fogGreen,state.fogBlue);
                glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            }
            float[] vertices=new float[33]; int at=0;
            for (Vertex v:new Vertex[]{a,b,c}) for (float f:new float[]{v.x,v.y,v.z,v.w,v.s,v.t,v.red,v.green,v.blue,v.alpha,v.fog})
                vertices[at++]=f;
            glBufferData(GL_ARRAY_BUFFER,vertices,GL_STREAM_DRAW); glDrawArrays(GL_TRIANGLES,0,3);
            surface.current=nextColor; currentDepth=nextDepth;
            if (scopedBindings && batchDepth>0 && feedback) {
                boundSurface=surface; boundState=state; boundImage=imageName; boundColor=nextColor; boundDepth=nextDepth;
            } else invalidateBindings();
        }
    }
    public Readback read(Surface surface) {
        return read(surface,false);
    }
    /** Straight-alpha Minecraft overlay from separately accumulated primitive coverage. */
    public Readback readOverlay(Surface surface) {
        return read(surface,true);
    }
    private Readback read(Surface surface,boolean overlay) {
        own(surface);
        invalidateBindings();
        if (!surface.initialized || !depthInitialized) throw new IllegalStateException("Uninitialized C1 readback");
        if (overlay && !surface.overlaySupported)
            throw new IllegalStateException("Overlay coverage currently requires unmasked source-over/copy; additive needs a separate composition path");
        try (Scope ignored=new Scope()) {
            pack();
            int w=surface.logicalWidth,h=surface.logicalHeight;
            ByteBuffer rgba=BufferUtils.createByteBuffer(w*h*4);
            IntBuffer z=BufferUtils.createIntBuffer(w*h);
            IntBuffer coverage=overlay ? BufferUtils.createIntBuffer(w*h):null;
            glBindFramebuffer(GL_READ_FRAMEBUFFER,surface.fbo[surface.current]);
            long readProfile=Rac1ShopFrameProfile.begin();
            glReadPixels(0,height-h,w,h,GL_RGBA_INTEGER,GL_UNSIGNED_BYTE,rgba);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,depthFbo[currentDepth]);
            glReadPixels(0,height-h,w,h,GL_RED_INTEGER,GL_UNSIGNED_INT,z);
            if (overlay) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER,surface.coverageFbo[surface.current]);
                glReadPixels(0,height-h,w,h,GL_RED_INTEGER,GL_UNSIGNED_INT,coverage);
            }
            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.READBACK_GL,readProfile);
            long packProfile=Rac1ShopFrameProfile.begin();
            byte[] out=new byte[w*h*4]; int[] depths=new int[w*h];
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int src=(h-1-y)*w+x,dst=y*w+x;
                for (int c=0;c<4;c++) out[dst*4+c]=rgba.get(src*4+c);
                if (overlay) {
                    int alpha=coverage.get(src);
                    for (int c=0;c<3;c++) out[dst*4+c]=(byte)(alpha==0 ? 0:
                            Math.min(255,((rgba.get(src*4+c)&255)*255+alpha/2)/alpha));
                    out[dst*4+3]=(byte)alpha;
                }
                depths[dst]=z.get(src);
            }
            var result=new Readback(w,h,out,depths);
            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.READBACK_PACK,packProfile);
            return result;
        }
    }
    private void blit(int from,int to) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER,from); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,to);
        glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
    }
    private int orderedDraws,opaqueDraws;
    record DrawCounts(int ordered,int opaque,long triangles,int bindingSetups) {}
    DrawCounts drawCounts() { return new DrawCounts(orderedDraws,opaqueDraws,triangles,bindingSetups); }
    public String frameDrawCounts() { return "ordered="+orderedDraws+", opaqueRuns="+opaqueDraws+", triangles="+triangles+", bindings="+bindingSetups; }
    private void invalidateBindings() { boundSurface=null; boundState=null; }
    private int integerTexture(int format,int external) {
        int id=glGenTextures();
        try {
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,id); unpack();
            glTexImage2D(GL_TEXTURE_2D,0,format,width,height,0,external,GL_UNSIGNED_INT,(ByteBuffer)null);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            return id;
        } catch (RuntimeException | Error failure) { glDeleteTextures(id); throw failure; }
    }
    private int framebuffer(int color) {
        int id=glGenFramebuffers();
        try {
            glBindFramebuffer(GL_FRAMEBUFFER,id);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0); glReadBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete C1 owned attachment");
            return id;
        } catch (RuntimeException | Error failure) { glDeleteFramebuffers(id); throw failure; }
    }
    private static void bind(int unit,int image) {
        glActiveTexture(GL_TEXTURE0+unit); glBindSampler(unit,0); glBindTexture(GL_TEXTURE_2D,image);
    }
    private int location(int linkedProgram,String name) {
        if (!cacheUniformLocations) return glGetUniformLocation(linkedProgram,name);
        var locations=uniformLocations.computeIfAbsent(linkedProgram,ignored -> new HashMap<>());
        var found=locations.get(name);
        if (found!=null) return found; // -1 is a valid cached "inactive uniform" result.
        int location=glGetUniformLocation(linkedProgram,name);
        locations.put(name,location);
        return location;
    }
    private void uniform(String name,int value) { glUniform1i(location(program,name),value); }
    private void own(Surface surface) {
        check();
        if (surface==null || surface.owner!=this) throw new IllegalArgumentException("Foreign C1 surface");
        surface.live();
    }
    private void context() {
        if (Thread.currentThread()!=thread || GL.getCapabilities()!=capabilities)
            throw new IllegalStateException("C1 context/thread ownership mismatch");
    }
    private void check() { context(); if (closed) throw new IllegalStateException("Closed C1 prototype"); }
    private static void depth(int z) { if (z<0 || z>0xffffff) throw new IllegalArgumentException("Expected unsigned GS Z24"); }
    @Override public void close() {
        context(); if (closed) return;
        for (var surface:List.copyOf(surfaces)) surface.close();
        for (var image:List.copyOf(images)) image.close();
        for (int i=0;i<2;i++) { if (depthFbo[i]!=0) glDeleteFramebuffers(depthFbo[i]); if (depth[i]!=0) glDeleteTextures(depth[i]); }
        if (mrt!=0) glDeleteFramebuffers(mrt);
        if (buffer!=0) glDeleteBuffers(buffer); if (vao!=0) glDeleteVertexArrays(vao); if (program!=0) glDeleteProgram(program);
        if (opaqueProgram!=0) glDeleteProgram(opaqueProgram); if (depthProgram!=0) glDeleteProgram(depthProgram);
        if (hardwareDepth!=0) glDeleteRenderbuffers(hardwareDepth);
        closed=true;
    }
    private static void unpack() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0); glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_FALSE);
        glPixelStorei(GL_UNPACK_ROW_LENGTH,0); glPixelStorei(GL_UNPACK_SKIP_ROWS,0); glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
    }
    private static void pack() {
        glBindBuffer(GL_PIXEL_PACK_BUFFER,0); glPixelStorei(GL_PACK_ALIGNMENT,1);
        glPixelStorei(GL_PACK_SWAP_BYTES,GL_FALSE);
        glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_ROWS,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0);
    }
    private static final int[] FLAGS={GL_BLEND,GL_DEPTH_TEST,GL_CULL_FACE,GL_SCISSOR_TEST,GL_DITHER,
            GL_FRAMEBUFFER_SRGB,GL_RASTERIZER_DISCARD,GL_STENCIL_TEST,GL_COLOR_LOGIC_OP,GL_MULTISAMPLE,
            GL_SAMPLE_ALPHA_TO_COVERAGE,GL_SAMPLE_COVERAGE,GL_SAMPLE_MASK,GL_PRIMITIVE_RESTART,GL_POLYGON_OFFSET_FILL,GL_DEPTH_CLAMP};
    private void defaults() {
        for (int flag:FLAGS) glDisable(flag);
        for (int i=0;i<clipDistances;i++) glDisable(GL_CLIP_DISTANCE0+i);
        glPolygonMode(GL_FRONT_AND_BACK,GL_FILL); glDepthRange(0,1);
        for (int i=0;i<3;i++) glColorMaski(i,true,true,true,true);
    }
    /** Restore touched state; never invoke a Minecraft callback inside this scope. */
    private static final class Scope implements AutoCloseable {
        final int active=glGetInteger(GL_ACTIVE_TEXTURE),program=glGetInteger(GL_CURRENT_PROGRAM);
        final int vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),buffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        final int depthFunction=glGetInteger(GL_DEPTH_FUNC),renderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING);
        final boolean depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int[] viewport=new int[4],scissor=new int[4],polygon=new int[2],textures=new int[6],samplers=new int[6];
        final double[] range=new double[2];
        final boolean[] flags=new boolean[FLAGS.length],clips=new boolean[glGetInteger(GL_MAX_CLIP_DISTANCES)];
        final ByteBuffer[] masks={BufferUtils.createByteBuffer(4),BufferUtils.createByteBuffer(4),BufferUtils.createByteBuffer(4)};
        final int[] pixelEnums={GL_PACK_ALIGNMENT,GL_PACK_ROW_LENGTH,GL_PACK_SKIP_ROWS,GL_PACK_SKIP_PIXELS,
                GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_PIXELS,
                GL_PACK_SWAP_BYTES,GL_UNPACK_SWAP_BYTES};
        final int[] pixels=new int[pixelEnums.length];
        Scope() {
            glGetIntegerv(GL_VIEWPORT,viewport); glGetIntegerv(GL_SCISSOR_BOX,scissor);
            glGetIntegerv(GL_POLYGON_MODE,polygon); glGetDoublev(GL_DEPTH_RANGE,range);
            for (int i=0;i<FLAGS.length;i++) flags[i]=glIsEnabled(FLAGS[i]);
            for (int i=0;i<clips.length;i++) clips[i]=glIsEnabled(GL_CLIP_DISTANCE0+i);
            for (int i=0;i<3;i++) glGetBooleani_v(GL_COLOR_WRITEMASK,i,masks[i]);
            for (int i=0;i<pixels.length;i++) pixels[i]=glGetInteger(pixelEnums[i]);
            for (int i=0;i<6;i++) { glActiveTexture(GL_TEXTURE0+i); textures[i]=glGetInteger(GL_TEXTURE_BINDING_2D); samplers[i]=glGetInteger(GL_SAMPLER_BINDING); }
        }
        @Override public void close() {
            for (int i=0;i<6;i++) { glActiveTexture(GL_TEXTURE0+i); glBindTexture(GL_TEXTURE_2D,textures[i]); glBindSampler(i,samplers[i]); }
            glActiveTexture(active); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
            glDepthFunc(depthFunction); glDepthMask(depthMask); glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer);
            glUseProgram(program); glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glBindBuffer(GL_PIXEL_PACK_BUFFER,pack); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);
            for (int i=0;i<pixels.length;i++) glPixelStorei(pixelEnums[i],pixels[i]);
            for (int i=0;i<FLAGS.length;i++) { if (flags[i]) glEnable(FLAGS[i]); else glDisable(FLAGS[i]); }
            for (int i=0;i<clips.length;i++) { if (clips[i]) glEnable(GL_CLIP_DISTANCE0+i); else glDisable(GL_CLIP_DISTANCE0+i); }
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]); glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
            glPolygonMode(GL_FRONT_AND_BACK,polygon[0]); glDepthRange(range[0],range[1]);
            for (int i=0;i<3;i++) { ByteBuffer m=masks[i]; glColorMaski(i,m.get(0)!=0,m.get(1)!=0,m.get(2)!=0,m.get(3)!=0); }
        }
    }
    private static int program() {
        int vs=shader(GL_VERTEX_SHADER,"""
                #version 330 core
                layout(location=0) in vec4 Position;
                layout(location=1) in vec2 UV;
                layout(location=2) in vec4 Color;
                layout(location=3) in float Fog;
                out vec2 st; noperspective out vec4 rgba; noperspective out float fog;
                void main() { gl_Position=Position; st=UV; rgba=Color; fog=Fog; }
                """);
        int fs=0,p=0;
        try {
            fs=shader(GL_FRAGMENT_SHADER,"""
                    #version 330 core
                    uniform sampler2D Image;
                    uniform usampler2D PreviousColor,PreviousZ,SourceTarget;
                    uniform usampler2D PreviousCoverage,SourceCoverage;
                    uniform int TextureMode,ArenaHeight,Blend,Fixed,AlphaReference,AlphaAlways,DepthAlways,ZMasked,FogEnabled;
                    uniform ivec2 SourceExtent;
                    uniform uint FrameMask;
                    uniform vec3 FogColor;
                    in vec2 st; noperspective in vec4 rgba; noperspective in float fog;
                    layout(location=0) out uvec4 ColorOut;
                    layout(location=1) out uint ZOut;
                    layout(location=2) out uint CoverageOut;
                    void main() {
                        ivec2 xy=ivec2(gl_FragCoord.xy);
                        uvec4 dst=texelFetch(PreviousColor,xy,0);
                        uint dz=texelFetch(PreviousZ,xy,0).r;
                        uint oldCoverage=texelFetch(PreviousCoverage,xy,0).r;
                        uint sourceCoverage=255u;
                        uint z=uint(clamp(floor(gl_FragCoord.z*16777216.0),0.0,16777215.0));
                        uvec4 src=uvec4(clamp(floor(rgba),0.0,255.0));
                        if (TextureMode!=0) {
                            uvec4 texel;
                            if (TextureMode==1) texel=uvec4(floor(texture(Image,st)*255.0+0.5));
                            else {
                                ivec2 q=clamp(ivec2(floor(st*vec2(SourceExtent))),ivec2(0),SourceExtent-1);
                                 texel=texelFetch(SourceTarget,ivec2(q.x,ArenaHeight-1-q.y),0);
                                 sourceCoverage=texelFetch(SourceCoverage,ivec2(q.x,ArenaHeight-1-q.y),0).r;
                            }
                            src=min(uvec4(255),(src*texel)>>7u);
                        }
                        if (FogEnabled!=0) src.rgb=uvec3(clamp(floor(mix(FogColor,vec3(src.rgb),clamp(floor(fog),0.0,255.0)/255.0)),0.0,255.0));
                        ColorOut=dst; ZOut=dz; CoverageOut=oldCoverage;
                        if (DepthAlways==0 && z<dz) return;
                        bool pass=AlphaAlways!=0 || src.a>=uint(AlphaReference);
                        ivec3 rgb;
                        if (Blend==104) rgb=ivec3(dst.rgb)+((ivec3(src.rgb)*Fixed)>>7);
                        else {
                            int factor=Blend==100 ? Fixed:int(src.a);
                            rgb=ivec3(dst.rgb)+(((ivec3(src.rgb)-ivec3(dst.rgb))*factor)>>7);
                        }
                        uvec4 next=uvec4(clamp(rgb,ivec3(0),ivec3(255)),pass ? src.a:dst.a);
                        uvec4 masks=(uvec4(FrameMask)>>uvec4(0,8,16,24))&255u;
                        ColorOut=(next&~masks)|(dst&masks);
                        // Overlay-only plane. GS framebuffer alpha is NOT coverage.
                        // Copies interpolate the source plane; ordinary primitives use
                        // their source alpha. The original integer color/Z result is unchanged.
                        uint factor=uint(Blend==100 || Blend==104 ? Fixed:min(128,int(src.a)));
                        uint contribution=TextureMode==2 ? sourceCoverage:255u;
                        if (Blend==104)
                            CoverageOut=min(255u,oldCoverage+(contribution*factor+64u)/128u);
                        else CoverageOut=(oldCoverage*(128u-factor)+contribution*factor+64u)/128u;
                        if (pass && ZMasked==0) ZOut=z;
                    }
                    """);
            p=glCreateProgram(); glAttachShader(p,vs); glAttachShader(p,fs); glLinkProgram(p);
            if (glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(p));
            return p;
        } catch (RuntimeException | Error failure) { if (p!=0) glDeleteProgram(p); throw failure; }
        finally { glDeleteShader(vs); if (fs!=0) glDeleteShader(fs); }
    }
    private static int shader(int type,String source) {
        int id=glCreateShader(type);
        try {
            glShaderSource(id,source); glCompileShader(id);
            if (glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(id));
            return id;
        } catch (RuntimeException | Error failure) { glDeleteShader(id); throw failure; }
    }
    private static int link(String vertex,String fragment) {
        int vs=shader(GL_VERTEX_SHADER,vertex),fs=0,p=0;
        try {
            fs=shader(GL_FRAGMENT_SHADER,fragment); p=glCreateProgram();
            glAttachShader(p,vs); glAttachShader(p,fs); glLinkProgram(p);
            if (glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(p));
            return p;
        } catch (RuntimeException | Error failure) { if (p!=0) glDeleteProgram(p); throw failure; }
        finally { glDeleteShader(vs); if (fs!=0) glDeleteShader(fs); }
    }
    private static int depthProgram() {
        return link("""
                #version 330 core
                void main() {
                    vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
                    gl_Position=vec4(p*2.0-1.0,0,1);
                }
                ""","""
                #version 330 core
                uniform usampler2D LogicalZ;
                void main() { gl_FragDepth=float(texelFetch(LogicalZ,ivec2(gl_FragCoord.xy),0).r)/16777215.0; }
                """);
    }
    private static int opaqueProgram() {
        return link("""
                #version 330 core
                layout(location=0) in vec4 Position;
                layout(location=1) in vec2 UV;
                layout(location=2) in vec4 Color;
                layout(location=3) in float Fog;
                out vec2 st; noperspective out vec4 rgba; noperspective out float fog;
                void main() { gl_Position=Position; st=UV; rgba=Color; fog=Fog; }
                ""","""
                #version 330 core
                uniform sampler2D Image;
                uniform int Textured,FogEnabled;
                uniform vec3 FogColor;
                in vec2 st; noperspective in vec4 rgba; noperspective in float fog;
                layout(location=0) out uvec4 ColorOut;
                layout(location=1) out uint ZOut;
                layout(location=2) out uint CoverageOut;
                void main() {
                    uvec4 src=uvec4(clamp(floor(rgba),0.0,255.0));
                    if (Textured!=0) src=min(uvec4(255),(src*uvec4(floor(texture(Image,st)*255.0+0.5)))>>7u);
                    if (FogEnabled!=0) src.rgb=uvec3(clamp(floor(mix(FogColor,vec3(src.rgb),clamp(floor(fog),0.0,255.0)/255.0)),0.0,255.0));
                    uint z=uint(clamp(floor(gl_FragCoord.z*16777216.0),0.0,16777215.0));
                    gl_FragDepth=float(z)/16777215.0;
                    ColorOut=src; ZOut=z; CoverageOut=255u;
                }
                """);
    }
}
