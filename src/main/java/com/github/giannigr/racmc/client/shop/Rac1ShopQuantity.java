package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.Rac1ShopDelivery;
import com.github.giannigr.racmc.shop.Rac1ShopPurchases;

/** Local intent only; at most one outstanding quote and at most five new requests/second. */
public final class Rac1ShopQuantity {
    private int desired,max,quoted;
    private long nextRequest;
    private boolean active,pending;
    private com.github.giannigr.racmc.shop.Rac1ShopOffer offer;
    public void accept(Rac1ShopPurchases.Quote quote,long balance) {
        if (quote==null || quote.kind()!=Rac1ShopDelivery.Kind.AMMO) { clear(); return; }
        var stock=quote.offer().stock();
        max=(int)Math.max(1,Math.min(stock.cap()-quote.currentAmmo(),balance/stock.unitPrice(false)));
        if (!active || offer!=quote.offer()) desired=quote.quantity();
        offer=quote.offer();
        desired=Math.max(1,Math.min(max,desired));
        quoted=quote.quantity(); active=true; pending=false;
    }
    public boolean adjust(int delta) {
        if (!active || delta==0) return false;
        int next=(int)Math.max(1,Math.min(max,(long)desired+delta));
        if (next==desired) return false;
        desired=next; return true;
    }
    /** Caller must also have CONFIRM and send the returned quantity through ClientFlow.request(). */
    public int request(long tick) {
        if (!active || pending || desired==quoted || tick<nextRequest) return 0;
        pending=true; nextRequest=tick+4; return desired;
    }
    public boolean matches(Rac1ShopPurchases.Quote quote) {
        return !active || !pending && quote!=null && quote.kind()==Rac1ShopDelivery.Kind.AMMO
                && quote.offer()==offer && quote.quantity()==desired;
    }
    public boolean active() { return active; }
    public int desired() { return desired; }
    public void clear() { active=false; pending=false; desired=quoted=max=0; nextRequest=0; offer=null; }
}
