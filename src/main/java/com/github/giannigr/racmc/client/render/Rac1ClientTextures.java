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
                image.setPixelRGBA(x, y, abgr(texture.pixel(x, y)));
            }
        }
        dynamic.setFilter(false, false);
        dynamic.upload();
        // A texture that is registered again - because the screen that drew it was built again -
        // must not be left in the texture manager of the game.
        release(location);
        Minecraft.getInstance().getTextureManager().register(location, dynamic);
        return location;
    }

    /**
     * Turns a colour of the game of blocks into the order the images of the game of blocks are
     * kept in.
     *
     * <p>The pixels of the textures of the game are kept in the order alpha, red, green and blue,
     * and the images of the game of blocks are kept in the order red, green, blue and alpha. The
     * method turns the colour of the game of blocks into the order of the images.
     *
     * @param argb the colour, with the alpha in the highest byte
     * @return the colour in the order of the images
     */
    private static int abgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    /**
     * Releases a texture of the game.
     *
     * <p>The texture manager of the game keeps the images of the textures that were registered
     * until they are released, so a screen that is built again has to release the textures of its
     * preview, or the memory of the images grows with every screen.
     *
     * @param location the place of the texture
     */
    public static void release(ResourceLocation location) {
        if (location == null) {
            return;
        }
        Minecraft.getInstance().getTextureManager().release(location);
    }
}
