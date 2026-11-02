package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.screen.IsoSelectionScreen;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import com.github.giannigr.racmc.iso.Rac1MediaGate;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import java.nio.file.Path;
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
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client side of the mod.
 *
 * <p>Everything in this class is client only. The class is registered with
 * {@link EventBusSubscriber#value()} set to {@link Dist#CLIENT}, so a dedicated server never loads
 * it.
 *
 * <p>The class holds the gate that keeps the game closed until a disc image is configured:
 *
 * <ul>
 *   <li>the screens that lead into a world or into a multiplayer server do not open while no
 *       verified disc image is configured; the selection screen takes their place;</li>
 *   <li>the main menu does not open either while no verified disc image is configured, so the
 *       selection screen cannot be dismissed and the main menu cannot be used around it;</li>
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

    private static final Rac1MediaManager MEDIA =
            new Rac1MediaManager(new Rac1DiscValidator(), mediaConfigurationFile());

    /**
     * The manager that holds the disc image the mod uses.
     *
     * @return the media manager of the client
     */
    public static Rac1MediaManager media() {
        return MEDIA;
    }

    /**
     * Whether the game may not be entered at the moment.
     *
     * @return {@code true} while no verified disc image is configured
     */
    public static boolean isWorldEntryBlocked() {
        return !Rac1MediaGate.allowsWorldEntry(MEDIA.state());
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
     * Reads the configuration of the media and verifies the image it names in the background.
     *
     * @param event the event that marks the end of the client setup
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(MEDIA::start);
    }

    /**
     * Keeps the game closed while no verified disc image is configured.
     *
     * @param event the event that is fired before a screen opens
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        String screenName = event.getNewScreen().getClass().getName();
        boolean ready = Rac1MediaGate.allowsWorldEntry(MEDIA.state());

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
            // The main menu does not open while no verified disc image is configured, so the
            // selection screen cannot be dismissed and the main menu cannot be used around it.
            RacMcMod.LOGGER.info("The disc image gate keeps the main menu closed until a disc image "
                    + "is configured.");
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
    }

    /**
     * Leaves a world that was entered without a verified disc image.
     *
     * <p>A quick play launch starts a world or joins a server before the mod is loaded, so it can
     * get around the screens above. Such a world is left again on the next client tick and the
     * player is sent back to the selection screen. The game therefore fails closed on that path
     * as well: a world is never played without a verified disc image.
     *
     * @param event the event that marks the end of a client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !Rac1MediaGate.mustLeaveWorld(MEDIA.state())) {
            return;
        }
        RacMcMod.LOGGER.info("The disc image gate leaves the world that was entered without a "
                + "verified disc image.");
        minecraft.disconnect(new TitleScreen());
        minecraft.setScreen(new IsoSelectionScreen(true));
    }

    private RacMcClient() {
        throw new UnsupportedOperationException("Utility class");
    }
}
