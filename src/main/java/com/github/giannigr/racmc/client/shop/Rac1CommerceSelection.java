package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.*;

/** Supplemental MC catalog, not an invented original retail row. No transaction authority. */
public final class Rac1CommerceSelection {
    private final boolean portable,bombRegistered;
    private Rac1ShopOffer selected=Rac1ShopOffer.BLASTER;
    private Rac1ShopPurchases.Quote receipt;
    public Rac1CommerceSelection(boolean portable,boolean bombRegistered) {
        this.portable=portable; this.bombRegistered=bombRegistered;
    }
    public Rac1ShopOffer offer() { return selected; }
    public boolean blaster() { return selected==Rac1ShopOffer.BLASTER; }
    public void requested() { receipt=null; }
    public List<Rac1ShopOffer> rows() {
        if (!bombRegistered) return List.of(Rac1ShopOffer.BLASTER);
        return List.of(Rac1ShopOffer.BLASTER,Rac1ShopOffer.BOMB_AMMO);
    }
    /** Caller must cancel any displayed quote first; never call while a request/receipt is pending. */
    public void cycle() { var rows=rows(); selected=rows.get((rows.indexOf(selected)+1)%rows.size()); receipt=null; }
    /** Original short-list browsing has endpoints, not wraparound or a purchase side effect. */
    public boolean move(int direction) {
        if (direction!=1 && direction!=-1) throw new IllegalArgumentException("Browse direction");
        var rows=rows();
        int index=rows.indexOf(selected),next=Math.clamp(index+direction,0,rows.size()-1);
        if (index==next) return false;
        selected=rows.get(next); receipt=null; return true;
    }
    public void purchased(Rac1ShopPurchases.Quote quote) {
        receipt=quote;
    }
    public String label() {
        return switch (selected) {
            case BLASTER -> portable ? "Blaster ammo refill: 10 bolts/round; cap 200":"Blaster";
            case BOMB_AMMO -> portable ? "Bomb Glove refill: 25 bolts/round; cap 40":"Bomb Glove";
            case BOMB_STARTER -> throw new IllegalStateException("Retired offer");
        };
    }
    public List<String> details(Rac1ShopPurchases.Quote quote) {
        if (quote==null || quote.offer()!=selected) return receipt==null ? List.of(label(),
                        !portable && !blaster() ? "Unowned: 1000 bolts, top up to 10 | Owned: 5/round, cap 40"
                                : "Enter: request quote (no charge)")
                :List.of(label(),"Server receipt: "+(receipt.kind()==Rac1ShopDelivery.Kind.WEAPON ? "1 weapon + ":"")+receipt.deliveredAmmo()+" rounds",
                        "Paid "+receipt.price()+" bolts",
                        "Enter: request a NEW quote (no automatic repeat)");
        if (selected==Rac1ShopOffer.BOMB_AMMO && quote.kind()==Rac1ShopDelivery.Kind.WEAPON)
            return List.of("MC paid weapon offer",
                    "Bomb Glove x1 + "+quote.deliveredAmmo()+" rounds (top up to 10)",
                    "TOTAL: "+quote.price()+" bolts",
                    "Enter / original Yes: confirm; Backspace / No: cancel");
        return List.of(label(),"Current: "+quote.currentAmmo()+"; deliver: "+quote.deliveredAmmo()+" rounds",
                "TOTAL: "+quote.price()+" bolts; quantity: "+quote.quantity(),
                "Enter / original Yes: confirm; Backspace / No: cancel");
    }
}
