package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The content of the cache of the assets.
 *
 * <p>The cache holds the assets that were converted out of the disc image of the game: one piece
 * of scenery, one animated character and the weapon the character holds. It also holds the
 * crates of the game, which are placed as the blocks of the mod. It also holds the identity of
 * the disc the assets were read from, so that a cache of another disc is never used.
 */
public final class Rac1CacheContent {

    /** The identity of the disc the assets were read from. */
    private final String discKey;

    /** The static asset of the cache. */
    private final Rac1StaticAsset staticAsset;

    /** The animated asset of the cache. */
    private final Rac1AnimatedAsset animatedAsset;

    /** The weapon of the cache. */
    private final Rac1AnimatedAsset blaster;

    /** The joint of the skeleton the weapon is held on. */
    private final int handJoint;

    /** The crates of the cache, by the key they are known by. */
    private final Map<String, Rac1CrateAsset> crates;

    /**
     * Creates the content of a cache.
     *
     * @param discKey the identity of the disc the assets were read from
     * @param staticAsset the static asset
     * @param animatedAsset the animated asset
     * @param blaster the weapon
     * @param handJoint the joint of the skeleton the weapon is held on
     */
    public Rac1CacheContent(String discKey, Rac1StaticAsset staticAsset,
                            Rac1AnimatedAsset animatedAsset, Rac1AnimatedAsset blaster,
                            int handJoint) {
        this(discKey, staticAsset, animatedAsset, blaster, handJoint, Map.of());
    }

    /**
     * Creates the content of a cache.
     *
     * @param discKey the identity of the disc the assets were read from
     * @param staticAsset the static asset
     * @param animatedAsset the animated asset
     * @param blaster the weapon
     * @param handJoint the joint of the skeleton the weapon is held on
     * @param crates the crates of the game
     */
    public Rac1CacheContent(String discKey, Rac1StaticAsset staticAsset,
                            Rac1AnimatedAsset animatedAsset, Rac1AnimatedAsset blaster,
                            int handJoint, Map<String, Rac1CrateAsset> crates) {
        this.discKey = discKey;
        this.staticAsset = staticAsset;
        this.animatedAsset = animatedAsset;
        this.blaster = blaster;
        this.handJoint = handJoint;
        this.crates = Collections.unmodifiableMap(new LinkedHashMap<>(crates));
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

    /**
     * The weapon of the cache.
     *
     * @return the asset
     */
    public Rac1AnimatedAsset blaster() {
        return blaster;
    }

    /**
     * The joint of the skeleton the weapon is held on.
     *
     * @return the number of the joint
     */
    public int handJoint() {
        return handJoint;
    }

    /**
     * The crates of the cache, by the key they are known by.
     *
     * @return the crates, in the order they were converted
     */
    public Map<String, Rac1CrateAsset> crates() {
        return crates;
    }

    /**
     * The crate with the given key.
     *
     * @param key the key of the crate
     * @return the crate, or {@code null} when the cache does not hold it
     */
    public Rac1CrateAsset crate(String key) {
        return crates.get(key);
    }
}
