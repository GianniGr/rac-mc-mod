package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import java.util.*;

/**
 * Vendor-local frame/layer adapter; reuses the existing hierarchy/skinning evaluator through a
 * single sampled frame. No shared evaluator or accepted world/item fit is changed.
 * Float snapshots are not PS2 packed-pose re-encoding/quantisation or pixel-parity evidence.
 */
public final class Rac1ShopKioskPose {
    public enum Stage { CLOSED, UNFOLD, OPEN, REVERSE_FOLD }
    public record Modifier(int joint, float x, float y, float z, float w, float tx, float ty, float tz) {
        public Modifier(int joint, float x, float y, float z, float w) { this(joint,x,y,z,w,0,0,0); }
        public Modifier {
            if (joint < 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(w) || x*x+y*y+z*z+w*w < 1e-12f || x*x+y*y+z*z+w*w > 4)
                throw new IllegalArgumentException("Invalid original monitor quaternion modifier");
            if (!Float.isFinite(tx) || !Float.isFinite(ty) || !Float.isFinite(tz)
                    || Math.abs(tx) > 1e6 || Math.abs(ty) > 1e6 || Math.abs(tz) > 1e6)
                throw new IllegalArgumentException("Invalid original monitor translation modifier");
        }
    }
    public record Pose(float[] chain, float[] joints, float[] skinning, float[] positions, float[] normals) {}
    private record Frame(float[] rotations, float[] translations, float[] inherited, float[] post) {}
    private final Rac1AnimatedAsset body;
    public Rac1ShopKioskPose(Rac1AnimatedAsset body) {
        if (body.classNumber() != 11) throw new IllegalArgumentException("Shop kiosk pose requires class11");
        this.body = body;
        for (int i = 0; i < 5; i++) sequence(i);
    }
    /**
     * Both-disc original OpenVendorMenu writes node0/2 +Y and node1/3 -Y translations,
     * not angles: (7-count)*1665 packed units when count<8, otherwise0.
     * Class scale/1024 and (x,z,-y) conversion apply once, before any outer MC fit.
     */
    public static List<Modifier> monitorArms(Rac1ShopSceneTables tables, int entries) {
        if (entries < 0 || entries > 64) throw new IllegalArgumentException("Invalid bounded original shop entry count");
        int packed = entries < 8 ? (7-entries)*1665 : 0;
        float displacement = packed / 1024f * tables.vendorPackedScale() * Rac1AssetExtractor.UNIT_SCALE;
        int[] targets = tables.monitorTargets();
        List<Modifier> modifiers = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            modifiers.add(new Modifier(targets[i],0,0,0,1,0,0,(i%2 == 0 ? -1 : 1)*displacement));
        return List.copyOf(modifiers);
    }
    /** Original physical unfold/fold run at half speed; state/camera40-frame transition is separate. */
    public Pose stage(Stage stage, float elapsedSeconds, List<Modifier> modifiers) {
        time(elapsedSeconds);
        int slot = switch (stage) { case CLOSED -> 0; case UNFOLD -> 2; case OPEN -> 3; case REVERSE_FOLD -> 4; };
        var clip = sequence(slot);
        float at = switch (stage) {
            case CLOSED, OPEN -> 0;
            case UNFOLD -> Math.min(end(clip),elapsedSeconds*.5f);
            case REVERSE_FOLD -> Math.max(0,end(clip)-elapsedSeconds*.5f);
        };
        return evaluate(sample(clip,at),modifiers);
    }
    /** Original cross-sequence blend uses hemisphere-flipped normalised lerp of local quaternions. */
    public Pose blend(int fromSlot, float fromTime, int toSlot, float toTime, float weight, List<Modifier> modifiers) {
        time(fromTime); time(toTime);
        if (!Float.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Invalid pose blend weight");
        Frame a = sample(sequence(fromSlot),fromTime), b = sample(sequence(toSlot),toTime);
        float[] rotations = interpolateQuaternions(a.rotations,b.rotations,weight,true);
        return evaluate(new Frame(rotations,lerp(a.translations,b.translations,weight),
                lerp(a.inherited,b.inherited,weight),lerp(a.post,b.post,weight)),modifiers);
    }
    private Rac1Animation sequence(int slot) {
        for (var a : body.animations()) if (a.name().equals("sequence_" + slot) && a.keyCount() > 0) return a;
        throw new IllegalArgumentException("Missing original kiosk sequence_" + slot);
    }
    /** Source endpoint for the vendor-specific cross-sequence blend; no synthetic loop key. */
    public float endTime(int slot) { return end(sequence(slot)); }
    private static float end(Rac1Animation clip) {
        int[] mapping = clip.sourceTiming().sampledSourceKeys();
        for (int i = clip.keyCount()-1; i >= 0; i--)
            if (mapping.length == 0 || mapping[i] >= 0) return clip.times()[i];
        throw new IllegalArgumentException("Missing original kiosk frame endpoint");
    }
    private Frame sample(Rac1Animation clip, float time) {
        float at = Math.min(end(clip),time);
        int low = 0, high = 0;
        for (int i = 0; i < clip.keyCount(); i++) {
            if (clip.times()[i] <= at) low = i;
            if (clip.times()[i] >= at) { high = i; break; }
            high = i;
        }
        Frame a = key(clip,low);
        if (low == high) return a;
        Frame b = key(clip,high);
        float fraction = (at-clip.times()[low])/(clip.times()[high]-clip.times()[low]);
        int[] mapping = clip.sourceTiming().sampledSourceKeys();
        boolean consecutive = high == low+1 && (mapping.length == 0
                || (mapping[low] >= 0 && mapping[high] == mapping[low]+1));
        return new Frame(interpolateQuaternions(a.rotations,b.rotations,fraction,!consecutive),
                lerp(a.translations,b.translations,fraction),lerp(a.inherited,b.inherited,fraction),lerp(a.post,b.post,fraction));
    }
    private Frame key(Rac1Animation clip, int key) {
        int joints = body.skeleton().jointCount();
        float[] rotations = Arrays.copyOfRange(clip.rotations(),key*joints*4,(key+1)*joints*4);
        float[] translations = body.skeleton().restTranslations().clone();
        float[] inherited = new float[joints*3], post = new float[joints*3];
        Arrays.fill(inherited,1); Arrays.fill(post,1);
        for (int i = 0; i < clip.translationKeys().length; i++)
            if (clip.translationKeys()[i] == key)
                System.arraycopy(clip.translationValues(),i*3,translations,clip.translationJoints()[i]*3,3);
        for (int i = 0; i < clip.scaleKeys().length; i++)
            if (clip.scaleKeys()[i] == key)
                System.arraycopy(clip.scaleValues(),i*3,clip.scaleInherited()[i] ? inherited : post,clip.scaleJoints()[i]*3,3);
        return new Frame(rotations,translations,inherited,post);
    }
    private Pose evaluate(Frame frame, List<Modifier> modifiers) {
        int joints = body.skeleton().jointCount();
        if (modifiers.size() > 4) throw new IllegalArgumentException("Too many original monitor manipulators");
        float[] rotations = frame.rotations.clone();
        float[] translations = frame.translations.clone();
        Set<Integer> targets = new HashSet<>();
        for (Modifier modifier : modifiers) {
            int j = modifier.joint;
            if (j >= joints || !targets.add(j)) throw new IllegalArgumentException("Invalid/duplicate monitor manipulator target");
            int at = j*4;
            float x=rotations[at], y=rotations[at+1], z=rotations[at+2], w=rotations[at+3];
            // Original mode0 node: q <- q HamiltonProduct q_modifier, before hierarchy matrices.
            rotations[at]=w*modifier.x+x*modifier.w+y*modifier.z-z*modifier.y;
            rotations[at+1]=w*modifier.y+y*modifier.w+z*modifier.x-x*modifier.z;
            rotations[at+2]=w*modifier.z+z*modifier.w+x*modifier.y-y*modifier.x;
            rotations[at+3]=w*modifier.w-x*modifier.x-y*modifier.y-z*modifier.z;
            translations[j*3]+=modifier.tx; translations[j*3+1]+=modifier.ty; translations[j*3+2]+=modifier.tz;
        }
        int[] translationKeys = new int[joints], translationJoints = new int[joints];
        int[] scaleKeys = new int[joints*2], scaleJoints = new int[joints*2];
        boolean[] inherited = new boolean[joints*2]; float[] scales = new float[joints*6];
        for (int j = 0; j < joints; j++) {
            translationJoints[j]=j; scaleJoints[j]=scaleJoints[j+joints]=j; inherited[j]=true;
        }
        System.arraycopy(frame.inherited,0,scales,0,joints*3);
        System.arraycopy(frame.post,0,scales,joints*3,joints*3);
        var one = new Rac1Animation("shop_local_frame",new float[]{0},false,rotations,
                translationKeys,translationJoints,translations,scaleKeys,scaleJoints,scales,inherited);
        float[] chain = Rac1AnimationEvaluator.evaluateChain(body.skeleton(),one,0);
        float[] jointPose = Rac1AnimationEvaluator.evaluatePose(body.skeleton(),one,0);
        float[] skinning = Rac1AnimationEvaluator.evaluate(body.skeleton(),one,0,1);
        float[] positions = new float[body.mesh().positions().length], normals = new float[positions.length];
        Rac1AnimationEvaluator.pose(body.mesh(),skinning,positions,normals);
        return new Pose(chain,jointPose,skinning,positions,normals);
    }
    private static float[] lerp(float[] a, float[] b, float t) {
        float[] out = new float[a.length];
        for (int i = 0; i < out.length; i++) out[i]=a[i]*(1-t)+b[i]*t;
        return out;
    }
    private static float[] interpolateQuaternions(float[] a, float[] b, float t, boolean normalise) {
        if (t == 0) return a.clone();
        if (t == 1) return b.clone();
        float[] out = new float[a.length];
        for (int at = 0; at < out.length; at+=4) {
            float dot=0; for (int k=0;k<4;k++) dot+=a[at+k]*b[at+k];
            float sign=normalise && Float.floatToRawIntBits(dot)<0 ? -1 : 1;
            float length=0;
            for (int k=0;k<4;k++) { out[at+k]=a[at+k]*(1-t)+b[at+k]*t*sign; length+=out[at+k]*out[at+k]; }
            if (normalise) {
                if (!Float.isFinite(length) || length < 1e-12) throw new IllegalArgumentException("Degenerate original pose quaternion blend");
                float scale=(float)(1/Math.sqrt(length)); for (int k=0;k<4;k++) out[at+k]*=scale;
            }
        }
        return out;
    }
    private static void time(float time) {
        if (!Float.isFinite(time) || time < 0) throw new IllegalArgumentException("Invalid original kiosk time");
    }
}
