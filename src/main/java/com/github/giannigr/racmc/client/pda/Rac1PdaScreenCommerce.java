package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.client.shop.Rac1CommerceSelection;
import com.github.giannigr.racmc.shop.*;
import java.util.UUID;

/** Screen's production catalog/quantity/receipt adapter; no world, balance prediction or local delivery. */
public final class Rac1PdaScreenCommerce {
    private final Rac1PdaClientFlow flow;
    private final Rac1CommerceSelection catalog;
    private int quantity=1;
    public Rac1PdaScreenCommerce(UUID token,boolean bombRegistered) {
        flow=new Rac1PdaClientFlow(token);
        catalog=new Rac1CommerceSelection(true,bombRegistered,false);
    }
    public Rac1PdaClientFlow flow() { return flow; }
    public Rac1CommerceSelection catalog() { return catalog; }
    public int quantity() { return quantity; }
    public boolean move(int direction) {
        if (flow.mode()!=Rac1PdaClientFlow.Mode.BROWSE || !catalog.move(direction)) return false;
        quantity=1; return true;
    }
    public Rac1PdaNetwork.Command request() {
        if (!flow.editable()) return null;
        catalog.requested(); quantity=1;
        return flow.request(quantity,catalog.offer());
    }
    public Rac1PdaNetwork.Command adjust(int delta,long balance) {
        if (flow.mode()!=Rac1PdaClientFlow.Mode.CONFIRM) return null;
        var stock=catalog.offer().stock();
        int max=(int)Math.max(1,Math.min(stock.cap()-flow.quote().currentAmmo(),
                Math.max(0,balance)/stock.unitPrice(true)));
        int next=Math.clamp(quantity+delta,1,max);
        if (next==quantity) return null;
        flow.cancelQuote(); quantity=next;
        return flow.request(quantity,catalog.offer());
    }
    public boolean receive(Rac1PdaNetwork.Reply reply) {
        if (!flow.receive(reply)) return false;
        if (flow.outcome()==Rac1ShopPurchases.Status.PURCHASED) catalog.purchased(flow.lastPurchase());
        if (flow.quote()!=null) quantity=flow.quote().quantity();
        return true;
    }
    public Rac1PdaNetwork.Command confirm() { return flow.confirm(); }
}
