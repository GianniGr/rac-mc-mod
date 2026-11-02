package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.iso.Rac1IsoSelectionService;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The client side of the mod.
 *
 * <p>Everything in this class is client only. The class is registered with
 * {@link EventBusSubscriber#value()} set to {@link Dist#CLIENT}, so a dedicated server never
 * loads it and never registers the key mapping.
 *
 * <p>The key mapping only opens the disc selection screen. It does not gate the game in any way:
 * a player who never presses it keeps playing normally, and no disc image is required to start.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class RacMcClient {

    /** The translation key of the category the key mapping is listed under. */
    public static final String KEY_CATEGORY = "key.categories.racmc";

    /**
     * The key mapping that opens the disc selection screen.
     *
     * <p>It is bound to the O key by default and can be changed under
     * {@code Options > Controls > Key Mappings}.
     */
    public static final KeyMapping OPEN_ISO_SELECTION = new KeyMapping(
            "key.racmc.open_iso_selection",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY);

    private static final Rac1IsoSelectionService SELECTION_SERVICE = new Rac1IsoSelectionService();

    /**
     * The service that keeps the disc image the player selected.
     *
     * @return the selection service of the client
     */
    public static Rac1IsoSelectionService selectionService() {
        return SELECTION_SERVICE;
    }

    /**
     * Registers the key mapping of the mod.
     *
     * @param event the event that carries the key mappings of the mod
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ISO_SELECTION);
    }

    private RacMcClient() {
        throw new UnsupportedOperationException("Utility class");
    }
}
