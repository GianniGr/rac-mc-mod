package com.github.giannigr.racmc.sound;

/** Publication fence for asynchronous imports. Invalidation cannot resurrect a stale catalog. */
public final class Rac1AudioGeneration {
    private long generation;
    private Rac1AudioCatalog catalog;
    public synchronized long invalidate() { catalog = null; return ++generation; }
    public synchronized boolean publish(long token, Rac1AudioCatalog complete) {
        if (token != generation) return false;
        catalog = complete;
        return true;
    }
    public synchronized Rac1AudioCatalog current() { return catalog; }
}
