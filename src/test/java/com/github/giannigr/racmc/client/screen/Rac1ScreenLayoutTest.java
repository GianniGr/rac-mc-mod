package com.github.giannigr.racmc.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the places of the screens of the mod inside the window of the game.
 *
 * <p>The checks hold that the text of a screen never reaches over the edge of the window, that the
 * report of the screen of the disc image is never cut off but scrolled instead, so that every line
 * of it can be read, and that the buttons of the mod in the main menu stay above the lines of the
 * version of the game, which the main menu writes into its lower left corner.
 *
 * <p>The narrowest window of the game is 320 by 240 pixels. The checks use it and the size of a
 * usual window.
 */
class Rac1ScreenLayoutTest {

    /** The width of the narrowest window of the game. */
    private static final int MIN_WIDTH = 320;

    /** The height of the narrowest window of the game. */
    private static final int MIN_HEIGHT = 240;

    /** The width of a usual window of the game. */
    private static final int WIDTH = 854;

    /** The height of a usual window of the game. */
    private static final int HEIGHT = 480;

    /** The number of lines of a report that is too long for the panel. */
    private static final int LONG_REPORT = 24;

    @Test
    @DisplayName("The text of a screen never reaches over the edge of the window")
    void theTextNeverReachesOverTheEdge() {
        assertTrue(Rac1ScreenLayout.contentWidth(MIN_WIDTH)
                        <= MIN_WIDTH - 2 * Rac1ScreenLayout.MARGIN,
                "The text of a screen must stay inside the narrowest window");
        assertTrue(Rac1ScreenLayout.contentLeft(MIN_WIDTH) >= 0,
                "The text of a screen must start inside the window");
        assertTrue(Rac1ScreenLayout.contentLeft(MIN_WIDTH)
                        + Rac1ScreenLayout.contentWidth(MIN_WIDTH) <= MIN_WIDTH,
                "The text of a screen must end inside the narrowest window");
        assertEquals(Rac1ScreenLayout.MAX_CONTENT_WIDTH, Rac1ScreenLayout.contentWidth(WIDTH),
                "The text of a screen must not grow wider than the widest text of the game");
    }

    @Test
    @DisplayName("The two buttons of a row fit next to each other or one under the other")
    void theButtonsOfARowFit() {
        assertTrue(Rac1ScreenLayout.sideBySide(Rac1ScreenLayout.contentWidth(MIN_WIDTH)),
                "The buttons must fit next to each other in the narrowest window");
        assertTrue(Rac1ScreenLayout.sideBySide(Rac1ScreenLayout.contentWidth(WIDTH)),
                "The buttons must fit next to each other in a usual window");
        assertFalse(Rac1ScreenLayout.sideBySide(Rac1ScreenLayout.PICK_BUTTON_WIDTH),
                "The buttons must stand one under the other when they do not fit");
    }

    @Test
    @DisplayName("The report of the screen fits between the buttons and the edge of the window")
    void theReportFitsInTheWindow() {
        int buttonsBottom = 150;
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(MIN_WIDTH, MIN_HEIGHT,
                Rac1ScreenLayout.reportTop(buttonsBottom, false), LONG_REPORT, false);
        Rac1ScreenLayout.Area report = layout.report();
        assertTrue(report.top() >= buttonsBottom,
                "The report of the screen must stand under the buttons");
        assertTrue(report.bottom() <= MIN_HEIGHT - Rac1ScreenLayout.MARGIN,
                "The report of the screen must stay inside the window");
        assertTrue(report.left() >= 0 && report.right() <= MIN_WIDTH,
                "The report of the screen must stay inside the window");
    }

    @Test
    @DisplayName("A report that is too long is scrolled and every line of it can be read")
    void aLongReportIsScrolled() {
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(MIN_WIDTH, MIN_HEIGHT,
                Rac1ScreenLayout.reportTop(150, false), LONG_REPORT, false);
        assertTrue(layout.visibleLines() >= 1,
                "The panel of the report must show at least one line");
        assertTrue(layout.maxScroll() > 0,
                "A report that does not fit into the panel must be scrolled");
        assertTrue(layout.maxScroll() + layout.visibleLines() >= LONG_REPORT,
                "The last line of the report must be reachable by scrolling");
    }

