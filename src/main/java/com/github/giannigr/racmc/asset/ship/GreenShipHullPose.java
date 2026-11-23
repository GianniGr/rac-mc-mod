package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.MobySequenceReader;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import java.util.Arrays;

/** CPU preparation for the two confirmed source poses, in the P45 ship-local axes. */
public record GreenShipHullPose(Rac1Mesh parked, Rac1Mesh closed, float[] parkedGlass, float[] closedGlass) {
    public static final float BLOCKS_PER_UNIT = (float) ShipCockpitRefit.activeProfile().blocksPerSourceUnit();

    public GreenShipHullPose {
        parkedGlass = parkedGlass.clone();
        closedGlass = closedGlass.clone();
    }

    @Override public float[] parkedGlass() { return parkedGlass.clone(); }
    @Override public float[] closedGlass() { return closedGlass.clone(); }

    public static GreenShipHullPose prepare(GreenShipContainer.Hull hull) {
        var raw = hull.mesh();
        var mesh = new Rac1SkinnedMesh(raw.positions(), raw.normals(), canonicalU(raw.uvs()), raw.indices(),
                raw.materials(), raw.joints(), raw.weights());
        var rig = hull.skeleton();
        float[] inverse = rig.inverseBind().clone();
        float[] rest = scaled(rig.restTranslations(), hull.classScale());
        for (int joint = 0; joint < rig.jointCount(); joint++) {
            int at = joint * 16;
            inverse[at + 12] *= hull.classScale();
            inverse[at + 13] *= hull.classScale();
            inverse[at + 14] *= hull.classScale();
        }
        var skeleton = new Rac1Skeleton(inverse, rig.parents(), rest);
        Frame parked = frameZero(mesh, skeleton, hull.parked(), hull.classScale(), hull.glassJoint());
        Frame closed = frameZero(mesh, skeleton, hull.closed(), hull.classScale(), hull.glassJoint());
        return new GreenShipHullPose(parked.mesh(), closed.mesh(), parked.glass(), closed.glass());
    }

    /** The verified hull uses one complete repeated tile [-2,-1] on U, not per-vertex wrapping. */
    static float[] canonicalU(float[] uvs) {
        boolean translatedTile = uvs.length != 0;
        for (int i = 0; i < uvs.length; i += 2) {
            translatedTile &= uvs[i] >= -2 && uvs[i] <= -1;
        }
        if (!translatedTile) return uvs;
        var canonical = uvs.clone();
        for (int i = 0; i < canonical.length; i += 2) canonical[i] += 2;
        return canonical;
    }

    private record Frame(Rac1Mesh mesh, float[] glass) {}

    private static Frame frameZero(Rac1SkinnedMesh mesh, Rac1Skeleton rig,
                                   MobySequenceReader.Keys keys, float scale, int glassJoint) {
        var anim = new Rac1Animation("frame_zero", keys.times(), keys.loop(), keys.rotations(),
                keys.translationKeys(), keys.translationJoints(), scaled(keys.translationValues(), scale),
                keys.scaleKeys(), keys.scaleJoints(), keys.scaleValues(), keys.scaleInherited());
        var matrices = Rac1AnimationEvaluator.evaluate(rig, anim, 0, 1);
        float[] positions = new float[mesh.positions().length];
        float[] normals = new float[mesh.normals().length];
        Rac1AnimationEvaluator.pose(mesh, matrices, positions, normals);
        toShipSpace(positions, BLOCKS_PER_UNIT);
        toShipSpace(normals, 1);
        int glassAt = glassJoint * Rac1AnimationEvaluator.MATRIX_SIZE;
        var chain = Rac1AnimationEvaluator.evaluateChain(rig, anim, 0);
        float[] glass = Arrays.copyOfRange(chain, glassAt, glassAt + Rac1AnimationEvaluator.MATRIX_SIZE);
        return new Frame(new Rac1Mesh(positions, normals, mesh.uvs(), mesh.indices(), mesh.materials()),
                glass);
    }

    private static float[] scaled(float[] source, float scale) {
        float[] result = source.clone();
        for (int at = 0; at < result.length; at++) {
            result[at] *= scale;
        }
        return result;
    }

    static void toShipSpace(float[] vectors, float scale) {
        for (int at = 0; at < vectors.length; at += 3) {
            float forward = vectors[at];
            vectors[at] = vectors[at + 1] * scale;
            vectors[at + 1] = vectors[at + 2] * scale;
            vectors[at + 2] = forward * scale;
        }
    }
}
