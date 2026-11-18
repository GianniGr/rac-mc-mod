package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Shared 2D texture lifecycle: cache replacement and resource reload release all old textures. */
public final class Rac1HudTextures {
    private static final Rac1HudTextureRegistry<ResourceLocation> TEXTURES =
            new Rac1HudTextureRegistry<>(Rac1ClientTextures::release);
    private Rac1HudTextures() {}
    public static ResourceLocation get(Rac1CacheContent content, String key, Rac1Texture texture) {
        return TEXTURES.get(content, key, () -> Rac1ClientTextures.registerClamped("hud_" + key, texture));
    }
    public static void release() {
        TEXTURES.clear();
    }
}
