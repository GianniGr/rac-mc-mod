package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1AttachmentSampler;
import com.github.giannigr.racmc.serverassets.Rac1WrenchData;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original attachment-list calibrated projection onto rigid Steve arms. No elbow IDs guessed,
 * no stretched limbs or Ratchet geometry. Render and server use exactly these socket matrices.
 */
public final class WrenchSteveRig {
    // Source +X forward,+Y up,+Z weapon side -> native model -Z forward,-Y up,-X right.
    private static final Quaternionf BASIS = new Quaternionf().rotationAxis((float) Math.PI,
            (float) Math.sqrt(0.5), 0, (float) -Math.sqrt(0.5));
    private final Rac1WrenchData data;
    private final int root;
    private final int torso;
    private final int weaponShoulder;
    private final int otherShoulder;
    private final int weaponHand;
    private final int otherHand;
    private final Matrix4f[] rest;

    public WrenchSteveRig(Rac1WrenchData data) {
        this.data = data;
        Rac1AnimatedAsset hero = data.hero();
        root = leaf(hero, 9);
        torso = leaf(hero, 11);
        weaponShoulder = leaf(hero, 12);
        otherShoulder = leaf(hero, 13);
        weaponHand = leaf(hero, 0);
        otherHand = leaf(hero, 14);
        requirePrefix(hero.attachmentChain(12), hero.attachmentChain(0));
        requirePrefix(hero.attachmentChain(13), hero.attachmentChain(14));
        rest = new Matrix4f[hero.skeleton().jointCount()];
        float[] inverse = hero.skeleton().inverseBind();
        for (int j = 0; j < rest.length; j++) {
            rest[j] = matrix(inverse, j).invert();
        }
    }

    public Pose sample(int bodySequence, double bodySeconds, int childSequence, double childSeconds,
            boolean leftHand, boolean slim, double bodyWeight) {
        return sample(bodySequence, bodySeconds, childSequence, childSeconds, leftHand, slim,
                bodyWeight, false);
    }

    public Pose sample(int bodySequence, double bodySeconds, int childSequence, double childSeconds,
            boolean leftHand, boolean slim, double bodyWeight, boolean crouching) {
        var clip = data.heroSequence(bodySequence).orElseThrow();
        float[] palette = Rac1AnimationEvaluator.evaluateChain(data.hero().skeleton(), clip,
                (float) bodySeconds);
        Matrix4f inverseRoot = matrix(palette, root).invert();
        Matrix4f inverseRestRoot = new Matrix4f(rest[root]).invert();
        Matrix4f body = new Matrix4f(inverseRoot).mul(matrix(palette, torso));
        Matrix4f bindBody = new Matrix4f(inverseRestRoot).mul(rest[torso]);
        Quaternionf torsoDelta = delta(body, bindBody);
        Quaternionf torsoRotation = conjugate(torsoDelta);
        Quaternionf weapon = arm(palette, inverseRoot, inverseRestRoot, weaponShoulder, weaponHand);
        Quaternionf other = arm(palette, inverseRoot, inverseRestRoot, otherShoulder, otherHand);
        Quaternionf wristDelta = conjugate(delta(
                new Matrix4f(inverseRoot).mul(matrix(palette, weaponHand)),
                new Matrix4f(inverseRestRoot).mul(rest[weaponHand])));
        if (leftHand) {
            torsoRotation = mirror(torsoRotation);
            wristDelta = mirror(wristDelta);
            Quaternionf original = weapon;
            weapon = mirror(other);
            other = mirror(original);
        }
        float weight = (float) Math.clamp(bodyWeight, 0, 1);
        torsoRotation = new Quaternionf().slerp(torsoRotation, weight);
        weapon = new Quaternionf().slerp(weapon, weight);
        other = new Quaternionf().slerp(other, weight);
        wristDelta = new Quaternionf().slerp(wristDelta, weight);
        // Native parts are flat siblings. A shared hip-root rotates the WHOLE native model/layers;
        // never copy independent Ratchet torso translations onto only three Steve parts.
        Matrix4f rootMatrix = modelRoot(torsoRotation, crouching);
        Part bodyPart = new Part(0, crouching ? 3.2F : 0, 0, crouching ? 0.5F : 0, 0, 0);
        Quaternionf inverseRootRotation = new Quaternionf(torsoRotation).conjugate();
        Part right = part(new Vector3f(-5, crouching ? 5.2F : 2, 0),
                new Quaternionf(inverseRootRotation).mul(weapon));
        Part left = part(new Vector3f(5, crouching ? 5.2F : 2, 0),
                new Quaternionf(inverseRootRotation).mul(other));
        // Native slim translateToHand correction is in parent space BEFORE arm rotation.
        Matrix4f arm = modelPart(leftHand ? left : right,
                slim ? (leftHand ? -0.5F : 0.5F) : 0);
        Matrix4f nativeSocket = new Matrix4f(rootMatrix).mul(SteveWrenchPose.socket(arm, leftHand))
                .mul(itemDisplay());
        // Steve has no wrist bone. Preserve the source wrist delta by rotating the accessory
        // ABOUT the real native grip, not by moving it to a detached source hand position.
        Quaternionf restGrip = SteveWrenchPose.socket(new Matrix4f(), leftHand).mul(itemDisplay())
                .getUnnormalizedRotation(new Quaternionf()).normalize();
        Matrix4f socket = new Matrix4f().translation(nativeSocket.getTranslation(new Vector3f()))
                .rotate(wristDelta.mul(restGrip));
        Matrix4f itemAdjustment = new Matrix4f(nativeSocket).invert().mul(socket);
        Matrix4f head = new Matrix4f(socket).mul(WrenchActionGeometry.head(
                data.wrench(), childSequence, childSeconds));
        return new Pose(bodyPart, right, left, socket, head, rootMatrix, itemAdjustment);
    }

