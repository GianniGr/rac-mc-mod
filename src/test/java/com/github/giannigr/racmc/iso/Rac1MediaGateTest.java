package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.cache.Rac1CacheState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the rule that decides whether the game may be entered.
 *
 * <p>The rule is the part of the gate that does not depend on the game, so it can be checked
 * without a running game.
 */
class Rac1MediaGateTest {

    @Test
    @DisplayName("The game may only be entered with a verified disc image")
    void onlyAVerifiedImageOpensTheGate() {
        assertTrue(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.VALID));
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.CHECKING));
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.INVALID));
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.UNCONFIGURED));
    }

    @Test
    @DisplayName("The selection screen cannot be dismissed without a verified disc image")
    void theSelectionScreenCannotBeDismissed() {
        assertTrue(Rac1MediaGate.isDismissible(Rac1MediaState.VALID));
        assertFalse(Rac1MediaGate.isDismissible(Rac1MediaState.CHECKING),
                "The screen has to stay while the image is verified");
        assertFalse(Rac1MediaGate.isDismissible(Rac1MediaState.INVALID),
                "The screen has to stay while the image is missing or changed");
        assertFalse(Rac1MediaGate.isDismissible(Rac1MediaState.UNCONFIGURED),
                "The screen has to stay while no image is configured");
    }

    @Test
    @DisplayName("Every state but the verified one closes the gate and the screen")
    void everyOtherStateClosesTheGateAndTheScreen() {
        for (Rac1MediaState state : Rac1MediaState.values()) {
            boolean verified = state == Rac1MediaState.VALID;
            assertEquals(verified, Rac1MediaGate.allowsWorldEntry(state),
                    "The gate has to be open only for a verified image, but was checked for " + state);
            assertEquals(verified, Rac1MediaGate.isDismissible(state),
                    "The screen may only be dismissed for a verified image, but was checked for " + state);
        }
    }

    @Test
    @DisplayName("The screens that lead into a world or into a server are blocked")
    void worldEntryScreensAreBlocked() {
        assertTrue(Rac1MediaGate.blocksScreen(
                "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"));
        assertTrue(Rac1MediaGate.blocksScreen(
                "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"));
        assertTrue(Rac1MediaGate.blocksScreen(
                "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen"));
    }

    @Test
    @DisplayName("A world that was entered without a verified disc image has to be left again")
    void aWorldHasToBeLeftWithoutAVerifiedImage() {
        assertFalse(Rac1MediaGate.mustLeaveWorld(Rac1MediaState.VALID));
        assertTrue(Rac1MediaGate.mustLeaveWorld(Rac1MediaState.CHECKING),
                "A world has to be left while the image is verified");
        assertTrue(Rac1MediaGate.mustLeaveWorld(Rac1MediaState.INVALID),
                "A world has to be left while the image is missing or changed");
        assertTrue(Rac1MediaGate.mustLeaveWorld(Rac1MediaState.UNCONFIGURED),
                "A world has to be left while no image is configured");
    }

    @Test
    @DisplayName("The screens of the main menu are not blocked")
    void mainMenuScreensAreNotBlocked() {
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.TitleScreen"));
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.OptionsScreen"));
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.multiplayer.SafetyScreen"));
        assertFalse(Rac1MediaGate.blocksScreen(null));
    }

    @Test
    @DisplayName("The game may only be entered when the cache of the assets is ready")
    void onlyAReadyCacheOpensTheGate() {
        assertTrue(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.VALID, Rac1CacheState.READY));
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.VALID, Rac1CacheState.BUILDING),
                "The game has to stay closed while the assets are converted");
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.VALID, Rac1CacheState.MISSING),
                "The game has to stay closed while there is no cache");
        assertFalse(Rac1MediaGate.allowsWorldEntry(Rac1MediaState.VALID, Rac1CacheState.FAILED),
                "The game has to stay closed when the cache could not be built");
    }

    @Test
    @DisplayName("Every state but the ready one closes the gate")
    void everyOtherCacheStateClosesTheGate() {
        for (Rac1MediaState media : Rac1MediaState.values()) {
            for (Rac1CacheState cache : Rac1CacheState.values()) {
                boolean open = media == Rac1MediaState.VALID && cache == Rac1CacheState.READY;
                assertEquals(open, Rac1MediaGate.allowsWorldEntry(media, cache),
                        "The gate has to be open only for a verified image and a ready cache, but was "
                                + "checked for " + media + " and " + cache);
            }
        }
    }
}
