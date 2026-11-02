package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.asset.Rac1Texture;

/**
 * The assets that the checks of the cache use.
 *
 * <p>The assets are built by hand. They are small, but they have the same shape as the assets that
 * are read out of the disc of the game, so that the checks of the cache cover the same code paths
 * as the real thing. No data of the game is used anywhere.
 */
final class SyntheticAssets {

    /** The number of the joints of the skeleton of the animated asset. */
    static final int JOINT_COUNT = 3;

    /** The number of the vertices of the meshes. */
    static final int VERTEX_COUNT = 4;

    private SyntheticAssets() {
    }

    /**
     * The static asset of the checks.
     *
     * @return the asset
     */
    static Rac1StaticAsset staticAsset() {
        float[] positions = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
                1.0f };
        float[] normals = { 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f };
        float[] uvs = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f };
        int[] indices = { 0, 1, 2, 1, 3, 2 };
        int[] materials = { 0, 0 };
        Rac1Mesh mesh = new Rac1Mesh(positions, normals, uvs, indices, materials);
        return new Rac1StaticAsset(452, "tie_452", mesh, new Rac1Texture[] { texture(2, 2) });
    }

    /**
     * The animated asset of the checks.
     *
     * @return the asset
     */
    static Rac1AnimatedAsset animatedAsset() {
        float[] positions = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
                1.0f };
        float[] normals = { 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f };
        float[] uvs = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f };
        int[] indices = { 0, 1, 2, 1, 3, 2 };
        int[] materials = { 0, 0 };
        int[] joints = { 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 0 };
        float[] weights = { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        Rac1SkinnedMesh mesh = new Rac1SkinnedMesh(positions, normals, uvs, indices, materials,
                joints, weights);

        float[] inverseBind = new float[JOINT_COUNT * 16];
        int[] parents = { -1, 0, 1 };
        float[] rest = new float[JOINT_COUNT * 3];
        for (int joint = 0; joint < JOINT_COUNT; joint++) {
            inverseBind[joint * 16] = 1.0f;
            inverseBind[joint * 16 + 5] = 1.0f;
            inverseBind[joint * 16 + 10] = 1.0f;
            inverseBind[joint * 16 + 15] = 1.0f;
            rest[joint * 3 + 1] = joint;
        }
        Rac1Skeleton skeleton = new Rac1Skeleton(inverseBind, parents, rest);

        float[] rotations = new float[2 * JOINT_COUNT * 4];
        for (int key = 0; key < 2; key++) {
            for (int joint = 0; joint < JOINT_COUNT; joint++) {
                rotations[(key * JOINT_COUNT + joint) * 4 + 3] = 1.0f;
            }
        }
        Rac1Animation animation = new Rac1Animation("sequence_0", new float[] { 0.0f, 0.5f }, true,
                rotations, new int[0], new int[0], new float[0], new int[0], new int[0],
                new float[0], new boolean[0]);
        return new Rac1AnimatedAsset(0, "moby_0", mesh, skeleton,
                new Rac1Animation[] { animation }, new Rac1Texture[] { texture(2, 2) });
    }

    /**
     * A texture of the checks.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @return the texture
     */
    static Rac1Texture texture(int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        for (int index = 0; index < width * height; index++) {
            pixels[index * 4] = (byte) 0xFF;
            pixels[index * 4 + 1] = (byte) 0x40;
            pixels[index * 4 + 2] = (byte) 0x80;
            pixels[index * 4 + 3] = (byte) 0xC0;
        }
        return new Rac1Texture(width, height, pixels);
    }

    /**
     * The content of a cache of the checks.
     *
     * @param discKey the identity of the disc
     * @return the content
     */
    static Rac1CacheContent content(String discKey) {
        return new Rac1CacheContent(discKey, staticAsset(), animatedAsset(), animatedAsset(), 56);
    }
}
