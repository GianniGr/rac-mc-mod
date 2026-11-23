package com.github.giannigr.racmc.client.shop;

/**
 * Bounded CPU GS pixel algebra for the proposed shop-local consumer. No GPU/readiness binding.
 * RGB is in original display-byte space; destination alpha is NOT source-over composited.
 * Does not emulate raster coverage, dither, texture interpolation or GS float precision.
 */
public final class Rac1ShopGsPixelProgram {
    private Rac1ShopGsPixelProgram() {}
    public record Rgba(int r,int g,int b,int a) {
        public Rgba { byteValue(r); byteValue(g); byteValue(b); byteValue(a); }
    }
    public record Result(Rgba color,int depth24) {}

    public static Rgba modulate(Rgba texture,Rgba vertex) {
        return new Rgba(Math.min(255,texture.r()*vertex.r()>>7),Math.min(255,texture.g()*vertex.g()>>7),
                Math.min(255,texture.b()*vertex.b()>>7),Math.min(255,texture.a()*vertex.a()>>7));
    }

    /** ALPHA0x44, TEST0x5360b, FBA0, COLCLAMP1; frameMask uses GS AABBGGRR bit positions. */
    public static Result fragment(Rgba source,Rgba destination,int sourceZ,int destinationZ,
                                  int frameMask,boolean zMasked) {
        depth(sourceZ); depth(destinationZ);
        if (sourceZ<destinationZ) return new Result(destination,destinationZ);
        boolean alphaPass=source.a()>=96;
        int r=blend(source.r(),destination.r(),source.a());
        int g=blend(source.g(),destination.g(),source.a());
        int b=blend(source.b(),destination.b(),source.a());
        // RGB_ONLY fails preserve destination alpha even though RGB is blended.
        int a=alphaPass ? source.a() : destination.a();
        return new Result(new Rgba(mask(r,destination.r(),frameMask),
                mask(g,destination.g(),frameMask>>>8),mask(b,destination.b(),frameMask>>>16),
                mask(a,destination.a(),frameMask>>>24)),alphaPass && !zMasked ? sourceZ:destinationZ);
    }
    private static int blend(int source,int destination,int alpha) {
        // Arithmetic shift, not Java division: negative products round toward -infinity.
        return Math.max(0,Math.min(255,(((source-destination)*alpha)>>7)+destination));
    }
    private static int mask(int source,int destination,int mask) {
        mask&=255; return (source&~mask)|(destination&mask);
    }
    private static void byteValue(int v) {
        if (v<0 || v>255) throw new IllegalArgumentException("Expected GS byte");
    }
    private static void depth(int v) {
        if (v<0 || v>0xffffff) throw new IllegalArgumentException("Expected original unsigned Z24");
    }
}
