package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1AttachmentSampler;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original local geometry plus an explicit Steve-only presentation scale. */
public final class WrenchActionGeometry {
    // Authentic source amplitude until an explicit owner presentation-size decision.
    public static final float SLAM_DEFORMATION = 1.0F;
    public static final float STEVE_MESH_SCALE = 1.18F;
    private WrenchActionGeometry() {}
    public record Mesh(float[] positions, float[] normals) {}

    /** The native socket is the grip pivot; scaling never moves it or the legacy contact head. */
    public static ActionVector visiblePoint(ActionVector grip, ActionVector originalPoint) {
        return grip.add(originalPoint.subtract(grip).multiply(STEVE_MESH_SCALE));
    }

    public static Mesh mesh(Rac1AnimatedAsset asset, int sequence, float seconds) {
        Mesh original = raw(asset, sequence, seconds);
        if (sequence != 10 || SLAM_DEFORMATION == 1 || original.positions.length == 0) return original;
        Mesh held = raw(asset, 1, 0);
        if (held.positions.length != original.positions.length) {
            throw new IllegalArgumentException("Wrench deformation topology mismatch");
        }
        for (int i = 0; i < original.positions.length; i++) {
            original.positions[i] = held.positions[i]
                    + SLAM_DEFORMATION * (original.positions[i] - held.positions[i]);
            original.normals[i] = held.normals[i]
                    + SLAM_DEFORMATION * (original.normals[i] - held.normals[i]);
        }
        for (int i = 0; i < original.normals.length; i += 3) {
            Vector3f n = new Vector3f(original.normals[i], original.normals[i + 1],
                    original.normals[i + 2]);
            if (n.lengthSquared() > 1.0e-9F) n.normalize();
            original.normals[i] = n.x;
            original.normals[i + 1] = n.y;
            original.normals[i + 2] = n.z;
        }
        return original;
    }

    private static Mesh raw(Rac1AnimatedAsset asset, int sequence, float seconds) {
        var clip = Arrays.stream(asset.animations())
                .filter(a -> a.name().equals("sequence_" + sequence)).findFirst().orElse(null);
        if (clip == null) return new Mesh(new float[0], new float[0]);
        float[] positions = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        Rac1AnimationEvaluator.pose(asset.mesh(),
                Rac1AnimationEvaluator.evaluate(asset.skeleton(), clip, seconds, 1), positions, normals);
        return new Mesh(positions, normals);
    }

    public static Matrix4f head(Rac1AnimatedAsset asset, int sequence, double seconds) {
        Matrix4f matrix = new Matrix4f().set(Rac1AttachmentSampler.sample(asset,
                "sequence_" + sequence, (float) seconds, 1).orElseThrow());
        if (sequence == 10 && SLAM_DEFORMATION != 1) {
            Vector3f rest = new Matrix4f().set(Rac1AttachmentSampler.sample(asset,
                    "sequence_1", 0, 1).orElseThrow()).getTranslation(new Vector3f());
            matrix.setTranslation(rest.lerp(matrix.getTranslation(new Vector3f()), SLAM_DEFORMATION));
        }
        return matrix;
    }

    public static Matrix4f anchor(Rac1AnimatedAsset asset, int sequence, int list, double blend) {
        Matrix4f matrix = list == 1 ? head(asset, sequence, 0)
                : new Matrix4f().set(Rac1AttachmentSampler.sample(asset, "sequence_" + sequence,
                        0, list).orElseThrow());
        if (blend < 1) {
            int from = sequence == 1 ? 6 : 1;
            Matrix4f previous = list == 1 ? head(asset, from, 0)
                    : new Matrix4f().set(Rac1AttachmentSampler.sample(asset, "sequence_" + from,
                            0, list).orElseThrow());
            matrix.setTranslation(previous.getTranslation(new Vector3f())
                    .lerp(matrix.getTranslation(new Vector3f()), (float) blend));
        }
        return matrix;
    }
}
