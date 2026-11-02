package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.cache.Rac1AssetCacheManager;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.cache.Rac1CacheStore;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.screen.BlasterTestScreen;
import com.github.giannigr.racmc.client.screen.IsoSelectionScreen;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import com.github.giannigr.racmc.iso.Rac1MediaGate;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import com.github.giannigr.racmc.iso.Rac1MediaState;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client side of the mod.
 *
 * <p>Everything in this class is client only. The class is registered with
 * {@link EventBusSubscriber#value()} set to {@link Dist#CLIENT}, so a dedicated server never loads
 * it.
 *
 * <p>The class holds the gate that keeps the game closed until a disc image is configured and the
 * assets of the game are in the cache:
 *
 * <ul>
 *   <li>the screens that lead into a world or into a multiplayer server do not open while no
 *       verified disc image is configured or while the cache of the assets is not ready; the
 *       selection screen takes their place;</li>
 *   <li>the main menu does not open either while the gate is closed, so the selection screen
 *       cannot be dismissed and the main menu cannot be used around it;</li>
 *   <li>a world that was entered in another way - by a quick play launch, which starts a world
 *       before the mod is loaded - is left again on the next client tick and the player is sent
 *       back to the selection screen.</li>
 * </ul>
 *
 * <p>There is no key mapping and no screen inside a world that opens the selection screen. The
 * selection screen belongs to the main menu.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class RacMcClient {

    static {
        // The selection screen uses the file chooser of the system, which needs a graphical user
        // interface. The game itself does not need one and turns it off in its entry point with
        // "java.awt.headless=true". The Java runtime reads the property when it is asked for the
        // first time, so it is turned back on here, before the first use of the file chooser.
        if ("true".equals(System.getProperty("java.awt.headless"))) {
            System.setProperty("java.awt.headless", "false");
        }
    }

    /** The class name of the main menu of the game. */
    private static final String TITLE_SCREEN = "net.minecraft.client.gui.screens.TitleScreen";

    /** The validator that opens the disc image and finds the identity of the disc. */
    private static final Rac1DiscValidator VALIDATOR = new Rac1DiscValidator();

    private static final Rac1MediaManager MEDIA =
            new Rac1MediaManager(VALIDATOR, mediaConfigurationFile());

    private static final Rac1AssetCacheManager ASSETS = new Rac1AssetCacheManager(
            new Rac1CacheStore(cacheDirectory()), VALIDATOR);

    /** The path of the disc image the cache was started for. */
    private static String assetsPath;

    /**
     * The manager that holds the disc image the mod uses.
     *
     * @return the media manager of the client
     */
    public static Rac1MediaManager media() {
        return MEDIA;
    }

    /**
     * The manager that holds the cache of the assets of the game.
     *
     * @return the cache manager of the client
     */
    public static Rac1AssetCacheManager assets() {
        return ASSETS;
    }

    /**
     * Whether the game may not be entered at the moment.
     *
     * @return {@code true} while no verified disc image is configured or while the cache of the
     *         assets is not ready
     */
    public static boolean isWorldEntryBlocked() {
        return !allowsWorldEntry();
    }

    /**
     * Whether the game may be entered at the moment.
     *
     * @return {@code true} when the disc image is verified and the cache of the assets is ready
     */
    public static boolean allowsWorldEntry() {
        return Rac1MediaGate.allowsWorldEntry(MEDIA.state(), ASSETS.state());
    }

    /**
     * The file the path of the disc image is remembered in.
     *
     * @return the file of the configuration
     */
    private static Path mediaConfigurationFile() {
        return Rac1MediaConfig.fileIn(FMLPaths.CONFIGDIR.get().resolve(RacMcConstants.MOD_ID));
    }

    /**
     * The directory the cache of the assets is kept in.
     *
     * @return the directory of the cache
     */
    private static Path cacheDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(RacMcConstants.MOD_ID).resolve("cache");
    }

    /**
     * Reads the configuration of the media and verifies the image it names in the background.
     *
     * @param event the event that marks the end of the client setup
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(MEDIA::start);
    }

    /**
     * Keeps the game closed while no verified disc image is configured or while the cache of the
     * assets is not ready.
     *
     * @param event the event that is fired before a screen opens
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        String screenName = event.getNewScreen().getClass().getName();
        boolean ready = allowsWorldEntry();

        if (Rac1MediaGate.blocksScreen(screenName)) {
            if (ready) {
                return;
            }
            // The screen that leads into a world or into a server does not open. The selection
            // screen takes its place and explains what is missing.
            event.setNewScreen(new IsoSelectionScreen(true));
            return;
        }

        if (TITLE_SCREEN.equals(screenName) && !ready) {
            // The main menu does not open while the gate is closed, so the selection screen cannot
            // be dismissed and the main menu cannot be used around it.
            RacMcMod.LOGGER.info("The disc image gate keeps the main menu closed until the disc "
                    + "image is verified and the assets are in the cache.");
            event.setNewScreen(new IsoSelectionScreen(true));
        }
    }

    /**
     * Adds the button that opens the selection screen to the main menu.
     *
     * <p>The button is only reachable while a verified disc image is configured, because the main
     * menu itself does not open otherwise.
     *
     * @param event the event that is fired after a screen was built
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        Button button = Button.builder(
                        Component.translatable("screen.racmc.iso_selection.open"),
                        pressed -> {
                            RacMcMod.LOGGER.info("The disc image picker is opened from the main menu.");
                            Minecraft.getInstance().setScreen(new IsoSelectionScreen());
                        })
                .bounds(4, event.getScreen().height - 24, 160, 20)
                .build();
        event.addListener(button);

        if (ASSETS.isReady()) {
            // The demo of the Blaster is only there when the assets are in the cache.
            Button demo = Button.builder(
                            Component.translatable("screen.racmc.blaster_test.open"),
                            pressed -> {
                                RacMcMod.LOGGER.info("The demo of the Blaster is opened from the "
                                        + "main menu.");
                                Minecraft.getInstance().setScreen(new BlasterTestScreen());
                            })
                    .bounds(4, event.getScreen().height - 48, 160, 20)
                    .build();
            event.addListener(demo);
        }
    }

    /**
     * Registers the keys of the mod.
     *
     * @param event the event that is fired when the keys of the mods are registered
     */
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(Rac1ClientKeys.blasterTest());
    }

    /**
     * Starts the cache of the assets when the disc image is verified and leaves a world that was
     * entered without a verified disc image.
     *
     * @param event the event that marks the end of a client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        startAssetsWhenReady();
        openDemoWhenAsked();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || allowsWorldEntry()) {
            return;
        }
        RacMcMod.LOGGER.info("The disc image gate leaves the world that was entered without a "
                + "verified disc image or without the assets of the game.");
        minecraft.disconnect(new TitleScreen());
        minecraft.setScreen(new IsoSelectionScreen(true));
    }

    /**
     * Opens the demo of the Blaster when its key was pressed.
     */
    private static void openDemoWhenAsked() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !ASSETS.isReady()) {
            return;
        }
        if (Rac1ClientKeys.blasterTest().consumeClick()) {
            RacMcMod.LOGGER.info("The demo of the Blaster is opened with its key.");
            minecraft.setScreen(new BlasterTestScreen());
        }
    }

    /**
     * Starts the cache of the assets as soon as the disc image is verified.
     *
     * <p>The method does nothing while the disc image is not verified or while the cache of that
     * disc image was already started.
     */
    private static void startAssetsWhenReady() {
        if (MEDIA.state() != Rac1MediaState.VALID) {
            return;
        }
        Optional<Path> image = MEDIA.configuredPath();
        if (image.isEmpty()) {
            return;
        }
        String path = image.get().toAbsolutePath().normalize().toString();
        if (path.equals(assetsPath)) {
            return;
        }
        Optional<String> key = discKey();
        if (key.isEmpty()) {
            RacMcMod.LOGGER.warn("The identity of the disc image could not be found, so the cache of "
                    + "the assets cannot be built.");
            return;
        }
        assetsPath = path;
        RacMcMod.LOGGER.info("The cache of the assets is started for the disc image.");
        ASSETS.start(image.get(), key.get());
    }

    /**
     * The identity of the disc that was verified.
     *
     * <p>The identity is built out of the revision that the verification found and the profile of
     * the catalogue, so that the image does not have to be read again.
     *
     * @return the identity, or an empty optional when the revision is not known
     */
    private static Optional<String> discKey() {
        Optional<Rac1DiscRevision> revision = MEDIA.attemptResult()
                .map(result -> result.revision());
        if (revision.isEmpty()) {
            return Optional.empty();
        }
        return VALIDATOR.catalog().profiles().stream()
                .filter(profile -> profile.revision() == revision.get())
                .findFirst()
                .map(profile -> Rac1DiscIdentity.key(revision.get(), profile));
    }

    /**
     * The static asset of the cache, when it is ready.
     *
     * @return the asset, or an empty optional when the cache is not ready
     */
    public static Optional<Rac1StaticAsset> staticAsset() {
        return ASSETS.content().map(Rac1CacheContent::staticAsset);
    }

    /**
     * The animated asset of the cache, when it is ready.
     *
     * @return the asset, or an empty optional when the cache is not ready
     */
    public static Optional<Rac1AnimatedAsset> animatedAsset() {
        return ASSETS.content().map(Rac1CacheContent::animatedAsset);
    }

    /**
     * The matrices of the joints of the animated asset at a moment of one of its sequences.
     *
     * @param animation the sequence
     * @param time the moment of the sequence in seconds
     * @return the matrices, sixteen floats per joint
     */
    public static float[] pose(Rac1Animation animation, float time) {
        return animatedAsset()
                .map(asset -> Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, time,
                        1.0f))
                .orElseGet(() -> new float[0]);
    }

    /**
     * Registers the textures of the static asset of the game.
     *
     * @param asset the asset whose textures are registered
     * @return the place of the texture of every material
     */
    public static net.minecraft.resources.ResourceLocation[] registerTextures(Rac1StaticAsset asset) {
        net.minecraft.resources.ResourceLocation[] locations =
                new net.minecraft.resources.ResourceLocation[asset.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register(asset.name() + "_" + index,
                    asset.textures()[index]);
        }
        return locations;
    }

    /**
     * Registers the textures of the animated asset of the game.
     *
     * @param asset the asset whose textures are registered
     * @return the place of the texture of every material
     */
    public static net.minecraft.resources.ResourceLocation[] registerTextures(
            Rac1AnimatedAsset asset) {
        net.minecraft.resources.ResourceLocation[] locations =
                new net.minecraft.resources.ResourceLocation[asset.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register(asset.name() + "_" + index,
                    asset.textures()[index]);
        }
        return locations;
    }
}
