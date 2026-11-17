package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.List;

/** Original static moby geometry, without crate registration or unit-fitting normalization. */
public record EffectMeshAsset(EffectAssetRef source, Rac1Mesh mesh, List<Rac1Texture> textures) {
    public EffectMeshAsset {
        if (source.bank() != EffectAssetRef.Bank.MOBY) throw new IllegalArgumentException("Not a moby");
        textures = List.copyOf(textures);
    }
}
