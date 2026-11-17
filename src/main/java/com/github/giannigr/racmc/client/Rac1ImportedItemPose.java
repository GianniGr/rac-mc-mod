package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;

/** Stateless sequence/time sampling. No action clock, event dispatch or Minecraft dependency. */
public final class Rac1ImportedItemPose {
    public record Sample(float[] positions, float[] normals) { }

    private Rac1ImportedItemPose() { }

    /** Times are seconds in the existing imported evaluator, not source gameplay key indices. */
    public static Sample sample(Rac1AnimatedAsset asset, int sequence, float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0 || sequence < 0) {
            throw new IllegalArgumentException("Invalid imported-item sample time/sequence.");
        }
        if (asset != null) {
            String name = "sequence_" + sequence;
            for (Rac1Animation animation : asset.animations()) {
                if (animation.name().equals(name)) {
                    float[] positions = new float[asset.mesh().positions().length];
                    float[] normals = new float[asset.mesh().normals().length];
                    float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(),
                            animation, seconds, 1.0f);
                    Rac1AnimationEvaluator.pose(asset.mesh(), matrices, positions, normals);
                    return new Sample(positions, normals);
                }
            }
        }
        return new Sample(new float[0], new float[0]);
    }

    public static float[] bounds(float[] positions) {
        if (positions.length == 0) {
            return null;
        }
        float[] result = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY };
        for (int i = 0; i < positions.length; i++) {
            int axis = i % 3;
            result[axis] = Math.min(result[axis], positions[i]);
            result[axis + 3] = Math.max(result[axis + 3], positions[i]);
        }
        return result;
    }
}
