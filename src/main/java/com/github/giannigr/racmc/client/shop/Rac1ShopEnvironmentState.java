package com.github.giannigr.racmc.client.shop;

/**
 * Shop-local CPU interpretation of the original moby environment-pass GS state.
 * Not a renderer or a claim about scene-selected ZBUF/fog/framebuffer state.
 */
public record Rac1ShopEnvironmentState(long alpha, long test, long tex1, long clamp) {
    public static final long ORIGINAL_ALPHA = 0x8000000044L;
    public static final long ORIGINAL_TEST = 0x5360bL;

    public Rac1ShopEnvironmentState {
        if (alpha != ORIGINAL_ALPHA || test != ORIGINAL_TEST || clamp != 5
                || (tex1 & 0xffffffffL) != 0x120L)
            throw new IllegalArgumentException("Unsupported original shop environment state");
        // Upper TEX1 K bits are carried through, not interpreted as a selected LOD.
        // MXL=0, so no mip chain is consumed by this bounded contract.
    }

    /** After alpha testing, before depth testing. RGB_ONLY does not discard RGB. */
    public record WriteMask(boolean rgb, boolean alpha, boolean depth) {}

    public WriteMask alphaTest(int modulatedGsAlpha) {
        if (modulatedGsAlpha < 0 || modulatedGsAlpha > 255)
            throw new IllegalArgumentException("GS alpha byte required");
        boolean pass = modulatedGsAlpha >= 0x60;
        return new WriteMask(true, pass, pass);
    }

    public int alphaReference() { return 0x60; }
    public boolean linearMagnification() { return true; }
    public boolean linearMinification() { return true; }
    public boolean clampS() { return true; }
    public boolean clampT() { return true; }
    public boolean mipmaps() { return false; }
    public boolean destinationAlphaTest() { return false; }
    public boolean depthGreaterOrEqual() { return true; }
}
