package com.github.giannigr.racmc.effect;

/** CPU/GPU generation ownership seam. Call on the client/render thread; completion from a worker
 * must first be marshalled to that thread. Stale results are closed rather than attached.
 * Replacing a catalog always clears old instances before releasing its resource owner. */
public final class EffectCatalogGeneration<T extends AutoCloseable> implements AutoCloseable {
    private long generation;
    private T current;
    private final Runnable clearInstances;
    public EffectCatalogGeneration(Runnable clearInstances) {
        this.clearInstances = java.util.Objects.requireNonNull(clearInstances);
    }
    public long generation() { return generation; }
    public T current() { return current; }
    public long invalidate() throws Exception {
        generation++;
        clearInstances.run();
        T old = current;
        current = null;
        if (old != null) old.close();
        return generation;
    }
    public boolean attach(long expectedGeneration, T value) throws Exception {
        java.util.Objects.requireNonNull(value);
        if (expectedGeneration != generation) {
            value.close();
            return false;
        }
        if (current != null) {
            value.close();
            return false;
        }
        current = value;
        return true;
    }
    @Override public void close() throws Exception { invalidate(); }
}
