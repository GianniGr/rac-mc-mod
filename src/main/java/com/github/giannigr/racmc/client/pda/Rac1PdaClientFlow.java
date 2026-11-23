package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.shop.*;
import java.util.*;

/** Per-screen intent/receipt state. Never predicts a debit, grant or successful purchase. */
public final class Rac1PdaClientFlow {
    public enum Mode { BROWSE, WAIT_QUOTE, CONFIRM, WAIT_BUY, FAILURE, UNCERTAIN, CLOSED }
    private final UUID token;
    private Mode mode=Mode.BROWSE;
    private long next=1,age;
    private Rac1ShopNetwork.Command pending;
    private Rac1ShopPurchases.Quote quote,lastPurchase;
    private Rac1ShopPurchases.Status outcome;
    private UUID presented;
    private Rac1ShopOffer requestedOffer=Rac1ShopOffer.BLASTER;
    private final Set<Integer> keys=new HashSet<>();

    public Rac1PdaClientFlow(UUID token) { this.token=Objects.requireNonNull(token); }
    public UUID token() { return token; }
    public Mode mode() { return mode; }
    public Rac1ShopPurchases.Quote quote() { return quote; }
    public Rac1ShopPurchases.Quote lastPurchase() { return lastPurchase; }
    public Rac1ShopPurchases.Status outcome() { return outcome; }
    public boolean editable() { return mode==Mode.BROWSE || mode==Mode.FAILURE; }
    public boolean activationDown(int key) { return keys.add(key); }
    public void release(int key) { keys.remove(key); }
    public boolean canConfirm() { return mode==Mode.CONFIRM && quote!=null && quote.id().equals(presented); }
    public void presented(UUID id) {
        if (mode==Mode.CONFIRM && quote!=null && quote.id().equals(id)) presented=id;
    }
    public Rac1PdaNetwork.Command request(int quantity) {
        return request(quantity,Rac1ShopOffer.BLASTER);
    }
    public Rac1PdaNetwork.Command request(int quantity,Rac1ShopOffer offer) {
        if (quantity<1 || quantity>200) throw new IllegalArgumentException("Invalid PDA quantity");
        Objects.requireNonNull(offer);
        if (!offer.permitted(true)) throw new IllegalArgumentException("Portable ammo only");
        if (!editable()) return null;
        if (!budget()) return null;
        quote=null; presented=null; outcome=null; lastPurchase=null; age=0;
        requestedOffer=offer;
        pending=new Rac1ShopNetwork.Command(token,next++,Rac1ShopNetwork.Action.QUOTE,quantity,null,offer);
        mode=Mode.WAIT_QUOTE; return wrap(pending);
    }
    public Rac1PdaNetwork.Command confirm() {
        if (!canConfirm() || !budget()) return null;
        pending=new Rac1ShopNetwork.Command(token,next++,Rac1ShopNetwork.Action.BUY,0,quote.id());
        mode=Mode.WAIT_BUY; age=0; presented=null; return wrap(pending);
    }
    private boolean budget() {
        if (next<=Rac1ShopSessions.MAX_REQUESTS) return true;
        quote=null; mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.EXHAUSTED; return false;
    }
    public boolean receive(Rac1PdaNetwork.Reply reply) {
        if (!reply.token().equals(token) || mode==Mode.CLOSED) return false;
        var result=reply.result();
        if (result.status()==Rac1ShopPurchases.Status.INVALID_SESSION) {
            mode=Mode.CLOSED; quote=null; pending=null; presented=null; return true;
        }
        if (pending==null || reply.sequence()!=pending.sequence()) return false;
        // Rate-limited requests have not consumed a server sequence. Keep retrying the SAME intent.
        if (result.status()==Rac1ShopPurchases.Status.RATE_LIMIT) { age=0; return true; }
        if (result.status()==Rac1ShopPurchases.Status.QUOTED) {
            if (pending.action()!=Rac1ShopNetwork.Action.QUOTE || result.quote().quantity()!=pending.quantity()
                    || result.quote().offer()!=requestedOffer) return false;
            quote=result.quote(); presented=null; mode=Mode.CONFIRM;
        } else if (result.status()==Rac1ShopPurchases.Status.PURCHASED) {
            if (pending.action()!=Rac1ShopNetwork.Action.BUY || !result.quote().id().equals(pending.quote())
                    || result.quote().offer()!=requestedOffer || !result.quote().equals(quote)) return false;
            lastPurchase=result.quote(); quote=null; mode=Mode.BROWSE;
        } else {
            quote=null; mode=Mode.FAILURE;
        }
        pending=null; age=0; outcome=result.status(); return true;
    }
    public Rac1PdaNetwork.Command tick() {
        age++;
        if (pending!=null && (age==40 || age==80)) return wrap(pending);
        if (pending!=null && age>=120) mode=Mode.UNCERTAIN;
        if (mode==Mode.CONFIRM && age>=Rac1ShopPurchases.QUOTE_TICKS) {
            quote=null; presented=null; mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.STALE_QUOTE;
        }
        return null;
    }
    /** A quote reserves nothing. Cancelling it is local; close invalidates the whole owner session. */
    public void cancelQuote() {
        if (mode!=Mode.CONFIRM && mode!=Mode.FAILURE) return;
        quote=null; presented=null; outcome=null; mode=Mode.BROWSE;
    }
    public Rac1PdaNetwork.Command close() {
        if (mode==Mode.CLOSED && pending==null) return null;
        mode=Mode.CLOSED; pending=null; quote=null; presented=null; keys.clear();
        return wrap(new Rac1ShopNetwork.Command(token,0,Rac1ShopNetwork.Action.CLOSE,0,null));
    }
    private static Rac1PdaNetwork.Command wrap(Rac1ShopNetwork.Command command) {
        return new Rac1PdaNetwork.Command(command);
    }
}
