package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.disc.*;
import java.util.ArrayList;
import java.util.List;

/** Generic static-moby read. Procedural controllers with offset zero are not mesh assets. */
public final class Rac1EffectMeshImporter {
    private Rac1EffectMeshImporter() {}

    public static EffectMeshAsset read(Rac1LevelAssets assets, String sha1, int classId)
            throws AssetFormatException {
        Rac1ClassEntry entry = assets.coreIndex().mobyClass(classId);
        if (entry == null || entry.offset() <= 0)
            throw new AssetFormatException("No static mesh blob for effect class " + classId);
        try {
            MobyClassReader reader = MobyClassReader.of(assets.classBlob(entry.offset()));
            if (reader.jointCount() != 0)
                throw new AssetFormatException("Skinned effect requires separate animated importer");
            MobyClassReader.Mesh source = reader.readMesh();
            float[] positions = source.positions().clone(), normals = source.normals().clone();
            // MobyClassReader already applies class scale, just as the crate importer does.
            for (int at = 0; at < positions.length; at += 3) {
                float y = positions[at + 1];
                positions[at + 1] = positions[at + 2];
                positions[at + 2] = -y;
                y = normals[at + 1];
                normals[at + 1] = normals[at + 2];
                normals[at + 2] = -y;
            }
            List<Rac1Texture> textures = new ArrayList<>();
            for (int slot = 0; slot < entry.textureCount(); slot++) {
                int id = entry.textureSlots()[slot] & 255;
                if (id >= assets.coreIndex().mobyTextures().size())
                    throw new AssetFormatException("Effect moby texture outside table");
                textures.add(Rac1TextureReader.read(assets.coreData(), assets.coreIndex().texturesBase(),
                        assets.gsRam(), assets.coreIndex().mobyTextures().get(id)));
            }
            return new EffectMeshAsset(new EffectAssetRef(sha1, assets.level(),
                    EffectAssetRef.Bank.MOBY, classId), new Rac1Mesh(positions, normals, source.uvs(),
                    source.indices(), source.materials()), textures);
        } catch (DiscFormatException exception) {
            throw new AssetFormatException(exception.getMessage(), exception);
        }
    }
}
