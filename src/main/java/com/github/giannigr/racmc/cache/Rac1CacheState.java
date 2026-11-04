package com.github.giannigr.racmc.cache;

/**
 * The state of the cache of the assets.
 *
 * <p>The state decides whether the game may be entered. Only {@link #READY} allows it: a cache
 * that is being built or that failed may not be used, because the assets of the game would be
 * missing or only half there.
 */
public enum Rac1CacheState {

    /** There is no cache for the disc that is configured. */
    MISSING,

    /** The cache is being built. The game may not be entered yet. */
    BUILDING,

    /** The cache is there and holds both assets. */
    READY,

    /** The cache could not be built or the cache that is there is damaged. */
    FAILED
}
