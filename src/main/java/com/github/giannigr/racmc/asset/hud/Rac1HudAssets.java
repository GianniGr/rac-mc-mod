package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.*;

/** Reusable converted 2D asset set. No ISO ranges or proprietary source bytes are cached. */
public record Rac1HudAssets(Map<String, List<Rac1Texture>> sprites,
                           Map<String, Rac1HudFont> fonts, Map<String, Integer> properties) {
    public Rac1HudAssets {
        var copy = new LinkedHashMap<String, List<Rac1Texture>>();
        sprites.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        sprites = Collections.unmodifiableMap(copy);
        fonts = Collections.unmodifiableMap(new LinkedHashMap<>(fonts));
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
    public static Rac1HudAssets empty() { return new Rac1HudAssets(Map.of(), Map.of(), Map.of()); }
}
