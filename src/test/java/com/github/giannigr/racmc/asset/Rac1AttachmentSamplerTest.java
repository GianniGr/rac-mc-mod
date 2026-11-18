package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Rac1AttachmentSamplerTest {
    private static Rac1AnimatedAsset asset() {
        var skeleton = new Rac1Skeleton(new float[32], new int[] { -1, 0 },
                new float[] { 0, 0, 0, 1, 2, 3 });
        var mesh = new Rac1SkinnedMesh(new float[0], new float[0], new float[0],
                new int[0], new int[0], new int[0], new float[0]);
        var clip = new Rac1Animation("ratchet_sequence_43", new float[] { 0 }, false,
                new float[] { 0, 0, 0, 1, 0, 0, 0, 1 }, new int[0], new int[0],
                new float[0], new int[0], new int[0], new float[0], new boolean[0]);
        return new Rac1AnimatedAsset(0, "synthetic", mesh, skeleton,
                new Rac1Animation[] { clip }, new Rac1Texture[0],
                new int[][] { { 0, 1 } });
    }

    @Test
    void attachmentIsJointPoseNotSkinningAndListIndexIsNotJointIndex() {
        var asset = asset();
        var matrix = Rac1AttachmentSampler.sample(asset, "ratchet_sequence_43", 0, 0).orElseThrow();
        assertEquals(1, matrix[0]);
        assertEquals(1, matrix[12]);
        assertEquals(2, matrix[13]);
        assertEquals(3, matrix[14]);
        assertArrayEquals(new int[] { 0, 1 }, asset.attachmentChain(0));
        var copy = asset.attachmentChain(0);
        copy[1] = 0;
        assertEquals(1, asset.attachmentChain(0)[1]);
        assertTrue(Rac1AttachmentSampler.sample(asset, "ratchet_sequence_43", 0, 1).isEmpty());
    }

    @Test
    void missingAssetClipAndInvalidTimeDoNotInventSockets() {
        assertTrue(Rac1AttachmentSampler.sample(null, "sequence_1", 0, 0).isEmpty());
        assertTrue(Rac1AttachmentSampler.sample(asset(), "sequence_1", 0, 0).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> Rac1AttachmentSampler.sample(asset(), "ratchet_sequence_43", Float.NaN, 0));
    }
}
