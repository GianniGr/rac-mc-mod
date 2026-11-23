package com.github.giannigr.racmc.client.shop;

/**
 * Shop-local algebraic metal VU0/EE program, not a mesh decoder, renderer or popup shader.
 * Pinned ReRAC moby_skinning_lighting §§5/6/9; primary both-disc EE driver confirms
 * quarter-light scaling,0x70 RGB bias, shine alpha and direct palette vertex fields.
 * All lighting and E inputs must come from the original scene, not guessed defaults.
 * Java float arithmetic is not a cycle/bit-accurate PS2 VU emulator.
 */
public final class Rac1ShopMetalProgram {
    private Rac1ShopMetalProgram() {}

    /** Signed4.12 ST after ftoi12/arithmetic-shift/halfword pack; raw RGBA bytes, NOT MC alpha. */
    public record Sample(short s, short t, int red, int green, int blue, int alpha) {
        public float textureS() { return s/4096f; }
        public float textureT() { return t/4096f; }
    }

    /**
     * n is the unnormalised SKIN-transformed normal in original model axes.
     * directions/colors each contain3 xyz light vectors; E contains three xyz basis
     * vectors, so e=E0*n.x+E1*n.y+E2*n.z. No inverse-transpose or normalization here.
     * Metal has no baked multiplier stream/back-light term/normal-light rsqrt.
     */
    public static Sample evaluate(float nx, float ny, float nz, float[] directions,
                                  float[] colors, float[] environmentBasis, int shineAlpha) {
        finite(nx); finite(ny); finite(nz);
        vectorBank(directions); vectorBank(colors); vectorBank(environmentBasis);
        if (shineAlpha < 0 || shineAlpha > 128) throw new IllegalArgumentException("Invalid original shine alpha");
        long[] rgb = {112,112,112};
        for (int light = 0; light < 3; light++) {
            int at=light*3;
            float dot=Math.max(0,directions[at]*nx+directions[at+1]*ny+directions[at+2]*nz);
            finite(dot);
            for (int channel = 0; channel < 3; channel++) {
                float color=colors[at+channel];
                if (color < 0) throw new IllegalArgumentException("Negative original light color");
                // The biased VU additions truncate each positive light contribution separately.
                float contribution=32f*color*dot;
                finite(contribution);
                if (contribution >= 0x1p31f) throw new IllegalArgumentException("Metal color outside bounded integer domain");
                rgb[channel]+=(int)contribution;
            }
        }
        float ex=environmentBasis[0]*nx+environmentBasis[3]*ny+environmentBasis[6]*nz;
        float ey=environmentBasis[1]*nx+environmentBasis[4]*ny+environmentBasis[7]*nz;
        return new Sample(st(ex),st(ey),(int)(rgb[0]&255),(int)(rgb[1]&255),(int)(rgb[2]&255),shineAlpha);
    }

    /** Original gate uses view depth-minus-radius in packed units, not Minecraft distance. */
    public static int shineAlpha(int originalShineDistance, int depthMinusRadiusPacked) {
        if (originalShineDistance < 0 || originalShineDistance > 255)
            throw new IllegalArgumentException("Invalid original shine distance");
        if (originalShineDistance == 0) return 0; // source gate disables the metal pass entirely
        long remaining=originalShineDistance*1024L-depthMinusRadiusPacked;
        if (remaining <= 0) return 0;
        return (int)Math.min(128,remaining>>7);
    }

    private static short st(float component) {
        float fixed=(component+1)*4096f;
        finite(fixed);
        if (fixed < -0x1p31f || fixed >= 0x1p31f)
            throw new IllegalArgumentException("Metal ST outside bounded integer domain");
        // Truncate toward zero FIRST, then signed shift, then low halfword (not clamp).
        return (short)(((int)fixed)>>1);
    }
    private static void vectorBank(float[] bank) {
        if (bank == null || bank.length != 9) throw new IllegalArgumentException("Expected three original xyz vectors");
        for (float value : bank) finite(value);
    }
    private static void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite original metal input");
    }
}
