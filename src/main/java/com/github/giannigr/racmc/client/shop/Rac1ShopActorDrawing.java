package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import java.util.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

/** Original actor identities/poses and per-offer transforms; shop-local source-GPU integration. */
public final class Rac1ShopActorDrawing {
    private static final float[] IDENTITY={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    private static final float[] ROTATION_ID={1,0,0,0,1,0,0,0,1};
    private Rac1ShopActorDrawing() {}
    public static int initial(Rac1ShopPreviewSource.Actor actor) {
        return Arrays.stream(actor.animated().animations()).anyMatch(a -> a.name().equals("sequence_1")) ? 1:0;
    }
    public static Rac1ShopNormalGeometry.Posed pose(Rac1ShopPreviewSource.Actor actor,int sequence,float time) {
        float[] skin=IDENTITY;
        if (actor.animated()!=null) {
            var a=actor.animated();
            var clip=Arrays.stream(a.animations()).filter(c -> c.name().equals("sequence_"+sequence)).findFirst().orElseThrow();
            skin=actor.normal().originalPalette(Rac1AnimationEvaluator.evaluate(a.skeleton(),clip,time,1));
        }
        return actor.normal().pose(actor.id(),skin);
    }
    public static void draw(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopPreviewSource source,
                            Rac1ShopPreviewSource.Actor actor,int sequence,float time,float[] rotation,float[] position,boolean panel) {
        var images=images(gpu,actor);
        try { prepared(gpu,target,source,actor,sequence,time,rotation,position,panel,images); }
        finally { for (var image:images) image.close(); }
    }
    private static List<Rac1ShopOrderedGpu.Image> images(Rac1ShopOrderedGpu gpu,Rac1ShopPreviewSource.Actor actor) {
        var images=new ArrayList<Rac1ShopOrderedGpu.Image>();
        try {
            for (var p:actor.textures()) images.add(gpu.image(p.width(),p.height(),p.rgba(),new Sampler(Filter.LINEAR,Address.REPEAT)));
            return images;
        } catch (RuntimeException | Error failure) { for (var image:images) image.close(); throw failure; }
    }
    private static void prepared(Rac1ShopOrderedGpu gpu,Rac1ShopOrderedGpu.Surface target,Rac1ShopPreviewSource source,
                                 Rac1ShopPreviewSource.Actor actor,int sequence,float time,float[] rotation,float[] position,
                                 boolean panel,List<Rac1ShopOrderedGpu.Image> images) {
        long profileStart=System.nanoTime();
        long poseProfile=Rac1ShopFrameProfile.begin();
        var mesh=pose(actor,sequence,time);
        Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.ACTOR_POSE,poseProfile);
        long projectProfile=Rac1ShopFrameProfile.begin();
        long posed=System.nanoTime();
        var triangles=mesh.triangles().stream().filter(t -> !degenerate(mesh,t)).toList();
        var used=new BitSet(mesh.vertices().size());
        for (var t:triangles) { used.set(t.a()); used.set(t.b()); used.set(t.c()); }
            float[] dirs=new float[9],colors=new float[12];
            System.arraycopy(Rac1ShopLightingProgram.toLight(ROTATION_ID,Arrays.copyOf(source.light().menuDirection(),3)),0,dirs,0,3);
            System.arraycopy(source.light().menuColor(),0,colors,0,4);
            int sourceHeight=source.pal() ? 448:416;
            float tanY=.63f*(source.pal() ? .756f:.775f);
            var fog=source.light().worldFog();
            var state=new Rac1ShopOrderedGpu.State(0x44,128,96,false,false,false,0,!panel,fog.red(),fog.green(),fog.blue());
            var vertices=new ArrayList<Rac1ShopOrderedGpu.Vertex>();
            for (int index=0;index<mesh.vertices().size();index++) {
                if (!used.get(index)) { vertices.add(null); continue; } // decoded transfer/cache vertices need not be drawn
                var v=mesh.vertices().get(index);
                float[] p=rotate(rotation,v.x(),v.y(),v.z()),n=rotate(rotation,v.nx(),v.ny(),v.nz());
                float x=p[0]+position[0],y=p[1]+position[1],z=p[2]+position[2],d=3.8f-x;
                if (d<=0) throw new IllegalArgumentException("Actor behind source cut camera");
                int[] c=Rac1ShopLightingProgram.normal(n[0],n[1],n[2],dirs,colors,new int[]{32,32,32},128,
                        new int[]{v.red(),v.green(),v.blue(),v.alpha()});
                double near=32,far=panel ? 524288:745472;
                double zgs=8388096-8388080*((far+near)/(far-near)-2*far*near/((far-near)*d*1024));
                // Preserve the selected scene's pixel focal lengths in the cropped RTT.
                // This is the current source compositor projection, not a fitted actor bounding box.
                vertices.add(new Rac1ShopOrderedGpu.Vertex(y/.63f,(z-1.5f)/tanY*sourceHeight/target.height(),
                        (float)((zgs/16777216*2-1)*d),d,v.s(),v.t(),c[0],c[1],c[2],c[3],
                        Rac1ShopLightingProgram.fogByte(d*1024,fog)));
            }
            long projected=System.nanoTime();
            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.ACTOR_PROJECT,projectProfile);
            long submitProfile=Rac1ShopFrameProfile.begin();
            gpu.orderedBatch(() -> {
                List<Rac1ShopOrderedGpu.Vertex> run=new ArrayList<>();
                Rac1ShopOrderedGpu.Image material=null;
                int selector=Integer.MIN_VALUE;
                for (var t:triangles) {
                    var image=t.selector()==-1 ? null:images.get(t.selector());
                    var a=vertices.get(t.a()); var b=vertices.get(t.b()); var c=vertices.get(t.c());
                    boolean opaque=(image==null || image.opaque()) && a.alpha()==128 && b.alpha()==128 && c.alpha()==128;
                    if (!run.isEmpty() && (!opaque || t.selector()!=selector || run.size()==60000)) {
                        gpu.opaqueTriangles(target,material,state,run); run.clear();
                    }
                    if (opaque) {
                        selector=t.selector(); material=image;
                        run.add(a); run.add(b); run.add(c);
                    } else gpu.triangle(target,image,state,a,b,c);
                }
                if (!run.isEmpty()) gpu.opaqueTriangles(target,material,state,run);
            });
            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.ACTOR_SUBMIT,submitProfile);
            if (Boolean.getBoolean("racmc.shopProfile"))
                System.out.println("Actor stages ms pose="+(posed-profileStart)/1e6+" project="+(projected-posed)/1e6
                        +" submit="+(System.nanoTime()-projected)/1e6+" "+gpu.frameDrawCounts());
    }
    static boolean degenerate(Rac1ShopNormalGeometry.Posed mesh,Rac1ShopNormalGeometry.Triangle t) {
        var a=mesh.vertices().get(t.a()); var b=mesh.vertices().get(t.b()); var c=mesh.vertices().get(t.c());
        double x=b.x()-a.x(),y=b.y()-a.y(),z=b.z()-a.z(),u=c.x()-a.x(),v=c.y()-a.y(),w=c.z()-a.z();
        return y*w-z*v==0 && z*u-x*w==0 && x*v-y*u==0;
    }
    /** Euler XYZ in original Z-up units, independent of player grip/muzzle/item transforms. */
    public static float[] rotate(float[] rotation,float x,float y,float z) {
        double sx=Math.sin(rotation[0]),cx=Math.cos(rotation[0]),sy=Math.sin(rotation[1]),cy=Math.cos(rotation[1]);
        double sz=Math.sin(rotation[2]),cz=Math.cos(rotation[2]);
        double a=y*cx-z*sx,b=y*sx+z*cx,c=x*cy+b*sy,d=-x*sy+b*cy;
        return new float[]{(float)(c*cz-a*sz),(float)(c*sz+a*cz),(float)d};
    }
    public static Rac1ShopOrderedGpu.Readback presenter(Rac1ShopPreviewSource source,long frames) {
        try (var target=new PresenterTarget(source)) { return target.frame(frames); }
    }
    /** Screen-owned persistent programs, targets and textures; never recompile/reupload each animation tick. */
    public static final class PresenterTarget implements AutoCloseable {
        private final Rac1ShopPreviewSource source;
        private final Rac1ShopOrderedGpu gpu;
        private final Rac1ShopOrderedGpu.Surface target;
        private final List<Rac1ShopOrderedGpu.Image> images;
        private final boolean accelerated;
        public PresenterTarget(Rac1ShopPreviewSource source) {
            this(source,true);
        }
        public PresenterTarget(Rac1ShopPreviewSource source,boolean accelerated) {
            this(source,accelerated,true);
        }
        PresenterTarget(Rac1ShopPreviewSource source,boolean accelerated,boolean cacheUniforms) {
            this(source,accelerated,cacheUniforms,true);
        }
        PresenterTarget(Rac1ShopPreviewSource source,boolean accelerated,boolean cacheUniforms,boolean scopedBindings) {
            this.source=Objects.requireNonNull(source); gpu=new Rac1ShopOrderedGpu(512,128,cacheUniforms,scopedBindings);
            this.accelerated=accelerated && gpu.enableTextureBarrier();
            try { target=gpu.surface(512,128); images=images(gpu,source.presenter()); }
            catch (RuntimeException | Error failure) { gpu.close(); throw failure; }
        }
        public boolean usesTextureBarrier() { return accelerated; }
        Rac1ShopOrderedGpu.DrawCounts drawCounts() { return gpu.drawCounts(); }
        public Rac1ShopOrderedGpu.Readback frame(long frames) {
            if (frames<0) throw new IllegalArgumentException("Negative presenter clock");
            int sequence=new int[]{2,3,0}[(int)(frames/600%3)];
            var actor=source.presenter();
            var clip=Arrays.stream(actor.animated().animations()).filter(a -> a.name().equals("sequence_"+sequence)).findFirst().orElseThrow();
            float duration=clip.times()[clip.keyCount()-1];
            float time=duration>0 ? (frames%600/60f)%duration:0;
            return frame(sequence,time);
        }
        /** Same renderer/material/order path; explicit pose from the provisional private presenter clock. */
        public Rac1ShopOrderedGpu.Readback frame(int sequence,float time) {
            if (!Float.isFinite(time) || time<0) throw new IllegalArgumentException("Invalid presenter pose time");
            gpu.beginFrame(0); gpu.clear(target,new Rac1ShopGsPixelProgram.Rgba(0,0,0,0),false,0,255);
            prepared(gpu,target,source,source.presenter(),sequence,time,new float[3],source.presentation().presenterPosition(),true,images);
            return gpu.readOverlay(target);
        }
        @Override public void close() { gpu.close(); }
    }
}
