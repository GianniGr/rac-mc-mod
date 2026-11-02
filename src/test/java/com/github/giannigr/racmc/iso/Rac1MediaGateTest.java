package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @DisplayName("The screens of the main menu are not blocked")
    void mainMenuScreensAreNotBlocked() {
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.TitleScreen"));
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.OptionsScreen"));
        assertFalse(Rac1MediaGate.blocksScreen("net.minecraft.client.gui.screens.multiplayer.SafetyScreen"));
        assertFalse(Rac1MediaGate.blocksScreen(null));
    }
}
