package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.effect.EffectMeshAsset;
import java.util.Objects;

/** Additional local scene models. Body/logo/weapon remain references to the main cache. */
public record Rac1ShopModels(Rac1AnimatedAsset salesman, Rac1AnimatedAsset backdrop,
                            Rac1AnimatedAsset popup, EffectMeshAsset ammo) {
    public Rac1ShopModels {
        Objects.requireNonNull(salesman);
        Objects.requireNonNull(backdrop);
        Objects.requireNonNull(popup);
        Objects.requireNonNull(ammo);
        if (salesman.classNumber() != 12 || backdrop.classNumber() != 13
                || popup.classNumber() != 1137 || ammo.source().sourceId() != 1006) {
            throw new IllegalArgumentException("Incorrect original shop model identities");
        }
        require(salesman, 10);
        require(backdrop, 1);
        require(popup, 2);
        if (ammo.mesh().vertexCount() == 0 || ammo.textures().isEmpty())
            throw new IllegalArgumentException("Missing rigid Blaster ammo geometry/textures");
    }

    private static void require(Rac1AnimatedAsset asset, int sequences) {
        // Popup 1137 has original untextured packets (material -1), not missing artwork.
        // Its eventual backend must support those packets; an atlas must not be invented.
        if (asset.mesh().vertexCount() == 0 || (asset.classNumber() != 1137 && asset.textures().length == 0))
            throw new IllegalArgumentException("Missing shop model geometry/textures: " + asset.classNumber());
        for (int i = 0; i < sequences; i++) {
            String name = "sequence_" + i;
            boolean found = false;
            for (var animation : asset.animations())
                found |= animation.name().equals(name) && animation.keyCount() > 0;
            if (!found) throw new IllegalArgumentException("Missing shop model " + asset.classNumber() + " " + name);
        }
    }
}
