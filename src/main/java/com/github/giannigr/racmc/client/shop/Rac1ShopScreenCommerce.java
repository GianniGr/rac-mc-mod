package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.UUID;

/** Actual kiosk screen's offer/quantity adapter. Quote facts alone authorize a review. */
public final class Rac1ShopScreenCommerce {
    private final Rac1ShopClientFlow flow;
    private final Rac1CommerceSelection catalog;
    private final Rac1ShopQuantity quantity=new Rac1ShopQuantity();
    public Rac1ShopScreenCommerce(UUID token,boolean bombRegistered,boolean starterAvailable) {
        flow=new Rac1ShopClientFlow(token);
        catalog=new Rac1CommerceSelection(false,bombRegistered,starterAvailable);
    }
    public Rac1ShopClientFlow flow() { return flow; }
    public Rac1CommerceSelection catalog() { return catalog; }
    public Rac1ShopQuantity quantity() { return quantity; }
    public boolean move(int direction) {
        if (flow.mode()!=Rac1ShopClientFlow.Mode.BROWSE || !catalog.move(direction)) return false;
        quantity.clear(); return true;
    }
    public Rac1ShopNetwork.Command request() {
        if (flow.mode()!=Rac1ShopClientFlow.Mode.BROWSE) return null;
        catalog.requested(); return flow.beginBuy(catalog.offer());
    }
    public boolean receive(Rac1ShopNetwork.Reply packet,long balance) {
        var previous=flow.mode();
        if (!flow.receive(packet)) return false;
        if (flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM) quantity.accept(flow.quote(),balance);
        else quantity.clear();
        if (Rac1ShopPresenter.purchased(previous,flow.mode(),flow.outcome())) catalog.purchased(packet.result().quote());
        return true;
    }
    public Rac1ShopNetwork.Command requote(long tick) {
        if (flow.mode()!=Rac1ShopClientFlow.Mode.CONFIRM) return null;
        int requested=quantity.request(tick);
        return requested==0 ? null:flow.request(requested,catalog.offer());
    }
    public boolean adjust(int delta) {
        return catalog.offer()!=Rac1ShopOffer.BOMB_STARTER
                && (flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM || flow.mode()==Rac1ShopClientFlow.Mode.WAIT_QUOTE)
                && quantity.adjust(delta);
    }
}
