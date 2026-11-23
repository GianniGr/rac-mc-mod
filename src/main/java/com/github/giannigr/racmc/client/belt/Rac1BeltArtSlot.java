package com.github.giannigr.racmc.client.belt;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** One in-flight art preparation. Never publish a job after its verified selection was replaced. */
final class Rac1BeltArtSlot<K, P, A> {
    private K attempted;
    private K loading;
    private CompletableFuture<P> job;
    private K installed;
    private A art;

    A get(K current) {
        return current != null && current.equals(installed) ? art : null;
    }

    void tick(K current, Function<K, CompletableFuture<P>> load, BiFunction<K, P, A> install,
              Consumer<A> close, Consumer<RuntimeException> failed) {
        if (art != null && !Objects.equals(installed, current)) {
            close.accept(art);
            installed = null;
            art = null;
        }
        if (job != null && job.isDone()) {
            var finished = job;
            var forKey = loading;
            job = null;
            loading = null;
            if (Objects.equals(forKey, current)) {
                try {
                    art = Objects.requireNonNull(install.apply(forKey, finished.join()));
                    installed = forKey;
                } catch (RuntimeException failure) {
                    failed.accept(failure);
                }
            }
        }
        if (current == null) attempted = null;
        if (job == null && current != null && !current.equals(attempted)) {
            attempted = current;
            loading = current;
            try {
                job = Objects.requireNonNull(load.apply(current));
            } catch (RuntimeException failure) {
                loading = null;
                failed.accept(failure);
            }
        }
    }
}
