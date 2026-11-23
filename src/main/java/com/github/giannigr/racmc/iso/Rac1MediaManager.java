package com.github.giannigr.racmc.iso;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the disc image the mod is configured to use and verifies it in the background.
 *
 * <p>The manager is the single place that knows whether the game may be entered. It never blocks
 * the thread that calls it: every verification runs on a worker thread, and the caller reads the
 * state of the media at any time.
 *
 * <p>The path of a verified image is written to the configuration of the mod, so that it can be
 * reused after a restart. The configuration holds the path and a few facts about the image, never
 * any part of the image itself. A new image is only written to the configuration after it was
 * verified completely; a failed attempt leaves the previous, valid image in place.
 */
public final class Rac1MediaManager implements Closeable {

    private final Rac1DiscValidator validator;
    private final Path configFile;
    private final ThreadPoolExecutor executor;
    private Future<?> verification;
    private volatile Rac1MediaLease.Retained verified;
    private volatile long generation, request;
    private volatile boolean closed;

    private volatile Rac1MediaState state = Rac1MediaState.UNCONFIGURED;
    private volatile Rac1MediaState attemptState;
    private volatile Path configuredPath;
    private volatile Path attemptPath;
    private volatile IsoValidationResult attemptResult;
    private volatile long bytesRead;
    private volatile long bytesTotal;
    private final java.util.concurrent.atomic.AtomicLong completedAttempts =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Creates a manager.
     *
     * @param validator the validator that checks the candidate images
     * @param configFile the file the configuration of the media is stored in
     */
    public Rac1MediaManager(Rac1DiscValidator validator, Path configFile) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.executor = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new LinkedBlockingQueue<>(1),runnable -> {
            Thread thread = new Thread(runnable, "racmc-media-verification");
            thread.setDaemon(true);
            return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Reads the configuration and verifies the image it names in the background.
     *
     * <p>The state of the media is {@link Rac1MediaState#CHECKING} while the image is verified, so
     * that the game stays closed until the image is known to be good.
     */
    public synchronized void start() {
        requireOpen();
        Rac1MediaConfig stored;
        try {
            stored = Rac1MediaConfig.read(configFile);
        } catch (IOException exception) {
            stored = null;
        }
        if (stored == null) {
            cancelRequest(); revokeVerified(); configuredPath=null;
            state = Rac1MediaState.UNCONFIGURED;
            attemptState=null; attemptPath=null; attemptResult=null;
            return;
        }
        configuredPath = stored.path();
        state = Rac1MediaState.CHECKING;
        begin(stored.path(), false);
    }

    /**
     * Verifies a candidate image in the background.
     *
     * <p>When the image passes every check, it becomes the configured image and is written to the
     * configuration. When it does not, the previously configured image stays in place.
     *
     * @param path the image the player chose
     */
    public synchronized void select(Path path) {
        requireOpen();
        if (path == null) {
            return;
        }
        begin(path,true);
    }

    /** Memory-only invalidation for resource reload/logout or cancelled asset consumers. */
    public synchronized void invalidateLeases() { generation++; }
    public long generation() { return generation; }

    /** Unsets this active session without deleting or rewriting the remembered configuration. */
    public synchronized void unset() {
        requireOpen(); cancelRequest(); revokeVerified();
        configuredPath=null; state=Rac1MediaState.UNCONFIGURED;
        attemptState=null; attemptPath=null; attemptResult=null; bytesRead=bytesTotal=0;
    }

    /**
     * Only an already fully verified selected image can issue a lease. Arguments are match guards,
     * never an alternate path to open or a cache-provided validation authority. Use on asset workers.
     */
    public synchronized Rac1MediaLease lease(Path expectedPath,String expectedDiscKey) throws IOException {
        var source=verified;
        if (closed || state!=Rac1MediaState.VALID || isChecking() || source==null || !source.active || source.stamp.fileKey()==null
                || expectedPath==null || !source.image.sourcePath().equals(expectedPath.toAbsolutePath().normalize())
                || !source.key.equals(expectedDiscKey))
            throw new IOException("No fully verified current media selection matches the requested lease");
        long epoch=generation;
        return new Rac1MediaLease(source,epoch,() -> !closed && generation==epoch && verified==source
                && state==Rac1MediaState.VALID && !isChecking());
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Media manager closed"); }
    private void cancelRequest() {
        request++; generation++;
        if (verification!=null) verification.cancel(true);
        executor.purge();
    }
    private void revokeVerified() {
        var previous=verified; verified=null;
        if (previous!=null) previous.revoke();
    }
    private void begin(Path path,boolean replaceOnSuccess) {
        cancelRequest();
        // Retain an unchanged different prior selection if a replacement attempt fails. Same-path
        // revalidation must never leave the old ready gate or old leases usable.
        if (!replaceOnSuccess || configuredPath!=null && configuredPath.equals(path.toAbsolutePath().normalize())) {
            revokeVerified(); state=Rac1MediaState.CHECKING;
        }
        attemptPath=path; attemptState=Rac1MediaState.CHECKING; attemptResult=null; bytesRead=bytesTotal=0;
        long ticket=request;
        verification=executor.submit(() -> verify(path,replaceOnSuccess,ticket));
    }

    /**
     * The state of the configured image.
     *
     * @return the state
     */
    public Rac1MediaState state() {
        return state;
    }

    /**
     * Whether the game may be entered with the configured image.
     *
     * @return {@code true} when the configured image was verified
     */
    public boolean isReady() {
        return state == Rac1MediaState.VALID;
    }

    /**
     * The image that is configured.
     *
     * @return the path of the configured image, or an empty optional when none is configured
     */
    public Optional<Path> configuredPath() {
        return Optional.ofNullable(configuredPath);
    }

    /**
     * The state of the verification that is running or that ran last.
     *
     * @return the state of the attempt, or an empty optional when no attempt was made yet
     */
    public Optional<Rac1MediaState> attemptState() {
        return Optional.ofNullable(attemptState);
    }

    /**
     * The image of the verification that is running or that ran last.
     *
     * @return the path of the attempt, or an empty optional when no attempt was made yet
     */
    public Optional<Path> attemptPath() {
        return Optional.ofNullable(attemptPath);
    }

    /**
     * The outcome of the verification that ran last.
     *
     * @return the result, or an empty optional when no attempt finished yet
     */
    public Optional<IsoValidationResult> attemptResult() {
        return Optional.ofNullable(attemptResult);
    }

    /**
     * Whether a verification is running at the moment.
     *
     * @return {@code true} while a verification is running
     */
    public boolean isChecking() {
        return attemptState == Rac1MediaState.CHECKING;
    }

    /**
     * How far the running verification has come.
     *
     * @return the number of bytes that were read so far
     */
    public long bytesRead() {
        return bytesRead;
    }

    /**
     * How much the running verification has to read.
     *
     * @return the total number of bytes, or {@code 0} when the size is not known yet
     */
    public long bytesTotal() {
        return bytesTotal;
    }

    /**
     * How many verifications have finished since the manager was created.
     *
     * <p>The number grows every time a verification is over, whatever its outcome. It lets a
     * caller wait for the end of a verification without guessing how long it takes.
     *
     * @return the number of finished verifications
     */
    public long completedAttempts() {
        return completedAttempts.get();
    }

    private void verify(Path path, boolean replaceOnSuccess,long ticket) {
            Rac1IsoSelectionResult outcome=null;
            boolean adopted=false;
            try {
                Rac1MediaLease.Stamp before=null;
                try { before=Rac1MediaLease.Stamp.read(path); } catch (IOException ignored) { /* Validator retains its detailed refusal. */ }
                outcome=validator.inspect(path,(read,total) -> reportProgress(ticket,read,total));
                Rac1MediaLease.Stamp after=outcome.isSupported() ? Rac1MediaLease.Stamp.read(path):null;
                if (outcome.isSupported() && (before==null || !before.equals(after)))
                    throw new IOException("Selected media changed while it was being verified");
                synchronized (this) {
                if (closed || ticket!=request) return;
                attemptResult=outcome.result();
                if (outcome.isSupported()) {
                    var image=outcome.image();
                    var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==image.revision())
                            .findFirst().orElseThrow(() -> new IOException("Verified media profile missing"));
                    String key=image.revision().label()+"|"+profile.productCode()+"|"+profile.imageSha1();
                    revokeVerified();
                    verified=new Rac1MediaLease.Retained(image,key,after,() -> sourceChanged(image));
                    adopted=true; generation++;
                    configuredPath = image.sourcePath();
                    state = Rac1MediaState.VALID;
                    attemptState = Rac1MediaState.VALID;
                    if (replaceOnSuccess) {
                        save(path, outcome.result());
                    }
                } else {
                    attemptState = Rac1MediaState.INVALID;
                    if (!replaceOnSuccess || verified==null) {
                        // The image that is configured could not be verified any more.
                        state = Rac1MediaState.INVALID;
                    }
                }
                }
            } catch (CancellationException cancelled) {
                // A newer request/unset/close owns diagnostics. Never publish an obsolete completion.
            } catch (IOException exception) {
                synchronized (this) {
                if (closed || ticket!=request) return;
                attemptResult = IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                        "The selected file could not be read: " + exception.getMessage(), path);
                attemptState = Rac1MediaState.INVALID;
                if (!replaceOnSuccess || verified==null) {
                    state = Rac1MediaState.INVALID;
                }
                }
            } finally {
                if (!adopted && outcome!=null) try { outcome.close(); } catch (IOException ignored) { }
                completedAttempts.incrementAndGet();
            }
    }

    private synchronized void reportProgress(long ticket,long read, long total) {
        if (closed || ticket!=request || Thread.currentThread().isInterrupted()) throw new CancellationException("Obsolete media verification");
        bytesRead = read;
        bytesTotal = total;
    }
    private synchronized void sourceChanged(Rac1DiscImage image) {
        if (verified==null || verified.image!=image) return;
        revokeVerified(); generation++;
        state=Rac1MediaState.INVALID;
        if (!isChecking()) {
            attemptState=Rac1MediaState.INVALID;
            attemptResult=IsoValidationResult.failed(IsoValidationStatus.CORRUPT_IMAGE,
                    "Selected media changed; verify it again before reading assets.",image.sourcePath());
        }
    }

    private void save(Path path, IsoValidationResult result) {
        try {
            long modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
            Rac1MediaConfig configuration = new Rac1MediaConfig(
                    path,
                    result.revision() == null ? "" : result.revision().label(),
                    result.revision() == null ? 0L : imageSizeOf(result),
                    imageDigestOf(result),
                    modified);
            configuration.write(configFile);
        } catch (IOException exception) {
            // The image stays configured for this session, it is only not remembered for the next
            // start of the game.
            // The already published valid selection remains valid.
        }
    }

    private long imageSizeOf(IsoValidationResult result) {
        return validator.catalog().profiles().stream()
                .filter(profile -> profile.revision() == result.revision())
                .mapToLong(DiscProfile::imageSize)
                .findFirst()
                .orElse(0L);
    }

    private String imageDigestOf(IsoValidationResult result) {
        return validator.catalog().profiles().stream()
                .filter(profile -> profile.revision() == result.revision())
                .map(DiscProfile::imageSha1)
                .findFirst()
                .orElse("");
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed=true; cancelRequest(); revokeVerified(); state=Rac1MediaState.UNCONFIGURED;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
