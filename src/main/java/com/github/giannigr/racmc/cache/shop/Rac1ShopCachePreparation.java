package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopAssets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** One in-flight sidecar job, no queued selections; generation changes discard stale completions. */
public final class Rac1ShopCachePreparation {
    public record Selection(Path cacheRoot, Path discImage, String mainDiscKey, int language) {
        public Selection {
            cacheRoot = cacheRoot.toAbsolutePath().normalize();
            discImage = discImage.toAbsolutePath().normalize();
            if (mainDiscKey == null || !mainDiscKey.matches(".+\\|[0-9a-f]{40}")
                    || !Set.of(0,2,3,4,5).contains(language))
                throw new IllegalArgumentException("Invalid selected-disc shop identity.");
        }
        public String sha1() { return mainDiscKey.substring(mainDiscKey.length()-40); }
    }
    @FunctionalInterface public interface Loader { Rac1ShopAssets load(Selection selection) throws Exception; }

    private final Executor executor;
    private final Loader loader;
    private Selection selected;
    private long generation, jobGeneration;
    private CompletableFuture<Rac1ShopAssets> job;
    private Rac1ShopAssets imported;
    private String error;

    public Rac1ShopCachePreparation(Executor executor, Loader loader) {
        this.executor = Objects.requireNonNull(executor);
        this.loader = Objects.requireNonNull(loader);
    }

    /** Null deselects. A failure remains terminal for that generation; no per-tick retry loop. */
    public synchronized void update(Selection selection) {
        if (!Objects.equals(selected,selection)) {
            generation++; selected=selection; imported=null; error=null;
        }
        if (job != null && job.isDone()) {
            try {
                var result = job.join();
                if (jobGeneration == generation && selected != null) {
                    if (result == null || !result.discSha1().equals(selected.sha1())
                            || result.language() != selected.language())
                        throw new IllegalStateException("Prepared shop resources have the wrong identity.");
                    imported=result;
                }
            } catch (CompletionException | IllegalStateException failure) {
                if (jobGeneration == generation) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    error=cause.getClass().getSimpleName()+": "+cause.getMessage();
                }
            } finally { job=null; }
        }
        if (selected != null && job == null && imported == null && error == null) {
            Selection source=selected; jobGeneration=generation;
            try {
                job=CompletableFuture.supplyAsync(() -> {
                    try { return loader.load(source); }
                    catch (Exception failure) { throw new CompletionException(failure); }
                },executor);
            } catch (RuntimeException failure) { error=failure.getClass().getSimpleName()+": "+failure.getMessage(); }
        }
    }

    /** Also invalidates A -> no selection -> A, even if the old worker has not finished. */
    public synchronized void invalidate() { generation++; selected=null; imported=null; error=null; }
    public synchronized Optional<Rac1ShopAssets> importedResources() { return Optional.ofNullable(imported); }
    public synchronized Optional<String> error() { return Optional.ofNullable(error); }
}
