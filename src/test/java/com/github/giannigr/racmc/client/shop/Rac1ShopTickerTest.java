package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopTickerTest {
    @Test void primaryBlinkHasTenOffFramesInEachFortyFrameReferencePeriod() {
        var strings=new Rac1ShopText(Map.of(20000,new byte[]{'b','A'},20001,new byte[]{'B'}));
        var ticker=new Rac1ShopTicker(font(),strings,ids(),bound -> 0);
        assertTrue(ticker.line().glyphs().isEmpty());
        ticker.advance(9); assertTrue(ticker.line().glyphs().isEmpty());
        ticker.advance(1); assertEquals(1,ticker.line().glyphs().size());
        ticker.advance(30); assertTrue(ticker.line().glyphs().isEmpty());
    }
    private Rac1ShopLedFont font() {
        int[] cells=new int[64],advance=new int[64]; Arrays.fill(cells,-1); Arrays.fill(advance,1);
        cells['A'-32]=0; cells['B'-32]=9*16;
        return new Rac1ShopLedFont(cells,advance);
    }
    private int[] ids() { int[] ids=new int[24]; Arrays.fill(ids,20000); ids[23]=20001; return ids; }
    private Rac1ShopText text() { return new Rac1ShopText(Map.of(20000,new byte[]{'A'},20001,new byte[]{'B'})); }
    @Test void originalWeightedChoicesPrefixAndTwoPixelStepsStayPrivate() {
        var choice=new AtomicInteger();
        var a=new Rac1ShopTicker(font(),text(),ids(),bound -> { assertEquals(24,bound); return choice.getAndSet(23); });
        var b=new Rac1ShopTicker(font(),text(),ids(),bound -> 0);
        assertEquals(20000,a.id()); assertEquals(36,a.line(true).glyphs().getFirst().x());
        a.advance(3); assertEquals(6,a.scroll()); assertEquals(30,a.line(true).glyphs().getFirst().x());
        assertEquals(0,b.scroll()); assertEquals(20000,b.id());
        a.advance(16); assertEquals(20000,a.id());
        a.advance(171); assertEquals(380,a.scroll()); // source lifetime is strlen*20, not glyph extent
        a.advance(1); assertEquals(20001,a.id()); assertEquals(0,a.scroll());
        assertEquals(20000,b.id());
    }
    @Test void constructorCopiesWeightedReferencesAndRejectsUnboundedClockOrBadChoice() {
        int[] ids=ids(); var ticker=new Rac1ShopTicker(font(),text(),ids,bound -> 0);
        ids[0]=29999; ticker.advance(191); assertEquals(20000,ticker.id());
        assertThrows(IllegalArgumentException.class,() -> ticker.advance(-1));
        assertThrows(IllegalArgumentException.class,() -> ticker.advance(241));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopTicker(font(),text(),ids(),bound -> 24));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopTicker(font(),text(),new int[1],bound -> 0));
    }
}
