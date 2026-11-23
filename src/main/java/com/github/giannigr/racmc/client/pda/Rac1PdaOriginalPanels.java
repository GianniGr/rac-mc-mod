package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.client.shop.*;
import java.util.function.IntUnaryOperator;

/** Exact production panel construction, also exercised under a private hidden test context. */
public final class Rac1PdaOriginalPanels implements AutoCloseable {
    private final Rac1ShopActorDrawing.PresenterTarget presenter;
    private final Rac1ShopTickerDrawing.Target tickerTarget;
    private final Rac1ShopTicker ticker;
    private final float idleDuration;
    private long frames;
    private boolean closed;

    public Rac1PdaOriginalPanels(Rac1PdaOriginalSource source,IntUnaryOperator choose) {
        var graphics=source.graphics();
        var idle=java.util.Arrays.stream(graphics.presenter().animated().animations())
                .filter(a -> a.name().equals("sequence_2")).findFirst().orElseThrow();
        idleDuration=idle.times()[idle.keyCount()-1];
        if (!(idleDuration>0)) throw new IllegalArgumentException("Original presenter idle duration missing");
        // Scene text owns the weighted ticker references. graphics.text() is only button/product labels.
        ticker=new Rac1ShopTicker(graphics.shop().sceneTables().led(),graphics.shop().text(),
                graphics.shop().sceneTables().tickerIds(),choose);
        presenter=new Rac1ShopActorDrawing.PresenterTarget(graphics);
        try { tickerTarget=new Rac1ShopTickerDrawing.Target(graphics.led()); }
        catch (RuntimeException | Error failure) { presenter.close(); throw failure; }
    }
    public void advance(int frames) { ticker.advance(frames); this.frames+=frames; }
    public int tickerId() { return ticker.id(); }
    public Rac1ShopOrderedGpu.Readback presenterFrame() { return presenter.frame(2,(frames/60f)%idleDuration); }
    public Rac1ShopOrderedGpu.Readback tickerFrame() { return tickerTarget.frame(ticker.line()); }
    @Override public void close() {
        if (closed) return;
        closed=true;
        try { presenter.close(); } finally { tickerTarget.close(); }
    }
}
