package com.github.giannigr.racmc.client.shop;

/** Original GS register/material facts for the2D monitor passes, not shared HUD/FX state.
 * 0x44=(Cs-Cd)*As+Cd;0x64=(Cs-Cd)*FIX+Cd;0x68=Cs*FIX+Cd. */
public final class Rac1ShopMaterials {
    private Rac1ShopMaterials() {}
    public enum Filter { POINT, LINEAR }
    public enum Address { CLAMP, REPEAT }
    public record Sampler(Filter filter, Address address) {}
    public enum Blend { SOURCE_ALPHA, FIXED_MIX, FIXED_ADD }
    public enum Pass {
        LED(Filter.POINT, Address.CLAMP, Blend.SOURCE_ALPHA),
        LED_NOISE(Filter.POINT, Address.REPEAT, Blend.FIXED_ADD),
        PANEL_NOISE(Filter.LINEAR, Address.REPEAT, Blend.FIXED_ADD),
        SCAN_BAR(Filter.LINEAR, Address.REPEAT, Blend.SOURCE_ALPHA),
        GLASS(Filter.LINEAR, Address.CLAMP, Blend.SOURCE_ALPHA),
        FONT(Filter.LINEAR, Address.CLAMP, Blend.SOURCE_ALPHA),
        SCREEN_COPY(Filter.POINT, Address.CLAMP, Blend.FIXED_MIX);
        private final Sampler sampler; private final Blend blend;
        Pass(Filter filter, Address address, Blend blend) {
            this.sampler = new Sampler(filter, address); this.blend = blend;
        }
        public Sampler sampler() { return sampler; }
        public Blend blend() { return blend; }
    }
    public static Blend blend(int gsAlphaLowByte) {
        return switch (gsAlphaLowByte) {
            case 0x44 -> Blend.SOURCE_ALPHA;
            case 0x64 -> Blend.FIXED_MIX;
            case 0x68 -> Blend.FIXED_ADD;
            default -> throw new IllegalArgumentException("Unimplemented original GS blend equation");
        };
    }
    /** Reference RGB equation for source-independent tests; values/FIX remain unclamped here. */
    public static double rgb(Blend blend, double source, double destination, double sourceAlpha, int fixed) {
        if (fixed < 0 || fixed > 128) throw new IllegalArgumentException("Invalid GS FIX");
        return switch (blend) {
            case SOURCE_ALPHA -> (source - destination) * sourceAlpha + destination;
            case FIXED_MIX -> (source - destination) * (fixed / 128.0) + destination;
            case FIXED_ADD -> source * (fixed / 128.0) + destination;
        };
    }
}
