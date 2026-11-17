package com.github.giannigr.racmc.asset;

import java.util.Arrays;
import java.util.Optional;

/** Pure local-data attachment sampling; no client singleton, disc IO or embedded trajectories. */
public final class Rac1AttachmentSampler {
    private Rac1AttachmentSampler() { }

    /**
     * Converted model-space, column-major joint pose, NOT a skinning matrix or Steve world socket.
     * Retains inherited scales, excludes local-only post scales like the existing chain evaluator.
     * Source list 0 on Ratchet is the known hand chain; list 1 on gadget71 is the head chain.
     */
    public static Optional<float[]> sample(Rac1AnimatedAsset asset, String sequenceName,
                                            float seconds, int listIndex) {
        if (!Float.isFinite(seconds) || seconds < 0 || listIndex < 0) {
            throw new IllegalArgumentException("Invalid attachment sample.");
        }
        if (asset == null) {
            return Optional.empty();
        }
        int[] chain = asset.attachmentChain(listIndex);
        if (chain.length == 0) {
            return Optional.empty();
        }
        for (Rac1Animation animation : asset.animations()) {
            if (animation.name().equals(sequenceName)) {
                float[] palette = Rac1AnimationEvaluator.evaluateChain(asset.skeleton(),
                        animation, seconds);
                int at = chain[chain.length - 1] * 16;
                return Optional.of(Arrays.copyOfRange(palette, at, at + 16));
            }
        }
        return Optional.empty();
    }
}
