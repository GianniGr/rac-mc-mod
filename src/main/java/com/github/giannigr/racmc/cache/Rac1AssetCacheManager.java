package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1IsoSelectionResult;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The manager of the cache of the assets.
 *
 * <p>The manager is the only place that knows whether the assets of the game are there. It reads
 * the cache of the disc that is configured, or builds it when there is none. The building runs on a
 * worker thread, so the game stays usable while the assets are read.
 *
 * <p>A cache is only used when it holds both assets and when every check of the file agrees. A
 * cache that is damaged is thrown away and built again, so that the game never works with half
 * of the assets.
 */
public final class Rac1AssetCacheManager implements Closeable {

    /** The state of the cache. */
    private volatile Rac1CacheState state = Rac1CacheState.MISSING;

    /** The message of the failure that stopped the building, if there was one. */
    private volatile String error;

    /** The step of the building that is running. */
    private volatile String step = "";

    /** How much of the work is done, in percent. */
    private volatile int progress;

    /** The content of the cache, when there is one. */
    private volatile Rac1CacheContent content;

    /** The disc image the cache belongs to. */
    private volatile Path discImage;

    /** The identity of the disc the cache belongs to. */
    private volatile String discKey;

    /** The number of the builds that were started since the manager was created. */
    private final AtomicLong builds = new AtomicLong();

    private final Rac1CacheStore store;
    private final Rac1DiscValidator validator;
    private final ExecutorService executor;

    /**
     * Creates a manager.
     *
     * @param store the place where the cache is kept
     * @param validator the validator that opens the disc image
     */
    public Rac1AssetCacheManager(Rac1CacheStore store, Rac1DiscValidator validator) {
        this.store = store;
        this.validator = validator;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "racmc-asset-cache");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * The state of the cache.
     *
     * @return the state
     */
    public Rac1CacheState state() {
        return state;
    }

    /**
     * Whether the assets of the game are there.
     *
     * @return {@code true} when the cache holds both assets
     */
    public boolean isReady() {
        return state == Rac1CacheState.READY && content != null;
    }

    /**
     * The content of the cache.
     *
     * @return the content, or an empty optional when there is none
     */
    public Optional<Rac1CacheContent> content() {
        return Optional.ofNullable(content);
    }

    /**
     * The message of the failure that stopped the building.
     *
     * @return the message, or an empty optional when there was no failure
     */
    public Optional<String> error() {
        return Optional.ofNullable(error);
    }

    /**
     * The step of the building that is running.
     *
     * @return the step
     */
    public String step() {
        return step;
    }

    /**
     * How much of the work is done.
     *
     * @return the share in percent
     */
    public int progress() {
        return progress;
    }

    /**
     * The number of the builds that were started.
     *
     * @return the number of builds
     */
    public long builds() {
        return builds.get();
    }

    /**
     * Makes the cache of the given disc ready.
     *
     * <p>When there is a cache that holds both assets, it is used. Otherwise the cache is built
     * in the background.
     *
     * @param discImage the disc image the assets come from
     * @param discKey the identity of the disc image
     */
    public void start(Path discImage, String discKey) {
        this.discImage = discImage;
        this.discKey = discKey;
        this.content = null;
        this.error = null;
        this.progress = 0;
        this.step = "";
        this.state = Rac1CacheState.MISSING;
        builds.incrementAndGet();
        executor.submit((Runnable) this::build);
    }

    /**
     * Builds the cache again.
     *
     * <p>The method does nothing when no disc image is configured.
     */
    public void retry() {
        Path image = discImage;
        String key = discKey;
        if (image == null || key == null) {
            return;
        }
        start(image, key);
    }

    private void build() {
        Path image = discImage;
        String key = discKey;
        if (image == null || key == null) {
            return;
        }
        try {
            Optional<Rac1CacheContent> cached = store.load(key);
            if (cached.isPresent()) {
                content = cached.get();
                progress = 100;
                step = "The assets are ready.";
                state = Rac1CacheState.READY;
                return;
            }
        } catch (IOException exception) {
            // A cache that cannot be read is thrown away and built again.
            error = null;
        }
        state = Rac1CacheState.BUILDING;
        content = null;
        try {
            Rac1CacheContent built = build(key, image);
            store.store(key, built);
            content = built;
            progress = 100;
            step = "The assets are ready.";
            state = Rac1CacheState.READY;
        } catch (IOException | DiscFormatException | WadFormatException | AssetFormatException
                | RuntimeException exception) {
            error = describe(exception);
            content = null;
            state = Rac1CacheState.FAILED;
        }
    }

    private Rac1CacheContent build(String key, Path image)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        step = "The disc image is opened.";
        progress = 5;
        try (Rac1IsoSelectionResult outcome = validator.inspect(image)) {
            if (!outcome.isSupported()) {
                throw new IOException(outcome.result().message());
            }
            step = "The assets of the level are read.";
            progress = 20;
            Rac1LevelAssets assets = Rac1LevelAssets.load(outcome.image(),
                    Rac1AssetExtractor.LEVEL);
            step = "The scenery is converted.";
            progress = 40;
            Rac1StaticAsset staticAsset = Rac1AssetExtractor.readStaticAsset(assets,
                    Rac1AssetExtractor.STATIC_CLASS);
            step = "The character is converted.";
            progress = 60;
            Rac1AnimatedAsset animatedAsset = Rac1AssetExtractor.readAnimatedAsset(assets,
                    Rac1AssetExtractor.ANIMATED_CLASS);
            step = "The weapon is converted.";
            progress = 80;
            Rac1AnimatedAsset blaster = Rac1AssetExtractor.readGadgetAsset(assets,
                    Rac1AssetExtractor.BLASTER_CLASS);
            int[] handChain = Rac1AssetExtractor.readHandChain(assets);
            int handJoint = handChain.length == 0 ? 0 : handChain[handChain.length - 1];
            step = "The cache is written.";
            progress = 90;
            return new Rac1CacheContent(key, staticAsset, animatedAsset, blaster, handJoint);
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
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
