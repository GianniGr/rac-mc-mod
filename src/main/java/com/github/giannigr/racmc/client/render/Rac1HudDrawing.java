package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.hud.*;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** Reusable textured 2D sprites and atlas glyphs; never draws substitute native font/art. */
public final class Rac1HudDrawing {
    private Rac1HudDrawing() {}
    public static void sprite(GuiGraphics graphics, Rac1CacheContent content, String key, int frame,
                              int x, int y, int width, int height, boolean rotated, float alpha) {
        var texture = content.hud().sprites().get(key).get(frame);
        var location = Rac1HudTextures.get(content, key + "_" + frame, texture);
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(127 / 128F, 127 / 128F, 127 / 128F, alpha);
        graphics.pose().pushPose();
        try {
            if (rotated) {
                // Retail flipped strip maps TL=(tw,th), TR=(0,th), BL=(tw,0), BR=(0,0).
                graphics.pose().translate(x + width, y + height, 0);
                graphics.pose().scale(-1, -1, 1);
            } else graphics.pose().translate(x, y, 0);
            graphics.blit(location, 0, 0, width, height, 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    public static int width(Rac1HudFont font, String text) {
        int width = 0;
        for (char c : text.toCharArray()) width += font.advance(c);
        return width;
    }

    public static void textRight(GuiGraphics graphics, Rac1CacheContent content, String fontKey,
                                 String text, int right, int top, int ps2Color) {
        var font = content.hud().fonts().get(fontKey);
        var location = Rac1HudTextures.get(content, "font_" + fontKey, font.atlas());
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((ps2Color & 255) / 255F, ((ps2Color >>> 8) & 255) / 255F,
                ((ps2Color >>> 16) & 255) / 255F, Math.min(1F, ((ps2Color >>> 24) & 255) / 128F));
        try {
            int x = right - width(font, text);
            for (char c : text.toCharArray()) {
                graphics.blit(location, x, top + font.top(c), font.u(c), font.v(c),
                        16, 16, font.atlas().width(), font.atlas().height());
                x += font.advance(c);
            }
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
}
