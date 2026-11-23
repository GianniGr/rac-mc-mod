package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.*;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Private cosmetic scene clock: weighted source references, never player/world RNG. */
public final class Rac1ShopTicker {
    private final Rac1ShopLedFont font;
    private final Rac1ShopText text;
    private final int[] ids;
    private final IntUnaryOperator choose;
    private int id,scroll,lifetime;
    private long frame;
    public Rac1ShopTicker(Rac1ShopLedFont font,Rac1ShopText text,int[] ids,IntUnaryOperator choose) {
        this.font=Objects.requireNonNull(font); this.text=Objects.requireNonNull(text);
        if (ids.length!=24) throw new IllegalArgumentException("Original weighted ticker table required");
        this.ids=ids.clone(); this.choose=Objects.requireNonNull(choose);
        for (int entry:ids) text.get(entry);
        next();
    }
    /** Three 60Hz reference frames per non-pausing 20Hz client tick; not PAL frame certification. */
    public void advance(int frames) {
        if (frames<0 || frames>240) throw new IllegalArgumentException("Bounded ticker frame step required");
        for (int i=0;i<frames;i++) {
            frame++;
            if (scroll>=lifetime) next(); else scroll+=2;
        }
    }
    public Rac1ShopTickerLayout.Line line(boolean blinkVisible) {
        return Rac1ShopTickerLayout.line(font,text.get(id),scroll,blinkVisible);
    }
    /** Primary LED routine: period ScaleTicks30+ScaleTicks10, hidden during the first10. */
    public Rac1ShopTickerLayout.Line line() { return line(frame%40>=10); }
    public int id() { return id; }
    public int scroll() { return scroll; }
    private void next() {
        int selection=choose.applyAsInt(ids.length);
        if (selection<0 || selection>=ids.length) throw new IllegalArgumentException("Invalid cosmetic ticker choice");
        id=ids[selection]; scroll=0;
        byte[] bytes=text.get(id);
        if (Rac1ShopTickerLayout.line(font,bytes,0,true).width()<1)
            throw new IllegalArgumentException("Ticker requires positive source advance");
        lifetime=(18+bytes.length)*20; // primary strlen(prefix+text) versus scroll/20, not measured glyph width
    }
}
