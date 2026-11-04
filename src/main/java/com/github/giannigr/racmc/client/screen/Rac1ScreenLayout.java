package com.github.giannigr.racmc.client.screen;

/**
 * The places of the screens of the mod inside the window of the game.
 *
 * <p>The class holds the arithmetic of the layout of the screens, apart from the widgets
 * themselves, so that the layout can be checked without a game. The screen of the disc image puts
 * its buttons in the upper part of the window and its report in a panel that fills the rest of
 * the window. A report that does not fit into the panel is scrolled, so that every line of it can
 * be read, and the buttons of the main menu stay above the lines of the version of the game, so
 * that they never cover them.
 *
 * <p>The narrowest window of the game is 320 by 240 pixels. The layout fits into it: the report
 * is never cut off, it is scrolled instead, and the drawing of the preview of the assets is left
 * out when the window is too small to hold it beside the report.
 */
public final class Rac1ScreenLayout {

    /** The distance between the content of a screen and the edge of the window. */
    public static final int MARGIN = 6;

    /** The distance between the text of the report and the frame of its panel. */
    public static final int PADDING = 4;

    /** The distance between two lines of text. */
    public static final int LINE_HEIGHT = 10;

    /** The height of a button. */
    public static final int BUTTON_HEIGHT = 20;

    /** The distance between two buttons and between a button and the panel of the report. */
    public static final int BUTTON_GAP = 4;

    /** The narrowest width of the text of a screen. */
    public static final int MIN_CONTENT_WIDTH = 140;

    /** The widest width of the text of a screen. */
    public static final int MAX_CONTENT_WIDTH = 260;

    /** The width of the button that picks the disc image. */
    public static final int PICK_BUTTON_WIDTH = 160;

    /** The width of the button that leaves the screen or that opens the settings. */
    public static final int SIDE_BUTTON_WIDTH = 100;

    /** The height of the drawing of the preview of the assets. */
    public static final int PREVIEW_HEIGHT = 120;

    /** The narrowest height of the drawing of the preview of the assets. */
    public static final int MIN_PREVIEW_HEIGHT = 60;

    /** The narrowest height of the panel of the report. */
    public static final int MIN_PANEL_HEIGHT = 50;

    /** The height of the lines of the version of the game and of the loader in the main menu. */
    public static final int MENU_FOOTER_HEIGHT = 20;

    /** The distance between the buttons of the mod and the footer of the main menu. */
    public static final int MENU_FOOTER_GAP = 4;

    /** The room that is kept between the buttons and the panel of the report. */
    public static final int PROGRESS_ROOM = 26;

    private Rac1ScreenLayout() {
    }

    /**
     * The width of the text of a screen.
     *
     * <p>The text never reaches over the edge of the window and never grows wider than the widest
     * text of the game of blocks, so that the screens of the mod look like the screens of the
     * game.
     *
     * @param screenWidth the width of the window of the game
     * @return the width of the text
     */
    public static int contentWidth(int screenWidth) {
        return Math.max(MIN_CONTENT_WIDTH, Math.min(MAX_CONTENT_WIDTH, screenWidth - 2 * MARGIN));
    }

    /**
     * The left edge of the text of a screen.
     *
     * @param screenWidth the width of the window of the game
     * @return the left edge, so that the text stands in the middle of the window
     */
    public static int contentLeft(int screenWidth) {
        return (screenWidth - contentWidth(screenWidth)) / 2;
    }

    /**
     * Whether the button that picks the disc image and the button beside it fit into one row.
     *
     * @param contentWidth the width of the text of the screen
     * @return {@code true} when both buttons fit next to each other
     */
    public static boolean sideBySide(int contentWidth) {
        return contentWidth >= PICK_BUTTON_WIDTH + SIDE_BUTTON_WIDTH;
    }

    /**
     * The top of the panel of the report, under the buttons of the screen.
     *
     * @param buttonsBottom the lower edge of the last button of the screen
     * @param progressActive whether the bar of the progress is drawn under the buttons
     * @return the top of the panel
     */
    public static int reportTop(int buttonsBottom, boolean progressActive) {
        return buttonsBottom + BUTTON_GAP + (progressActive ? PROGRESS_ROOM : 0);
    }

