package com.github.giannigr.racmc.asset.effect;

/** CPU-side original bank generation. Particle and FX numeric IDs never alias. */
public record EffectBanks(EffectTextureBank particles, EffectTextureBank fx,
                          ParticleFrameLists definitions) {
    public EffectBanks {
        if (particles.source().bank() != EffectAssetRef.Bank.PARTICLE
                || fx.source().bank() != EffectAssetRef.Bank.FX
                || !particles.source().discSha1().equals(fx.source().discSha1())
                || particles.source().sourceLevel() != fx.source().sourceLevel()
                || particles.entries().size() != definitions.textureCount())
            throw new IllegalArgumentException("Inconsistent effect banks");
    }
}
