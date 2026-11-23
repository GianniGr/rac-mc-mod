package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local-joint snapshot blend, with inherited and joint-only scale kept separate. */
public final class Rac1ClankPoseBlend {
    public record Bone(Vector3f translation,Quaternionf rotation,Vector3f scale,Vector3f postScale) {}
    private Rac1ClankPoseBlend() {}
    public static Bone[] sample(Rac1AnimatedAsset model,Rac1Animation clip,float seconds) {
        float[] chains=Rac1AnimationEvaluator.evaluateChain(model.skeleton(),clip,seconds);
        float[] full=Rac1AnimationEvaluator.evaluatePose(model.skeleton(),clip,seconds);
        Bone[] result=new Bone[model.skeleton().jointCount()];
        for(int i=0;i<result.length;i++) {
            var chain=new Matrix4f().set(chains,i*16);
            var local=new Matrix4f(chain);
            int parent=model.skeleton().parents()[i];
            if(parent>=0) {
                var parentMatrix=new Matrix4f().set(chains,parent*16);
                if(Math.abs(parentMatrix.determinant())<1e-12)return null;
                local=parentMatrix.invert().mul(local);
            }
            if(Math.abs(chain.determinant())<1e-12)return null;
            var post=new Matrix4f(chain).invert().mul(new Matrix4f().set(full,i*16));
            result[i]=new Bone(local.getTranslation(new Vector3f()),
                    local.getUnnormalizedRotation(new Quaternionf()).normalize(),
                    local.getScale(new Vector3f()),post.getScale(new Vector3f()));
        }
        return result;
    }
    public static Bone[] blend(Bone[] from,Bone[] to,float weight) {
        if(from==null||to==null)return to;
        float t=Math.clamp(weight,0,1);
        Bone[] result=new Bone[to.length];
        for(int i=0;i<result.length;i++)result[i]=new Bone(
                new Vector3f(from[i].translation()).lerp(to[i].translation(),t),
                new Quaternionf(from[i].rotation()).slerp(to[i].rotation(),t),
                new Vector3f(from[i].scale()).lerp(to[i].scale(),t),
                new Vector3f(from[i].postScale()).lerp(to[i].postScale(),t));
        return result;
    }
    public static float[] skin(Rac1AnimatedAsset model,Bone[] bones) {
        var skeleton=model.skeleton();
        Matrix4f[] chain=new Matrix4f[bones.length];
        float[] skin=new float[bones.length*16];
        for(int i=0;i<bones.length;i++) {
            var b=bones[i];
            var local=new Matrix4f().translationRotateScale(b.translation(),b.rotation(),b.scale());
            int parent=skeleton.parents()[i];
            chain[i]=parent<0?local:new Matrix4f(chain[parent]).mul(local);
            new Matrix4f(chain[i]).scale(b.postScale())
                    .mul(new Matrix4f().set(skeleton.inverseBind(),i*16)).get(skin,i*16);
        }
        return skin;
    }
    /** Attachment chains from the exact blended local poses, excluding joint-only post scale. */
    public static float[] chain(Rac1AnimatedAsset model,Bone[] bones) {
        if(bones==null)return null;
        Matrix4f[] chain=new Matrix4f[bones.length];float[] palette=new float[bones.length*16];
        for(int i=0;i<bones.length;i++) {
            var b=bones[i];var local=new Matrix4f().translationRotateScale(b.translation(),b.rotation(),b.scale());
            int parent=model.skeleton().parents()[i];
            chain[i]=parent<0?local:new Matrix4f(chain[parent]).mul(local);chain[i].get(palette,i*16);
        }
        return palette;
    }
}
