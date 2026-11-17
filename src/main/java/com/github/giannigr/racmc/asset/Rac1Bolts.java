package com.github.giannigr.racmc.asset;

import java.util.List;

/** Static original moby assets; reuse the cache's keyed static-moby representation, not crate fit. */
public final class Rac1Bolts {
    private Rac1Bolts() {}
    public static List<Rac1CrateDefinition> definitions() {
        return List.of(new Rac1CrateDefinition("bolt", 13),
                new Rac1CrateDefinition("bolt_hexnut", 14),
                new Rac1CrateDefinition("bolt_starnut", 15),
                new Rac1CrateDefinition("bolt_circlenut", 16));
    }
    public static String key(int value) {
        return switch (value) {
            case 5 -> "bolt_hexnut";
            case 20 -> "bolt_starnut";
            case 50 -> "bolt_circlenut";
            default -> "bolt";
        };
    }
    public static float scale(int classNumber) {
        return switch (classNumber) { case 14 -> .70F; case 15 -> .75F; case 16 -> .60F; default -> .65F; };
    }
}