    private Quaternionf arm(float[] palette, Matrix4f invRoot, Matrix4f invBind,
            int shoulder, int hand) {
        Matrix4f current = new Matrix4f(invRoot).mul(matrix(palette, hand));
        Vector3f a = new Matrix4f(invRoot).mul(matrix(palette, shoulder))
                .getTranslation(new Vector3f());
        Vector3f direction = current.getTranslation(new Vector3f()).sub(a);
        if (direction.lengthSquared() < 1.0e-8F) {
            throw new IllegalArgumentException("Collapsed original arm chain");
        }
        BASIS.transform(direction).normalize();
        Quaternionf swing = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
        Quaternionf handDelta = conjugate(delta(current, new Matrix4f(invBind).mul(rest[hand])));
        Quaternionf relative = new Quaternionf(swing).conjugate().mul(handDelta);
        float length = (float) Math.sqrt(relative.y * relative.y + relative.w * relative.w);
        Quaternionf twist = length < 1.0e-5 ? new Quaternionf()
                : new Quaternionf(0, relative.y / length, 0, relative.w / length);
        return swing.mul(twist).normalize();
    }

    private static Quaternionf delta(Matrix4f current, Matrix4f bind) {
        return current.getUnnormalizedRotation(new Quaternionf()).normalize()
                .mul(bind.getUnnormalizedRotation(new Quaternionf()).normalize().conjugate());
    }

    private static Quaternionf conjugate(Quaternionf rotation) {
        return new Quaternionf(BASIS).mul(rotation).mul(new Quaternionf(BASIS).conjugate()).normalize();
    }

    private static Quaternionf mirror(Quaternionf q) {
        return new Quaternionf(q.x, -q.y, -q.z, q.w);
    }

    private static Part part(Vector3f pivot, Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
        return new Part(pivot.x, pivot.y, pivot.z, euler.x, euler.y, euler.z);
    }

    public static Matrix4f modelPart(Part part, float slimCorrection) {
        return SteveWrenchPose.part(new Matrix4f(),
                new ActionVector(part.x + slimCorrection, part.y, part.z),
                part.xRot, part.yRot, part.zRot);
    }

    /** Left JSON -90deg is mirrored by ItemTransform.apply(true), yielding the same +90deg. */
    public static Matrix4f itemDisplay() {
        return new Matrix4f().rotateZ((float) (Math.PI / 2));
    }

    public static Matrix4f modelRoot(Quaternionf rotation, boolean crouching) {
        float y = (crouching ? 12.2F : 12) / 16;
        float z = (crouching ? 4 : 0) / 16F;
        return new Matrix4f().translation(0, y, z).rotate(rotation).translate(0, -y, -z);
    }

    public static Matrix4f blendRoot(Matrix4f root, boolean crouching, double weight) {
        Quaternionf target = root.getUnnormalizedRotation(new Quaternionf()).normalize();
        return modelRoot(new Quaternionf().slerp(target, (float) Math.clamp(weight, 0, 1)), crouching);
    }

    /** Matches LivingEntityRenderer's player frame, without camera/viewmodel offsets. */
    public static Matrix4f world(double x, double y, double z, float bodyYaw) {
        return world(x, y, z, bodyYaw, 1, false);
    }

    public static Matrix4f world(double x, double y, double z, float bodyYaw, float scale,
            boolean crouching) {
        return new Matrix4f().translation((float) x,
                        (float) (y - (crouching ? scale * 0.125 : 0)), (float) z)
                .scale(scale).rotateY((float) Math.toRadians(180 - bodyYaw))
                .scale(-0.9375F, -0.9375F, 0.9375F)
                .translate(0, -1.501F, 0);
    }

    private static int leaf(Rac1AnimatedAsset asset, int list) {
        int[] chain = asset.attachmentChain(list);
        if (chain.length == 0) throw new IllegalArgumentException("Missing calibrated list " + list);
        return chain[chain.length - 1];
    }

    private static void requirePrefix(int[] shoulder, int[] hand) {
        if (shoulder.length >= hand.length
                || !Arrays.equals(shoulder, Arrays.copyOf(hand, shoulder.length))) {
            throw new IllegalArgumentException("Original shoulder is not on hand chain");
        }
    }

    private static Matrix4f matrix(float[] palette, int joint) {
        return new Matrix4f().set(Arrays.copyOfRange(palette, joint * 16, joint * 16 + 16));
    }

    public record Part(float x, float y, float z, float xRot, float yRot, float zRot) {}
    public record Pose(Part body, Part rightArm, Part leftArm, Matrix4f socket, Matrix4f head,
            Matrix4f root, Matrix4f itemAdjustment) {}
}
