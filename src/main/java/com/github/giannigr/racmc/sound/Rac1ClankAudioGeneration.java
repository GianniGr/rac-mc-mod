package com.github.giannigr.racmc.sound;

/** Independent optional publication fence. No shared audio catalog/cache invalidation. */
public final class Rac1ClankAudioGeneration {
    public record Snapshot(long generation,Rac1ClankAudioCatalog catalog) {}
    private long generation;
    private Rac1ClankAudioCatalog catalog;
    public synchronized long invalidate() { catalog=null;return ++generation; }
    public synchronized boolean publish(long token,Rac1ClankAudioCatalog complete) {
        if(token!=generation)return false;
        catalog=complete;return true;
    }
    /** Resource reload retires voices and fences pending imports, without rereading media if ready. */
    public synchronized void reload() { ++generation; }
    public synchronized Snapshot current() { return new Snapshot(generation,catalog); }
}
