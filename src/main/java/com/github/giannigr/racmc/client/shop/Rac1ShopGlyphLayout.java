package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudFont;
import com.github.giannigr.racmc.asset.shop.Rac1ShopText;
import java.util.*;

/** Original byte-font single-line primitives (§3.2 hud_text); no native font substitution.
 * Word/window wrapping and LED ticker are separate, not approximated here. Colors are GS
 * packed AABBGGRR, not Minecraft ARGB; the eventual drawing adapter must convert them. */
public final class Rac1ShopGlyphLayout {
    private Rac1ShopGlyphLayout() {}
    private static final int[] COLORS = {0, 0xe07070, 0x40a040, 0xa060a0, 0x4080c0, 0, 0, 0};
    public record Glyph(int x, int y, int width, int height, int u, int v, int gsColor) {}
    public record Line(List<Glyph> glyphs, int advance) {
        public Line { glyphs = List.copyOf(glyphs); }
    }

    public static Line line(Rac1HudFont font, byte[] text, int gsColor, boolean colorCodes) {
        Objects.requireNonNull(font); Objects.requireNonNull(text);
        if (text.length > Rac1ShopText.MAX_STRING) throw new IllegalArgumentException("Oversized original text line");
        byte[] metrics = font.metrics();
        List<Glyph> glyphs = new ArrayList<>();
        int x = 0, color = gsColor;
        for (byte value : text) {
            int c = value & 255;
            if (c == 0) break;
            if (c == 1) throw new IllegalArgumentException("Original text window/newline layout required");
            if (c >= 8 && c <= 15) {
                if (colorCodes) color = c == 8 ? gsColor : (color & 0xff000000) | COLORS[c - 8];
                continue;
            }
            int at = entry(metrics, c);
            int advance = metrics[at + 3];
            if (advance == 0) continue;
            if (c >= 0x80 && c <= 0xa7) {
                int accent = entry(metrics, c + 0x40);
                glyphs.add(glyph(font, metrics, accent, x + metrics[accent + 3],
                        metrics[accent + 2], 16, color));
            }
            if (c < 0x20) {
                int grey = ((color & 255) + ((color >>> 8) & 255) + ((color >>> 16) & 255)) / 3;
                glyphs.add(glyph(font, metrics, at, x, metrics[at + 2], 24,
                        (color & 0xff000000) | grey * 0x010101));
            } else if (c > 0x20) {
                glyphs.add(glyph(font, metrics, at, x, metrics[at + 2], 16, color));
            }
            x += advance;
        }
        return new Line(glyphs, x);
    }
    private static int entry(byte[] metrics, int c) {
        if (c * 4 + 3 >= metrics.length) throw new IllegalArgumentException("Unsupported original font byte " + c);
        return c * 4;
    }
    private static Glyph glyph(Rac1HudFont font, byte[] metrics, int at, int x, int y, int width, int color) {
        int u = metrics[at] & 255, v = metrics[at + 1] & 255;
        if (u + width > font.atlas().width() || v + 16 > font.atlas().height())
            throw new IllegalArgumentException("Original glyph outside imported atlas");
        return new Glyph(x, y, width, 16, u, v, color);
    }
}
