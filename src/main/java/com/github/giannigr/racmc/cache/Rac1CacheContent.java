package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;

/**
 * The content of the cache of the assets.
 *
 * <p>The cache holds the assets that were converted out of the disc image of the game: one piece
 * of scenery and one animated character, with their textures. It also holds the identity of the
 * disc the assets were read from, so that a cache of another disc is never used.
 */
public final class Rac1CacheContent {

    /** The identity of the disc the assets were read from. */
    private final String discKey;

    /** The static asset of the cache. */
    private final Rac1StaticAsset staticAsset;

    /** The animated asset of the cache. */
    private final Rac1AnimatedAsset animatedAsset;

    /**
     * Creates the content of a cache.
     *
     * @param discKey the identity of the disc the assets were read from
     * @param staticAsset the static asset
     * @param animatedAsset the animated asset
     */
    public Rac1CacheContent(String discKey, Rac1StaticAsset staticAsset,
                            Rac1AnimatedAsset animatedAsset) {
        this.discKey = discKey;
        this.staticAsset = staticAsset;
        this.animatedAsset = animatedAsset;
    }

    /**
     * The identity of the disc the assets were read from.
     *
     * @return the identity
     */
    public String discKey() {
        return discKey;
    }

    /**
     * The static asset of the cache.
     *
     * @return the asset
     */
    public Rac1StaticAsset staticAsset() {
        return staticAsset;
    }

    /**
     * The animated asset of the cache.
     *
     * @return the asset
     */
    public Rac1AnimatedAsset animatedAsset() {
        return animatedAsset;
    }
}
