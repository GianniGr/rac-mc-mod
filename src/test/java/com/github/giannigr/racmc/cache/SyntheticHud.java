package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.*;
import java.util.*;

/** Entirely synthetic 2D fixtures; not extracted original pixels. */
final class SyntheticHud {
    static Rac1HudAssets assets() {
        var sprite = new Rac1Texture(32, 32, new byte[32 * 32 * 4]);
        byte[] metrics = new byte[232 * 4];
        for (char c : "0123456789'.".toCharArray()) metrics[c * 4 + 3] = 13;
        var font = new Rac1HudFont(new Rac1Texture(256, 128, new byte[256 * 128 * 4]), metrics);
        Map<String, Integer> properties = new LinkedHashMap<>();
        for (String key : Rac1BoltHudDefinition.PROPERTIES) properties.put(key, 0);
        properties.put("digitWidth", 13); properties.put("top", 18);
        properties.put("openSteps", 8); properties.put("textSteps", 8); properties.put("uiHz", 60);
        return new Rac1HudAssets(Map.of("bolt", Collections.nCopies(30, sprite),
                "bolt_panel", Collections.nCopies(2, sprite)), Map.of("regular", font), properties);
    }
}
