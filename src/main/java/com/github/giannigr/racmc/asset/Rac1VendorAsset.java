package com.github.giannigr.racmc.asset;

import java.util.Objects;

/** The original Gadgetron kiosk and its separately spawned logo, not the menu salesman. */
public record Rac1VendorAsset(Rac1AnimatedAsset body, Rac1AnimatedAsset hologram) {
    // Class identities: OpenRAC rac1-decomp tools/extract/moby_classes.tsv.
    public static final int BODY_CLASS = 11;
    public static final int HOLOGRAM_CLASS = 1143;

    public Rac1VendorAsset {
        Objects.requireNonNull(body);
        Objects.requireNonNull(hologram);
        if (body.classNumber() != BODY_CLASS || hologram.classNumber() != HOLOGRAM_CLASS) {
            throw new IllegalArgumentException("The vendor requires classes 11 and 1143.");
        }
        closedSequence(body);
        if (hologram.skeleton().jointCount() != 3) {
            throw new IllegalArgumentException("The vendor logo requires three joints.");
        }
    }

    public static Rac1Animation closedSequence(Rac1AnimatedAsset body) {
        for (Rac1Animation animation : body.animations()) {
            if (animation.name().equals("sequence_0") && animation.keyCount() > 0) {
                return animation;
            }
        }
        throw new IllegalArgumentException("The vendor has no closed sequence_0.");
    }
}
