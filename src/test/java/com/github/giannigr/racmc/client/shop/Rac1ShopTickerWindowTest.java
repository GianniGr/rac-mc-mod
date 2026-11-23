package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.Rac1HudFont;
import com.github.giannigr.racmc.asset.shop.Rac1ShopLedFont;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopTickerWindowTest {
    private Rac1HudFont font() {
        byte[] m = new byte[928];
        for (int c = 32; c < 127; c++) m[c * 4 + 3] = 10;
        return new Rac1HudFont(new Rac1Texture(256, 128, new byte[256 * 128 * 4]), m);
    }
    @Test void tickerUsesOriginalPrefixPackedCellsAdvancesAndBlinkControl() {
        int[] cells = new int[64], advances = new int[64];
        Arrays.fill(cells, -1); Arrays.fill(advances, 9);
        cells['A' - 32] = (36 * 16 << 16) | 9 * 16; advances['A' - 32] = 10;
        var led = new Rac1ShopLedFont(cells, advances);
        var line = Rac1ShopTickerLayout.line(led, new byte[]{'b','A',' ','A'}, 300, false);
        assertEquals(382, line.width()); assertEquals(1, line.glyphs().size());
        assertEquals(new Rac1ShopTickerLayout.Glyph(62, 8, 9, 36), line.glyphs().getFirst());
        assertEquals(2, Rac1ShopTickerLayout.line(led, new byte[]{'b','A',' ','A'}, 300, true).glyphs().size());
        assertEquals(324, Rac1ShopTickerLayout.line(led, new byte[]{'a',(byte)174}, 0, true).width());
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopTickerLayout.line(led, new byte[]{1}, 0, true));
    }
    @Test void originalWindowWrapsAtSpacesSplitsLongWordsAndCentersWithScissor() {
        var window = new Rac1ShopTextWindow.Window(0, 0, 100, 100, 50, 50, 16, true, true);
        var layout = Rac1ShopTextWindow.layout(font(), "AAAA BBBB CCCC".getBytes(java.nio.charset.StandardCharsets.US_ASCII), window);
        assertEquals(2, layout.lines().size());
        assertEquals(90, layout.lines().get(0).width());
        assertEquals(5, layout.lines().get(0).x()); assertEquals(34, layout.lines().get(0).y());
        assertEquals(32, layout.height()); assertEquals(window, layout.scissor());
        var longWord = Rac1ShopTextWindow.layout(font(), "AAAAAAAAAAAA".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                new Rac1ShopTextWindow.Window(0,0,40,100,0,0,16,false,false));
        assertEquals(3, longWord.lines().size());
        for (var line : longWord.lines()) assertEquals(40, line.width());
    }
    @Test void originalNewlineColorCarryAndVerticalLineSkipRemainExplicit() {
        var layout = Rac1ShopTextWindow.layout(font(), new byte[]{12,'A',1,'B'}, new Rac1ShopTextWindow.Window(
                0,0,100,16,0,-32,16,false,false));
        assertEquals(2, layout.lines().size()); assertEquals(4, layout.lines().get(1).colorSlot());
        assertFalse(layout.lines().get(0).visible()); assertTrue(layout.lines().get(1).visible());
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopTextWindow.layout(font(), new byte[2049], layout.scissor()));
        assertThrows(IllegalArgumentException.class, () -> new Rac1ShopTextWindow.Window(0,0,1,1,0,0,0,false,false));
    }
}
