package com.github.giannigr.racmc.effect;

/** Explicit soft-effect contract, independent of cutout crate/entity rendering. */
public record EffectMaterial(Blend blend, boolean depthTest, boolean depthWrite,
                             boolean bilinear, Address address) {
    public enum Blend { NORMAL, ADDITIVE }
    public enum Address { CLAMP, REPEAT }
    public static final EffectMaterial NORMAL = new EffectMaterial(Blend.NORMAL, true, false, true, Address.CLAMP);
    public static final EffectMaterial ADDITIVE = new EffectMaterial(Blend.ADDITIVE, true, false, true, Address.CLAMP);
    public static final EffectMaterial NORMAL_REPEAT = new EffectMaterial(Blend.NORMAL, true, false, true, Address.REPEAT);

    public EffectMaterial {
        if (blend == null || address == null) throw new IllegalArgumentException("Missing material state");
    }

    /** Original packed colour is R-low; GS RGB128 is neutral, alpha128 is full. */
    public static int rgba(int original) {
        int r = Math.min(255, (original & 255) * 255 / 128);
        int g = Math.min(255, ((original >>> 8) & 255) * 255 / 128);
        int b = Math.min(255, ((original >>> 16) & 255) * 255 / 128);
        int a = Math.min(255, ((original >>> 24) & 255) * 255 / 128);
        return r << 24 | g << 16 | b << 8 | a;
    }
}
