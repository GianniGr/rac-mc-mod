package com.github.giannigr.racmc.client;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.*;
import org.junit.jupiter.api.Test;

class Rac1ImportedItemPoseTest {
    private static Rac1AnimatedAsset asset() {
        float[] inverse = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
        var skeleton = new Rac1Skeleton(inverse, new int[] { -1 }, new float[3]);
        var mesh = new Rac1SkinnedMesh(new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 },
                new float[] { 0, 0, 1, 0, 0, 1, 0, 0, 1 }, new float[6],
                new int[] { 0, 1, 2 }, new int[] { 0 }, new int[9],
                new float[] { 1, 0, 0, 1, 0, 0, 1, 0, 0 });
        var animation = new Rac1Animation("sequence_1", new float[] { 0, 1 }, false,
                new float[] { 0, 0, 0, 1, 0, 0, 0, 1 }, new int[] { 0, 1 },
                new int[] { 0, 0 }, new float[] { 0, 0, 0, 2, 0, 0 },
                new int[0], new int[0], new float[0], new boolean[0]);
        return new Rac1AnimatedAsset(71, "synthetic", mesh, skeleton,
                new Rac1Animation[] { animation }, new Rac1Texture[0]);
    }

    @Test
    void sequenceAndSecondsSampleWithoutMutatingAssetOrAdvancingAClock() {
        var asset = asset();
        var first = Rac1ImportedItemPose.sample(asset, 1, 0);
        var middle = Rac1ImportedItemPose.sample(asset, 1, 0.5f);
        assertEquals(0, first.positions()[0], 1e-5);
        assertEquals(1, middle.positions()[0], 1e-5);
        assertArrayEquals(first.positions(), Rac1ImportedItemPose.sample(asset, 1, 0).positions());
        assertEquals(0, asset.mesh().positions()[0]);
        assertEquals(1, middle.normals()[2], 1e-5);
        assertArrayEquals(new float[] { 1, 0, 0, 2, 1, 0 },
                Rac1ImportedItemPose.bounds(middle.positions()), 1e-5f);
    }

    @Test
    void unavailableSequenceIsNotGuessedAndInvalidTimeIsRejected() {
        assertEquals(0, Rac1ImportedItemPose.sample(asset(), 99, 0).positions().length);
        assertEquals(0, Rac1ImportedItemPose.sample(null, 1, 0).positions().length);
        for (float time : new float[] { -1, Float.NaN, Float.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class,
                    () -> Rac1ImportedItemPose.sample(asset(), 1, time));
        }
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ImportedItemPose.sample(asset(), -1, 0));
    }
}
