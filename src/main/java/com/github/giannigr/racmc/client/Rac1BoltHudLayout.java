package com.github.giannigr.racmc.client;

/** HUD-specific animation/locale policy, not part of the indexed-image decoder. */
public final class Rac1BoltHudLayout {
    private Rac1BoltHudLayout() {}
    public static int frame(long nowNanos, int uiHz) {
        return (int) (Math.floorMod(nowNanos / (1_000_000_000L / uiHz), 60L) / 2);
    }
    public static boolean dot(String language) {
        // Retail InitOnce maps PS2 German(4)->game3 and Italian(5)->game5.
        return language.startsWith("de") || language.startsWith("it");
    }
}
