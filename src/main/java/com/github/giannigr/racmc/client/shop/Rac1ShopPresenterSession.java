package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.Rac1Animation;
import java.util.function.*;

/** Production screen adapter. Catalog eligibility is NOT the private scene's lifetime. */
public final class Rac1ShopPresenterSession implements AutoCloseable {
    private final Rac1ShopVoicePlayback voice;
    private Rac1ShopPresenter presenter;
    private boolean blaster=true;
    public Rac1ShopPresenterSession(BooleanSupplier live,Rac1ShopVoicePlayback.Backend backend) {
        voice=new Rac1ShopVoicePlayback(live,backend);
    }
    public Rac1ShopVoicePlayback voice() { return voice; }
    public Rac1ShopPresenter attach(Rac1Animation[] animations,Rac1ShopVoiceData data,IntUnaryOperator random) {
        if (presenter!=null) throw new IllegalStateException("Presenter already attached");
        return presenter=new Rac1ShopPresenter(animations,data,voice,random);
    }
    public void select(boolean blaster) {
        if (this.blaster==blaster) return;
        this.blaster=blaster;
        voice.cancel();
    }
    public Rac1ShopPresenter.Frame tick(int frames,boolean focused) {
        if (presenter==null) throw new IllegalStateException("Presenter not attached");
        return presenter.tick(frames,focused,blaster);
    }
    @Override public void close() { if (presenter!=null) presenter.close(); else voice.close(); }
}
