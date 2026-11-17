package com.github.giannigr.racmc.asset.effect;

/** Source IDs are scoped by disc, curated source level and bank, never by MC dimension. */
public record EffectAssetRef(String discSha1, int sourceLevel, Bank bank, int sourceId) {
    public enum Bank { PARTICLE, FX, MOBY, OVERLAY }

    public EffectAssetRef {
        if (discSha1 == null || !discSha1.matches("[0-9a-f]{40}") || sourceLevel < 0
                || sourceLevel > 18 || bank == null || sourceId < 0 || sourceId > 65535)
            throw new IllegalArgumentException("Invalid effect source reference");
    }
}
