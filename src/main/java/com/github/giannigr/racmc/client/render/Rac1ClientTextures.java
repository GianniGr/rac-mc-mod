package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The textures of the assets of the game in the game of blocks.
 *
 * <p>The textures of the game are 8 bit images with a palette. The converter turns them into 32
 * bit images, and this class puts them into the texture manager of the game so that they can be
 * drawn.
 */
public final class Rac1ClientTextures {

    /** The namespace the textures of the game are registered in. */
    public static final String NAMESPACE = "racmc";

    private Rac1ClientTextures() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a texture of the game and returns the place it can be found at.
     *
     * @param name the name of the texture, for example {@code static_0}
     * @param texture the texture
     * @return the place of the texture
     */
    public static ResourceLocation register(String name, Rac1Texture texture) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(NAMESPACE,
                "textures/" + name + ".png");
        DynamicTexture dynamic = new DynamicTexture(texture.width(), texture.height(), true);
        NativeImage image = dynamic.getPixels();
        for (int y = 0; y < texture.height(); y++) {
            for (int x = 0; x < texture.width(); x++) {
                image.setPixelRGBA(x, y, texture.pixel(x, y));
            }
        }
        dynamic.setFilter(false, false);
        dynamic.upload();
        Minecraft.getInstance().getTextureManager().register(location, dynamic);
        return location;
    }
}