    @Test
    @DisplayName("A report that fits into the panel is not scrolled")
    void aShortReportIsNotScrolled() {
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(WIDTH, HEIGHT,
                Rac1ScreenLayout.reportTop(150, false), 3, false);
        assertEquals(0, layout.maxScroll(),
                "A report that fits into the panel must not be scrolled");
        assertTrue(layout.visibleLines() >= 3,
                "The panel of the report must show the whole report");
    }

    @Test
    @DisplayName("The drawing of the preview never covers the report")
    void thePreviewNeverCoversTheReport() {
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(WIDTH, HEIGHT,
                Rac1ScreenLayout.reportTop(150, false), 3, true);
        assertTrue(layout.hasPreview(),
                "The preview of the assets must be drawn when there is room for it");
        assertTrue(layout.preview().top() >= layout.report().bottom(),
                "The preview of the assets must stand under the report of the screen");
        assertTrue(layout.preview().bottom() <= HEIGHT - Rac1ScreenLayout.MARGIN,
                "The preview of the assets must stay inside the window");
    }

    @Test
    @DisplayName("The report keeps its room when the window is too small for the preview")
    void theReportKeepsItsRoomInASmallWindow() {
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(MIN_WIDTH, MIN_HEIGHT,
                Rac1ScreenLayout.reportTop(150, false), 3, true);
        assertTrue(layout.report().height() >= Rac1ScreenLayout.MIN_PANEL_HEIGHT,
                "The report of the screen must keep its room in the narrowest window");
        assertFalse(layout.hasPreview(),
                "The preview of the assets must be left out when the window is too small");
    }

    @Test
    @DisplayName("The room of the bar of the progress is only kept while there is a bar")
    void theRoomOfTheProgressIsOnlyKeptWhileItIsNeeded() {
        int buttonsBottom = 110;
        assertTrue(Rac1ScreenLayout.reportTop(buttonsBottom, false)
                        < Rac1ScreenLayout.reportTop(buttonsBottom, true),
                "The report of the screen must keep the room of the bar while there is no bar");
        assertEquals(buttonsBottom + Rac1ScreenLayout.BUTTON_GAP,
                Rac1ScreenLayout.reportTop(buttonsBottom, false),
                "The report of the screen must stand right under the buttons");
        assertEquals(buttonsBottom + Rac1ScreenLayout.BUTTON_GAP
                        + Rac1ScreenLayout.PROGRESS_ROOM,
                Rac1ScreenLayout.reportTop(buttonsBottom, true),
                "The report of the screen must stand under the bar of the progress");
    }

    @Test
    @DisplayName("The report of the screen keeps its room in the narrowest window of the game")
    void theReportKeepsItsRoomInTheNarrowestWindow() {
        // The window of the game is 320 by 240 pixels, but its frame takes a few of them, so the
        // area that the game draws in is a little smaller.
        Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(304, 201,
                Rac1ScreenLayout.reportTop(110, false), LONG_REPORT, false);
        assertTrue(layout.report().height() >= Rac1ScreenLayout.MIN_PANEL_HEIGHT,
                "The report of the screen must keep its room in the narrowest window");
        assertTrue(layout.visibleLines() >= 3,
                "The panel of the report must show several lines in the narrowest window");
        assertTrue(layout.maxScroll() + layout.visibleLines() >= LONG_REPORT,
                "Every line of the report must be reachable by scrolling");
    }

    @Test
    @DisplayName("The buttons of the mod in the main menu stand above the version of the game")
    void theButtonsOfTheMainMenuClearTheFooter() {
        int top = Rac1ScreenLayout.mainMenuButtonTop(MIN_HEIGHT);
        assertTrue(top + Rac1ScreenLayout.BUTTON_HEIGHT
                        <= MIN_HEIGHT - Rac1ScreenLayout.MENU_FOOTER_HEIGHT,
                "The button of the mod must stand above the lines of the version of the game");
        int upper = Rac1ScreenLayout.mainMenuUpperButtonTop(MIN_HEIGHT);
        assertTrue(upper + Rac1ScreenLayout.BUTTON_HEIGHT <= top,
                "The two buttons of the mod must not cover each other");
        assertTrue(Rac1ScreenLayout.mainMenuButtonTop(HEIGHT)
                        + Rac1ScreenLayout.BUTTON_HEIGHT
                        <= HEIGHT - Rac1ScreenLayout.MENU_FOOTER_HEIGHT,
                "The button of the mod must stand above the lines of the version of the game");
    }
}
