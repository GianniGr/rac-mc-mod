package com.github.giannigr.racmc.iso;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService executor;

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
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "racmc-media-verification");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Reads the configuration and verifies the image it names in the background.
     *
     * <p>The state of the media is {@link Rac1MediaState#CHECKING} while the image is verified, so
     * that the game stays closed until the image is known to be good.
     */
    public void start() {
        Rac1MediaConfig stored;
        try {
            stored = Rac1MediaConfig.read(configFile);
        } catch (IOException exception) {
            stored = null;
        }
        if (stored == null) {
            state = Rac1MediaState.UNCONFIGURED;
            return;
        }
        configuredPath = stored.path();
        state = Rac1MediaState.CHECKING;
        verify(stored.path(), false);
    }

    /**
     * Verifies a candidate image in the background.
     *
     * <p>When the image passes every check, it becomes the configured image and is written to the
     * configuration. When it does not, the previously configured image stays in place.
     *
     * @param path the image the player chose
     */
    public void select(Path path) {
        if (path == null) {
            return;
        }
        attemptPath = path;
        attemptState = Rac1MediaState.CHECKING;
        attemptResult = null;
        bytesRead = 0;
        bytesTotal = 0;
        verify(path, true);
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

    private void verify(Path path, boolean replaceOnSuccess) {
        executor.submit(() -> {
            try (Rac1IsoSelectionResult outcome = validator.inspect(path, this::reportProgress)) {
                attemptResult = outcome.result();
                if (outcome.isSupported()) {
                    configuredPath = path;
                    state = Rac1MediaState.VALID;
                    attemptState = Rac1MediaState.VALID;
                    if (replaceOnSuccess) {
                        save(path, outcome.result());
                    }
                } else {
                    attemptState = Rac1MediaState.INVALID;
                    if (!replaceOnSuccess) {
                        // The image that is configured could not be verified any more.
                        state = Rac1MediaState.INVALID;
                    }
                }
            } catch (IOException exception) {
                attemptResult = IsoValidationResult.failed(IsoValidationStatus.IO_ERROR,
                        "The selected file could not be read: " + exception.getMessage(), path);
                attemptState = Rac1MediaState.INVALID;
                if (!replaceOnSuccess) {
                    state = Rac1MediaState.INVALID;
                }
            } finally {
                completedAttempts.incrementAndGet();
            }
        });
    }

    private void reportProgress(long read, long total) {
        bytesRead = read;
        bytesTotal = total;
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
            state = Rac1MediaState.VALID;
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
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