    /**
     * The top of the button of the mod in the main menu.
     *
     * <p>The main menu writes the version of the game and the version of the loader into its lower
     * left corner. The button of the mod stands above those two lines, so that it never covers
     * them and the player can read them.
     *
     * @param screenHeight the height of the window of the game
     * @return the top of the button
     */
    public static int mainMenuButtonTop(int screenHeight) {
        return screenHeight - MENU_FOOTER_HEIGHT - MENU_FOOTER_GAP - BUTTON_HEIGHT;
    }

    /**
     * The top of the second button of the mod in the main menu.
     *
     * @param screenHeight the height of the window of the game
     * @return the top of the button
     */
    public static int mainMenuUpperButtonTop(int screenHeight) {
        return mainMenuButtonTop(screenHeight) - BUTTON_HEIGHT - BUTTON_GAP;
    }

    /**
     * The places of the report and of the preview of the assets in a window.
     *
     * @param report the panel of the report
     * @param preview the area of the drawing of the preview, which is empty when there is none
     * @param visibleLines the number of lines of the report that fit into the panel
     * @param maxScroll the number of lines the report can be scrolled by
     */
    public record Layout(Area report, Area preview, int visibleLines, int maxScroll) {

        /**
         * Whether the preview of the assets is drawn.
         *
         * @return {@code true} when there is room for the preview
         */
        public boolean hasPreview() {
            return this.preview.height() > 0;
        }
    }

    /**
     * A rectangle of the window of the game.
     *
     * @param left the left edge
     * @param top the upper edge
     * @param right the right edge
     * @param bottom the lower edge
     */
    public record Area(int left, int top, int right, int bottom) {

        /**
         * The width of the area.
         *
         * @return the width
         */
        public int width() {
            return this.right - this.left;
        }

        /**
         * The height of the area.
         *
         * @return the height
         */
        public int height() {
            return this.bottom - this.top;
        }

        /**
         * Whether a point lies inside the area.
         *
         * @param x the first coordinate of the point
         * @param y the second coordinate of the point
         * @return {@code true} when the point lies inside the area
         */
        public boolean contains(double x, double y) {
            return x >= this.left && x < this.right && y >= this.top && y < this.bottom;
        }
    }

    /**
     * Finds the places of the report and of the preview of the assets.
     *
     * <p>The panel of the report fills the room that is left under the buttons of the screen. The
     * preview of the assets is only drawn when the cache of the assets is ready, and it is only
     * given room when the panel of the report keeps at least {@value #MIN_PANEL_HEIGHT} pixels
     * and the preview itself is at least {@value #MIN_PREVIEW_HEIGHT} pixels high. A report that
     * does not fit into the panel is scrolled: the panel shows the first lines of it and the
     * player can scroll to the last ones.
     *
     * @param screenWidth the width of the window of the game
     * @param screenHeight the height of the window of the game
     * @param reportTop the top of the panel of the report
     * @param lineCount the number of lines of the report
     * @param withPreview whether the preview of the assets is drawn
     * @return the places of the report and of the preview
     */
    public static Layout layout(int screenWidth, int screenHeight, int reportTop, int lineCount,
                                boolean withPreview) {
        int width = contentWidth(screenWidth);
        int left = (screenWidth - width) / 2;
        int bottom = screenHeight - MARGIN;
        int previewHeight = 0;
        if (withPreview) {
            int needed = lineCount * LINE_HEIGHT + 2 * PADDING;
            int spare = bottom - reportTop - needed - MIN_PANEL_HEIGHT;
            if (spare >= MIN_PREVIEW_HEIGHT) {
                previewHeight = Math.min(PREVIEW_HEIGHT, spare);
            }
        }
        int reportBottom = bottom - (previewHeight > 0 ? previewHeight + BUTTON_GAP : 0);
        int reportHeight = Math.max(0, reportBottom - reportTop);
        int inner = reportHeight - 2 * PADDING;
        int visible = Math.max(1, inner / LINE_HEIGHT);
        if (lineCount > visible) {
            // A line of the panel is kept for the hint that tells the player that the report can
            // be scrolled.
            visible = Math.max(1, (inner - LINE_HEIGHT) / LINE_HEIGHT);
        }
        int maxScroll = Math.max(0, lineCount - visible);
        Area report = new Area(left, reportTop, left + width, reportTop + reportHeight);
        Area preview = previewHeight > 0
                ? new Area(MARGIN, reportBottom + BUTTON_GAP, screenWidth - MARGIN, bottom)
                : new Area(0, 0, 0, 0);
        return new Layout(report, preview, visible, maxScroll);
    }
}
