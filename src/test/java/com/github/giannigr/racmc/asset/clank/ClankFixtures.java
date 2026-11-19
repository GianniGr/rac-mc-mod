package com.github.giannigr.racmc.asset.clank;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.Rac1DiscCatalog;
import java.io.IOException;
import java.util.Arrays;

/** Synthetic independent fixture, never original mesh/texture bytes. */
public final class ClankFixtures {
    private ClankFixtures() {}
    public static String disc(int index) {
        var p = Rac1DiscCatalog.supported().profiles().get(index);
        return Rac1DiscIdentity.key(p.revision(), p);
    }
    public static Rac1AnimatedAsset model(int id) {
        int count = switch (id) { case 601 -> 33; case 607 -> 36; case 609 -> 10; default -> 15; };
        int clips = id == 601 ? 18 : id == 609 ? 5 : 9;
        float[] inverse = new float[count * 16], rotations = new float[count * 4];
        int[] parents = new int[count];
        Arrays.fill(parents, -1);
        for (int i = 0; i < count; i++) {
            inverse[16 * i] = inverse[16 * i + 5] = inverse[16 * i + 10] = inverse[16 * i + 15] = 1;
            rotations[i * 4 + 3] = 1;
        }
        var animations = new Rac1Animation[clips];
        for (int i = 0; i < clips; i++)
            animations[i] = new Rac1Animation("sequence_" + i, new float[] { 0 }, false,
                    rotations.clone(), new int[0], new int[0], new float[0],
                    new int[0], new int[0], new float[0], new boolean[0],
                    new Rac1SourceTiming(0, new int[] { 0 }, new int[] { 0 },
                            new float[] { 0 }, new int[] { 0 }));
        return new Rac1AnimatedAsset(id, "synthetic_" + id,
                new Rac1SkinnedMesh(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0},
                        new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1}, new float[6],
                        new int[] {0, 1, 2}, new int[] {0}, new int[9],
                        new float[] {1, 0, 0, 1, 0, 0, 1, 0, 0}),
                new Rac1Skeleton(inverse, parents, new float[count * 3]), animations,
                new Rac1Texture[] {new Rac1Texture(1, 1, new byte[] {-1, 0, 0, 0})},
                id == 601 ? new int[][] {{0}, {0}, {0}, {0}, {0}, {0}, {10}}
                        : id == 608 ? new int[][] {{0}, {0}, {0}, {0}} : new int[0][]);
    }
    public static Rac1ClankAssets bundle() throws IOException {
        return new Rac1ClankAssets(disc(0), model(601), model(607), model(608));
    }
}
