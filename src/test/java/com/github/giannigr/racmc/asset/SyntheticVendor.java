package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.Map;

/** Entirely hand-built fixture, containing no proprietary asset data. */
public final class SyntheticVendor {
    private SyntheticVendor() {
    }

    public static Rac1AnimatedAsset asset(int classNumber) {
        float[] positions = {-2, -0.1f, -2, 2, -0.1f, 2, 0, 2, 0};
        float[] normals = {0, 1, 0, 0, 1, 0, 0, 1, 0};
        Rac1SkinnedMesh mesh = new Rac1SkinnedMesh(positions, normals,
                new float[] {0, 0, 1, 0, 0, 1}, new int[] {0, 1, 2}, new int[] {0},
                new int[] {1, 0, 0, 2, 0, 0, 0, 0, 0},
                new float[] {1, 0, 0, 1, 0, 0, 1, 0, 0});
        float[] inverse = new float[48];
        for (int at = 0; at < 48; at += 16) {
            inverse[at] = inverse[at + 5] = inverse[at + 10] = inverse[at + 15] = 1;
        }
        Rac1Skeleton skeleton = new Rac1Skeleton(inverse, new int[] {-1, 0, 0}, new float[9]);
        Rac1Animation closed = new Rac1Animation("sequence_0", new float[] {0}, false,
                new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1},
                new int[0], new int[0], new float[0], new int[0], new int[0],
                new float[0], new boolean[0]);
        return new Rac1AnimatedAsset(classNumber, "synthetic_" + classNumber, mesh, skeleton,
                new Rac1Animation[] {closed},
                new Rac1Texture[] {new Rac1Texture(1, 1, new byte[] {-1, 64, 32, 16})});
    }

    public static Rac1VendorAsset vendor() {
        return new Rac1VendorAsset(asset(11), asset(1143));
    }

    public static Rac1CacheContent content() {
        Rac1AnimatedAsset asset = asset(0);
        Rac1Mesh mesh = new Rac1Mesh(asset.mesh().positions(), asset.mesh().normals(),
                asset.mesh().uvs(), asset.mesh().indices(), asset.mesh().materials());
        return new Rac1CacheContent("synthetic-vendor", new Rac1StaticAsset(452, "fixture",
                mesh, asset.textures()), asset, asset, 0, Map.of(), vendor());
    }
}
