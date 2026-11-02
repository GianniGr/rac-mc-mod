package com.github.giannigr.racmc.iso;

import java.util.Set;

/**
 * The rule that decides whether the game may be entered.
 *
 * <p>The rule is deliberately kept apart from the game code: it only knows the state of the
 * configured disc image and the names of the screens that lead into a world or into a server. That
 * way it can be checked without a running game.
 */
public final class Rac1MediaGate {

    /**
     * The screens that lead into a world or into a multiplayer server.
     *
     * <p>They are named by their class name, so that this rule does not depend on the game.
     */
    public static final Set<String> BLOCKED_SCREENS = Set.of(
            "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen",
            "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen",
            "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen",
            "net.minecraft.client.gui.screens.ConnectScreen");

    private Rac1MediaGate() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Whether the game may be entered with the given state of the media.
     *
     * @param state the state of the configured disc image
     * @return {@code true} only when the configured image was verified
     */
    public static boolean allowsWorldEntry(Rac1MediaState state) {
        return state == Rac1MediaState.VALID;
    }

    /**
     * Whether the selection screen may be dismissed with the given state of the media.
     *
     * <p>The screen may only be dismissed when a verified disc image is configured. While it is
     * not, the screen keeps the player in it, so the main menu cannot be used around it.
     *
     * @param state the state of the configured disc image
     * @return {@code true} only when the configured image was verified
     */
    public static boolean isDismissible(Rac1MediaState state) {
        return state == Rac1MediaState.VALID;
    }

    /**
     * Whether a world that is active has to be left again.
     *
     * <p>A quick play launch starts a world before the mod is loaded, so it can get around the
     * screens that the gate blocks. Such a world is left again as long as no verified disc image
     * is configured.
     *
     * @param state the state of the configured disc image
     * @return {@code true} while no verified disc image is configured
     */
    public static boolean mustLeaveWorld(Rac1MediaState state) {
        return state != Rac1MediaState.VALID;
    }

    /**
     * Whether the given screen leads into a world or into a server and may therefore not be
     * opened while the media is not configured.
     *
     * @param screenClassName the class name of the screen that is about to open
     * @return {@code true} when the screen has to be blocked
     */
    public static boolean blocksScreen(String screenClassName) {
        return screenClassName != null && BLOCKED_SCREENS.contains(screenClassName);
    }
}
