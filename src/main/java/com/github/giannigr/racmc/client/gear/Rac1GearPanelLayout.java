package com.github.giannigr.racmc.client.gear;

/** Coordinates in GUI-scaled pixels; the second Belt position remains display-only. */
record Rac1GearPanelLayout(Box panel, Box back, Box backButton, Box aerial, Box hydro,
                            int beltTitleY, Box beltFirst, Box beltSecond, Box beltButton) {
    static final int WIDTH = 176;
    static final int TAB_HEIGHT = 20;
    static final int BELT_BUTTON_HEIGHT = 16;
    static final int BACK_TEXT_WIDTH = 144;
    static final int BACK_STATUS_OFFSET = 17;
    static final int SLOT_TEXT_INSET = 5;
    private static final int INSET = 10;
    private static final int GAP = 5;
    private static final int BACK_TOP = 22;
    private static final int SLOT_WIDTH = (WIDTH - 2 * INSET - GAP) / 2;
    static final int SLOT_TEXT_WIDTH = SLOT_WIDTH - 2 * SLOT_TEXT_INSET;

    record Box(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }

    static Rac1GearPanelLayout of(int width, int height, int lineHeight, int backLines,
                                 int upgradeLines, int beltHeadingLines, int beltSlotLines, int laterLines) {
        int backHeight = BACK_STATUS_OFFSET + backLines * lineHeight + 4 + TAB_HEIGHT + 5;
        int upgradeHeight = 2 * SLOT_TEXT_INSET + (upgradeLines + laterLines) * lineHeight + 3;
        int beltHeadingHeight = beltHeadingLines * lineHeight;
        int beltHeight = 2 * SLOT_TEXT_INSET + beltSlotLines * lineHeight
                + Math.max(laterLines * lineHeight, BELT_BUTTON_HEIGHT) + 3;
        int panelHeight = BACK_TOP + backHeight + GAP + upgradeHeight + GAP + beltHeadingHeight + 3 + beltHeight + 5;
        int x = (width - WIDTH) / 2;
        int y = Math.max(TAB_HEIGHT, (height - panelHeight + TAB_HEIGHT) / 2);
        var panel = new Box(x, y, WIDTH, panelHeight);
        var back = new Box(x + INSET, y + BACK_TOP, WIDTH - 2 * INSET, backHeight);
        var backButton = new Box(x + 16, back.bottom() - TAB_HEIGHT - 5, BACK_TEXT_WIDTH, TAB_HEIGHT);
        var aerial = new Box(back.x(), back.bottom() + GAP, SLOT_WIDTH, upgradeHeight);
        var hydro = new Box(aerial.right() + GAP, aerial.y(), SLOT_WIDTH, upgradeHeight);
        int beltTitle = aerial.bottom() + GAP;
        var beltFirst = new Box(aerial.x(), beltTitle + beltHeadingHeight + 3, SLOT_WIDTH, beltHeight);
        var beltSecond = new Box(hydro.x(), beltFirst.y(), SLOT_WIDTH, beltHeight);
        var beltButton = new Box(beltFirst.x() + SLOT_TEXT_INSET,
                beltFirst.bottom() - SLOT_TEXT_INSET - BELT_BUTTON_HEIGHT, SLOT_TEXT_WIDTH, BELT_BUTTON_HEIGHT);
        return new Rac1GearPanelLayout(panel, back, backButton, aerial, hydro, beltTitle, beltFirst, beltSecond, beltButton);
    }
}
