package com.github.giannigr.racmc.serverassets;

import com.github.giannigr.racmc.cache.Rac1CacheImporter;
import com.github.giannigr.racmc.cache.Rac1CacheStore;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static com.github.giannigr.racmc.serverassets.Rac1WrenchDataStatus.Origin.*;
import static com.github.giannigr.racmc.serverassets.Rac1WrenchDataStatus.State.*;

/** Pure-Java asynchronous local validation/cache/import. No client types or global startup gate. */
public final class Rac1WrenchDataProvider implements AutoCloseable {
    /** Bound this optional catalog's cache read before the existing in-memory codec allocates. */
    static final long MAX_CACHE_BYTES = 128L * 1024 * 1024;
    private record Snapshot(Rac1WrenchDataStatus status, Rac1WrenchData data) { }
    private volatile Snapshot snapshot = new Snapshot(
            new Rac1WrenchDataStatus(VALIDATING, "Reading local wrench data configuration.", NONE), null);
    private boolean closed;
    private final Consumer<Rac1WrenchDataStatus> diagnostics;
    private volatile Thread ownedThread;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "racmc-server-wrench-data");
        thread.setDaemon(true);
        ownedThread = thread;
        return thread;
    });

    private Rac1WrenchDataProvider(Path directory, boolean dedicated,
                                   Consumer<Rac1WrenchDataStatus> diagnostics) {
        this.diagnostics = diagnostics;
        worker.submit(() -> load(directory, dedicated));
    }

    public static Rac1WrenchDataProvider open(Path modDirectory, boolean dedicated) {
        return open(modDirectory, dedicated, ignored -> { });
    }

    public static Rac1WrenchDataProvider open(Path modDirectory, boolean dedicated,
                                             Consumer<Rac1WrenchDataStatus> diagnostics) {
        return new Rac1WrenchDataProvider(modDirectory, dedicated, diagnostics);
    }

    public Rac1WrenchDataStatus status() {
        return snapshot.status();
    }

    public Optional<Rac1WrenchData> data() {
        return Optional.ofNullable(snapshot.data());
    }

    private synchronized void publish(Rac1WrenchDataStatus status, Rac1WrenchData data) {
        if (!closed) {
            snapshot = new Snapshot(status, data);
            diagnostics.accept(status);
        }
    }

    private void load(Path directory, boolean dedicated) {
        try {
            var config = Rac1WrenchServerConfig.read(directory, dedicated);
            if (config.image() == null) {
                publish(new Rac1WrenchDataStatus(MISSING,
                        "Managed wrench actions disabled: configure a local ISO in "
                                + directory.resolve(Rac1WrenchServerConfig.FILE_NAME), NONE), null);
                return;
            }
            var validator = new Rac1DiscValidator();
            publish(new Rac1WrenchDataStatus(VALIDATING,
                    "Validating the operator's complete local disc image.", NONE), null);
            try (var disc = validator.inspect(config.image(), (read, total) -> checkCanceled())) {
                checkCanceled();
                if (!disc.isSupported()) {
                    throw new IOException(disc.result().message());
                }
                var revision = disc.result().revision();
                var profile = validator.catalog().byProductCode(disc.result().productCode()).orElseThrow();
                String key = Rac1DiscIdentity.key(revision, profile);
                Rac1CacheStore store = new Rac1CacheStore(config.cacheDirectory());
                var cached = cached(store, key);
                if (cached.isPresent()) {
                    publish(new Rac1WrenchDataStatus(READY, "Local wrench cache ready.", CACHE),
                            cached.get());
                    return;
                }
                if (config.sharedReadCacheDirectory() != null) {
                    var shared = cached(new Rac1CacheStore(config.sharedReadCacheDirectory()), key);
                    if (shared.isPresent()) {
                        publish(new Rac1WrenchDataStatus(READY,
                                "Validated shared local cache ready (read-only).", SHARED_CACHE),
                                shared.get());
                        return;
                    }
                }
                publish(new Rac1WrenchDataStatus(IMPORTING,
                        "Importing original animation data into the server-local cache.", NONE), null);
                store.verifyUsable();
                var content = Rac1CacheImporter.read(disc.image(), key,
                        (message, percent) -> checkCanceled());
                var data = Rac1WrenchData.from(content);
                checkCanceled();
                store.store(key, content);
                checkCanceled();
                publish(new Rac1WrenchDataStatus(READY, "Original local wrench data imported.", IMPORT),
                        data);
            }
        } catch (CancellationException canceled) {
            // close() already invalidates availability; never publish READY afterward.
        } catch (Exception failure) {
            publish(new Rac1WrenchDataStatus(FAILED,
                    "Managed wrench actions disabled: " + failure.getMessage(), NONE), null);
        } finally {
            worker.shutdown(); // one startup job, no idle background polling/retry worker
        }
    }

    private Optional<Rac1WrenchData> cached(Rac1CacheStore store, String key) {
        checkCanceled();
        try {
            if (!cacheWithinLimit(store.fileFor(key))) {
                return Optional.empty();
            }
            return store.load(key).map(Rac1WrenchData::from);
        } catch (IOException | IllegalArgumentException incompleteOrCorrupt) {
            return Optional.empty(); // rebuild separately; never repair the read-only shared cache
        }
    }

    static boolean cacheWithinLimit(Path file) throws IOException {
        return !Files.isRegularFile(file) || Files.size(file) <= MAX_CACHE_BYTES;
    }

    private static void checkCanceled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Local wrench data import canceled.");
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            closed = true;
            snapshot = new Snapshot(new Rac1WrenchDataStatus(CLOSED,
                    "Local wrench data provider closed.", NONE), null);
        }
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IOException("Local wrench data import worker did not terminate.");
            }
            Thread thread = ownedThread;
            if (thread != null) {
                thread.join(1000);
                if (thread.isAlive()) {
                    throw new IOException("Owned local wrench data thread did not exit.");
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while joining local wrench data import.", interrupted);
        }
    }
}
