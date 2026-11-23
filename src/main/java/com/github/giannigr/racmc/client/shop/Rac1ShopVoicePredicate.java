package com.github.giannigr.racmc.client.shop;

import java.util.Objects;

/** Proven predicate algebra only. No assumed cursor units, scaling implementation or MC-clock adapter. */
public final class Rac1ShopVoicePredicate {
    @FunctionalInterface public interface Scaling {
        float apply(float value,int selector);
    }
    private Rac1ShopVoicePredicate() {}

    /**
     * Cursor is the source getter result; field58/field5c are the two Moby floats,
     * not asserted to be seconds, speed or delta. The caller must supply a verified
     * scaler; there is deliberately no identity/rounding default or runtime caller.
     */
    public static boolean reached(float threshold,float cursor,float field58,float field5c,Scaling scaling) {
        Objects.requireNonNull(scaling);
        float residual=scaling.apply(cursor-threshold,4);
        float step=scaling.apply(field58*field5c,4);
        // Ordered inclusive lower check, ordered strict upper check, as in both functions.
        return threshold<=cursor && residual<step;
    }
}
