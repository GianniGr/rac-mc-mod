package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.asset.effect.EffectMeshAsset;
import com.github.giannigr.racmc.asset.effect.Rac1EffectMeshImporter;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import java.util.Arrays;
import java.util.List;

/** Both supported retail revisions, Novalis only. No substitute or persisted model data. */
public final class BombGloveArt {
    public static final int GLOVE_CLASS = 192;
    public static final int BOMB_CLASS = 121;
    public static final int AMMO_CLASS = 226;

    private BombGloveArt() {}

    public static Rac1AnimatedAsset glove(Rac1LevelAssets assets) throws AssetFormatException {
        checkSource(assets, GLOVE_CLASS);
        var entry = assets.coreIndex().gadget(GLOVE_CLASS);
        if (entry == null || entry.compressedSize() != 19180) {
            throw new AssetFormatException("Unsupported Novalis class-192 gadget");
        }
        var art = Rac1AssetExtractor.readGadgetBindAsset(assets, GLOVE_CLASS);
        var mesh = art.mesh();
        validate(mesh.positions(), mesh.normals(), mesh.uvs(), mesh.indices(), mesh.materials(),
                Arrays.asList(art.textures()), 1140, 1200);
        if (art.skeleton().jointCount() != 17) throw new AssetFormatException("Unexpected class-192 skeleton");
        for (int i = 0; i < mesh.weights().length; i++) {
            float weight = mesh.weights()[i];
            if (!Float.isFinite(weight) || weight < 0
                    || (weight > 0 && (mesh.joints()[i] < 0 || mesh.joints()[i] >= 17))) {
                throw new AssetFormatException("Invalid class-192 joint influence");
            }
        }
        return art;
    }

    public static EffectMeshAsset bomb(Rac1LevelAssets assets, String sha1) throws AssetFormatException {
        return rigid(assets, sha1, BOMB_CLASS, 5184, 186, 160);
    }

    public static EffectMeshAsset ammo(Rac1LevelAssets assets, String sha1) throws AssetFormatException {
        return rigid(assets, sha1, AMMO_CLASS, 8000, 218, 240);
    }

    private static EffectMeshAsset rigid(Rac1LevelAssets assets, String sha1, int id,
                                         int blobSize, int vertices, int triangles) throws AssetFormatException {
        checkSource(assets, id);
        var row = assets.coreIndex().mobyClass(id);
        if (row.offset() <= 0 || assets.coreIndex().blockSize(row.offset()) != blobSize) {
            throw new AssetFormatException("Unexpected Novalis moby extent");
        }
        var art = Rac1EffectMeshImporter.read(assets, sha1, id);
        var mesh = art.mesh();
        validate(mesh.positions(), mesh.normals(), mesh.uvs(), mesh.indices(), mesh.materials(),
                art.textures(), vertices, triangles);
        return art;
    }

    private static void checkSource(Rac1LevelAssets assets, int id) throws AssetFormatException {
        var row = assets.coreIndex().mobyClass(id);
        if (assets.level() != 1 || row == null || row.textureCount() != 1) {
            throw new AssetFormatException("Missing curated Novalis class " + id);
        }
        int slot = row.textureSlots()[0] & 255;
        if (slot >= assets.coreIndex().mobyTextures().size()) throw new AssetFormatException("Missing moby texture");
        var texture = assets.coreIndex().mobyTextures().get(slot);
        if (texture.width() != 64 || texture.height() != 64) {
            throw new AssetFormatException("Unsupported Novalis moby texture size");
        }
    }

    static void validate(float[] positions, float[] normals, float[] uvs, int[] indices,
                         int[] materials, List<Rac1Texture> textures, int vertices, int triangles)
            throws AssetFormatException {
        if (positions.length != vertices * 3 || normals.length != vertices * 3 || uvs.length != vertices * 2
                || indices.length != triangles * 3 || materials.length != triangles
                || textures.size() != 1 || !finite(positions) || !finite(normals) || !finite(uvs)) {
            throw new AssetFormatException("Unsupported Novalis Bomb Glove mesh layout");
        }
        for (int index : indices) if (index < 0 || index >= vertices) {
            throw new AssetFormatException("Bomb Glove triangle outside vertex buffer");
        }
        for (int material : materials) if (material != 0) {
            throw new AssetFormatException("Unsupported Bomb Glove material");
        }
        var texture = textures.getFirst();
        if (texture.width() != 64 || texture.height() != 64) throw new AssetFormatException("Unsupported texture");
        byte[] pixels = texture.pixels();
        for (int i = 0; i < pixels.length; i += 4) if (pixels[i] != (byte) 255) {
            throw new AssetFormatException("Unexpected alpha in opaque Bomb Glove asset");
        }
    }

    private static boolean finite(float[] values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
