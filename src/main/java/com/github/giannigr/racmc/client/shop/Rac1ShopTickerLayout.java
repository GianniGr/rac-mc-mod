package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopLedFont;
import com.github.giannigr.racmc.asset.shop.Rac1ShopText;
import java.util.*;

/** Original LED primitives: 9x9 source cells drawn18x18 at y8; 'b' blinks the next glyph. */
public final class Rac1ShopTickerLayout {
    private Rac1ShopTickerLayout() {}
    public record Glyph(int x, int y, int u, int v) {}
    public record Line(List<Glyph> glyphs, int width) {
        public Line { glyphs = List.copyOf(glyphs); }
    }
    /** Blink phase is supplied by the scene clock; this math never consumes gameplay RNG. */
    public static Line line(Rac1ShopLedFont font, byte[] text, int scroll, boolean blinkVisible) {
        if (text.length > Rac1ShopText.MAX_STRING || scroll < 0 || scroll > 1 << 20)
            throw new IllegalArgumentException("Invalid ticker line/scroll");
        int advance = 18 * font.advance(' ') * 2; // original18-space prefix
        List<Glyph> glyphs = new ArrayList<>();
        boolean blink = false;
        for (byte b : text) {
            int c = b & 255;
            if (c == 0) break;
            if (c == 'b') { blink = true; continue; }
            // Original LED routine checks byte-0x20 < 59; high bytes (including174 in
            // retail ticker strings) skip without a glyph or advance. Never transliterate.
            if (c > Rac1ShopLedFont.LAST) continue;
            if (c < Rac1ShopLedFont.FIRST)
                throw new IllegalArgumentException("Unsupported control byte in original LED text");
            int cell = font.cell(c);
            if (cell == -1 && c != ' ') continue;
            if (cell != -1 && (!blink || blinkVisible))
                glyphs.add(new Glyph(advance - scroll, 8, (cell & 65535) / 16, (cell >>> 16) / 16));
            advance += font.advance(c) * 2;
            blink = false;
        }
        return new Line(glyphs, advance);
    }
}
