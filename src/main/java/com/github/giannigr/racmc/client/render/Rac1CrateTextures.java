package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1CrateAsset;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The textures of the crates of the game in the texture manager of the game.
 *
 * <p>A crate of the game is drawn in two places: in the world and in the lists of the creative
 * mode. Both places use the same textures, so the textures are put into the texture manager of
 * the game once for every crate and not once for every renderer.
 *
 * <p>The textures are put into the texture manager again when the content of the cache of the
 * assets changes, and the textures of the content that is not the one of the moment are released,
 * so that the images of the textures do not stay in the texture manager.
 */
public final class Rac1CrateTextures {

    /** The textures of every crate, by the key of the crate. */
    private static final Map<String, ResourceLocation[]> TEXTURES = new HashMap<>();

    /** The content of the cache the textures belong to. */
    private static Rac1CacheContent source;

    private Rac1CrateTextures() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The places of the textures of a crate.
     *
     * @param content the content of the cache of the assets
     * @param crate the crate of the game
     * @return the places of the textures of the crate, one per material
     */
    public static synchronized ResourceLocation[] of(Rac1CacheContent content,
                                                      Rac1CrateAsset crate) {
        if (source != content) {
            release();
            source = content;
        }
        ResourceLocation[] locations = TEXTURES.get(crate.key());
        if (locations == null) {
            locations = register(crate);
            TEXTURES.put(crate.key(), locations);
        }
        return locations;
    }

    /**
     * Puts the textures of a crate into the texture manager of the game.
     *
     * @param crate the crate of the game
     * @return the places of the textures
     */
    private static ResourceLocation[] register(Rac1CrateAsset crate) {
        ResourceLocation[] locations = new ResourceLocation[crate.textures().length];
        for (int index = 0; index < locations.length; index++) {
            locations[index] = Rac1ClientTextures.register("crate_" + crate.key() + "_" + index,
                    crate.textures()[index]);
        }
        return locations;
    }

    /**
     * Releases the textures of the crates that were drawn.
     */
    private static void release() {
        for (ResourceLocation[] locations : TEXTURES.values()) {
            for (ResourceLocation location : locations) {
                Rac1ClientTextures.release(location);
            }
        }
        TEXTURES.clear();
    }
}
