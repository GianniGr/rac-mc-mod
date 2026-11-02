package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.client.screen.IsoSelectionScreen;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import com.github.giannigr.racmc.iso.Rac1MediaGate;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
 *   <li>the screens that lead into a world or into a multiplayer server are not opened while no
 *       verified disc image is configured; the selection screen is shown instead;</li>
 *   <li>the selection screen is shown once when the main menu appears while the media is not
 *       configured;</li>
 *   <li>a world that was entered anyway - by a quick play launch, which starts a world before the
 *       mod is loaded - is left again on the next tick.</li>
 * </ul>
 *
 * <p>There is no key mapping and no screen inside a world that opens the selection screen. The
 * selection screen belongs to the main menu.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class RacMcClient {

    /** The class name of the main menu of the game. */
    private static final String TITLE_SCREEN = "net.minecraft.client.gui.screens.TitleScreen";

    private static final Rac1MediaManager MEDIA =
            new Rac1MediaManager(new Rac1DiscValidator(), mediaConfigurationFile());

    /** Whether the selection screen was already shown for this start of the game. */
    private static boolean promptShown;

    /**
     * The manager that holds the disc image the mod uses.
     *
     * @return the media manager of the client
     */
    public static Rac1MediaManager media() {
        return MEDIA;
    }

    /**
     * The file the path of the disc image is remembered in.
     *
     * @return the file of the configuration
     */
    private static java.nio.file.Path mediaConfigurationFile() {
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
            event.setNewScreen(new IsoSelectionScreen());
            return;
        }

        if (TITLE_SCREEN.equals(screenName) && !ready && !promptShown) {
            // The main menu appears without a usable disc image: ask for one, once.
            promptShown = true;
            event.setNewScreen(new IsoSelectionScreen());
        }
    }

    /**
     * Leaves a world that was entered without a verified disc image.
     *
     * <p>A quick play launch starts a world before the mod is loaded, so the world can be entered
     * without the gate. Such a world is left again on the next tick and the player is sent back to
     * the main menu.
     *
     * @param event the event that marks the end of a client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || Rac1MediaGate.allowsWorldEntry(MEDIA.state())) {
            return;
        }
        minecraft.disconnect(new TitleScreen());
        minecraft.setScreen(new IsoSelectionScreen());
    }

    private RacMcClient() {
        throw new UnsupportedOperationException("Utility class");
    }
}
