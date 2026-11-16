package com.github.giannigr.racmc.asset.hud;

/** Per-HUD selectors, separate from the shared page/font/cache formats. */
public final class Rac1BoltHudDefinition {
    public static final int ICON = 0x754F, PANEL = 0x7580;
    public static final String FONT = "regular";
    public static final String[] PROPERTIES = {"textClear", "textOpaque", "textX", "textY",
            "separatorX", "separatorY", "dotX", "dotY", "digitWidth"};
    private Rac1BoltHudDefinition() {}
    public static boolean complete(Rac1HudAssets assets) {
        var icons = assets.sprites().get("bolt");
        var panel = assets.sprites().get("bolt_panel");
        var font = assets.fonts().get(FONT);
        if (icons == null || icons.size() != 30 || panel == null || panel.size() != 2 || font == null)
            return false;
        for (String property : PROPERTIES) if (!assets.properties().containsKey(property)) return false;
        for (char c : "0123456789'.".toCharArray()) if (font.advance(c) <= 0) return false;
        for (String key : new String[]{"openSteps", "textSteps", "uiHz"}) {
            Integer value = assets.properties().get(key);
            if (value == null || value < 1 || value > 120) return false;
        }
        for (var texture : icons) if (texture.width() != 32 || texture.height() != 32) return false;
        for (var texture : panel) if (texture.width() != 32 || texture.height() != 32) return false;
        return assets.properties().get("digitWidth") > 0 && assets.properties().get("digitWidth") <= 64
                && assets.properties().containsKey("top");
    }
}
