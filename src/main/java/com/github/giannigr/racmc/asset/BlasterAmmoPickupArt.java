package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.asset.effect.EffectMeshAsset;
import com.github.giannigr.racmc.asset.effect.Rac1EffectMeshImporter;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;

/** Original level-1 world pickup, not a vendor emblem or shop-rendering dependency. */
public final class BlasterAmmoPickupArt {
    private static final int PICKUP_CLASS = 1006;

    private BlasterAmmoPickupArt() {}

    public static EffectMeshAsset read(Rac1LevelAssets assets, String sha1) throws AssetFormatException {
        var pickup = Rac1EffectMeshImporter.read(assets, sha1, PICKUP_CLASS);
        if (pickup.mesh().triangleCount() == 0 || pickup.textures().isEmpty()
                || pickup.textures().size() > 8
                || pickup.textures().stream().anyMatch(t -> t.width() > 1024 || t.height() > 1024))
            throw new AssetFormatException("Missing or unsupported Blaster Ammo pickup mesh/texture");
        return pickup;
    }
}
