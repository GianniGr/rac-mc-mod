package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.hud.Rac1HudFont;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopGlyphLayoutTest {
    private Rac1HudFont font() {
        byte[] metrics = new byte[928];
        for (int c : new int[]{'A', ' ', 0x10, 0x80}) metrics[c * 4 + 3] = 10;
        metrics[0xc0 * 4 + 3] = 3; metrics[0xc0 * 4 + 2] = -4;
        return new Rac1HudFont(new Rac1Texture(256, 128, new byte[256 * 128 * 4]), metrics);
    }
    @Test void originalPadAccentSpacingColorAndResetRemainDistinct() {
        var line = Rac1ShopGlyphLayout.line(font(), new byte[]{0x10, ' ', 0x0c, (byte)0x80, 8, 'A'}, 0x80112233, true);
        assertEquals(40, line.advance()); assertEquals(4, line.glyphs().size());
        assertEquals(24, line.glyphs().get(0).width());
        assertEquals(0x80222222, line.glyphs().get(0).gsColor());
        assertEquals(23, line.glyphs().get(1).x()); assertEquals(-4, line.glyphs().get(1).y());
        assertEquals(0x804080c0, line.glyphs().get(2).gsColor());
        assertEquals(0x80112233, line.glyphs().get(3).gsColor());
        assertThrows(UnsupportedOperationException.class, () -> line.glyphs().clear());
    }
    @Test void controlCodesDoNotBecomeNativeGlyphsAndMalformedInputsFail() {
        var line = Rac1ShopGlyphLayout.line(font(), new byte[]{0x0c, 'A', 0}, 123, false);
        assertEquals(10, line.advance()); assertEquals(123, line.glyphs().getFirst().gsColor());
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopGlyphLayout.line(font(), new byte[]{1}, 0, true));
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopGlyphLayout.line(font(), new byte[]{(byte)255}, 0, true));
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopGlyphLayout.line(font(), new byte[2049], 0, true));
        byte[] bad = font().metrics(); bad[0x10 * 4] = (byte)250;
        var malformed = new Rac1HudFont(font().atlas(), bad);
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopGlyphLayout.line(malformed, new byte[]{0x10}, 0, true));
    }
}
