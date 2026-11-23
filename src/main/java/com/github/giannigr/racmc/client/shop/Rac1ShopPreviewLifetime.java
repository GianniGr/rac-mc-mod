package com.github.giannigr.racmc.client.shop;

/** Fail-closed preview lifetime snapshot; no authority to freeze/protect a world or buy items. */
record Rac1ShopPreviewLifetime(boolean alive,boolean spectator,boolean sameLevel,boolean loaded,
        boolean sameVendor,boolean inReach,boolean sameAssets,boolean sameMedia) {
    boolean open() {
        return alive && !spectator && sameLevel && loaded && sameVendor && inReach && sameAssets && sameMedia;
    }
}
