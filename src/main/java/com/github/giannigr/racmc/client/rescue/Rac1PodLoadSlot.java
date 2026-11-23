package com.github.giannigr.racmc.client.rescue;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Client-thread publication slot. Obsolete jobs retain the one slot until they actually finish. */
final class Rac1PodLoadSlot<K, Cpu, Ready> {
    private record Flight<K, T>(K key, CompletableFuture<T> result) {}

    private K requested;
    private boolean attempted;
    private Flight<K, Cpu> inFlight;
    private Ready ready;

    Ready lookup(Predicate<K> current) {
        return ready != null && current.test(requested) ? ready : null;
    }

    void tick(K key, Predicate<K> current, Function<K, CompletableFuture<Cpu>> submit,
              Function<Cpu, Ready> publish, Consumer<Ready> release, Consumer<Throwable> failed) {
        if (!Objects.equals(requested, key)) {
            if (ready != null) release.accept(ready);
            ready = null;
            requested = key;
            attempted = false;
        }
        if (inFlight != null) {
            if (!inFlight.result.isDone()) return;
            var complete = inFlight;
            inFlight = null;
            if (key != null && Objects.equals(complete.key, key) && current.test(key)) {
                try {
                    Ready uploaded = publish.apply(complete.result.join());
                    // The verifier runs concurrently. Recheck after texture creation as well.
                    if (current.test(key)) ready = uploaded;
                    else release.accept(uploaded);
                } catch (CompletionException failure) {
                    if (current.test(key)) failed.accept(failure.getCause());
                } catch (RuntimeException | LinkageError failure) {
                    if (current.test(key)) failed.accept(failure);
                }
            }
        }
        if (key != null && !attempted && current.test(key)) {
            attempted = true;
            try {
                inFlight = new Flight<>(key, submit.apply(key));
            } catch (RuntimeException failure) {
                failed.accept(failure);
            }
        }
    }
}
