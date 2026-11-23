package com.github.giannigr.racmc.client.shop;

import java.util.*;

/**
 * Render-thread owner for scene textures/targets. Backend registers each allocation immediately;
 * partial creation closes it, and resize/reload/logout/identity replacement discard the whole set.
 * No GPU allocation or fake artwork is performed by this foundation.
 */
public final class Rac1ShopResources implements AutoCloseable {
    @FunctionalInterface public interface Factory {
        void create(Rac1ShopResources owner) throws Exception;
    }
    private final List<AutoCloseable> owned = new ArrayList<>();
    private final Thread thread = Thread.currentThread();
    private boolean closed;
    public <T extends AutoCloseable> T own(T resource) {
        check();
        if (closed) throw new IllegalStateException("Shop resources already closed");
        owned.add(Objects.requireNonNull(resource));
        return resource;
    }
    public static Rac1ShopResources create(Factory factory) throws Exception {
        var result = new Rac1ShopResources();
        try { factory.create(result); return result; }
        catch (Exception | Error failure) {
            try { result.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void close() throws Exception {
        check();
        if (closed) return;
        closed = true;
        Exception failure = null;
        for (int i = owned.size() - 1; i >= 0; i--) {
            try { owned.get(i).close(); }
            catch (Exception next) {
                if (failure == null) failure = next; else failure.addSuppressed(next);
            }
        }
        owned.clear();
        if (failure != null) throw failure;
    }
    private void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Shop GPU owner requires render thread");
    }

    /** Backend-neutral lifecycle, with no shared cache-manager or entrypoint modifications. */
    public static final class Lifecycle implements AutoCloseable {
        private final Thread thread = Thread.currentThread();
        private Rac1ShopResources current;
        private String identity;
        private int width, height;
        public void ensure(String key, int width, int height, Factory factory) throws Exception {
            check();
            Objects.requireNonNull(key);
            if (width < 1 || height < 1 || width > 16384 || height > 16384)
                throw new IllegalArgumentException("Invalid shop viewport size");
            if (current != null && key.equals(identity) && this.width == width && this.height == height) return;
            close(); // old target cannot survive failed resize/cache replacement
            current = create(factory);
            identity = key; this.width = width; this.height = height;
        }
        public void reload() throws Exception { close(); }
        public void logout() throws Exception { close(); }
        @Override public void close() throws Exception {
            check();
            Rac1ShopResources previous = current;
            current = null; identity = null; width = height = 0;
            if (previous != null) previous.close();
        }
        private void check() {
            if (Thread.currentThread() != thread)
                throw new IllegalStateException("Shop lifecycle requires render thread");
        }
    }
}
