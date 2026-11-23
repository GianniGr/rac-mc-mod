package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.*;
import java.util.*;

/** Per-screen request state, no Minecraft inventory mutation or predicted purchase success. */
public final class Rac1ShopClientFlow {
    public enum Mode { BROWSE, WAIT_QUOTE, CONFIRM, WAIT_BUY, DENIED, MAXED, FAILURE, UNCERTAIN, CLOSED }
    private final UUID token;
    private long next=1,pending,age;
    private Rac1ShopNetwork.Command command;
    private Rac1ShopPurchases.Quote quote;
    private UUID presented;
    private Rac1ShopOffer requestedOffer=Rac1ShopOffer.BLASTER;
    private Rac1ShopDelivery.Kind requestedKind;
    private Rac1ShopPurchases.Status outcome;
    private Mode mode=Mode.BROWSE;
    private boolean deliberateBuy;
    private long deniedOpening;
    private final Set<Integer> activationKeys=new HashSet<>();
    public Rac1ShopClientFlow(UUID token) { this.token=Objects.requireNonNull(token); }
    public UUID token() { return token; }
    public Mode mode() { return mode; }
    public Rac1ShopPurchases.Quote quote() { return quote; }
    public Rac1ShopPurchases.Status outcome() { return outcome; }
    public long deniedOpening() { return deniedOpening; }
    /** Only the deliberate initial Buy edge may request the original denied-popup cue. */
    public Rac1ShopNetwork.Command beginBuy() {
        return beginBuy(Rac1ShopOffer.BLASTER);
    }
    public Rac1ShopNetwork.Command beginBuy(Rac1ShopOffer offer) {
        if (mode!=Mode.BROWSE) return null;
        var request=request(1,offer);
        deliberateBuy=request!=null;
        return request;
    }
    /** OS key-repeat is not a second confirmation edge. */
    public boolean activationDown(int key) { return activationKeys.add(key); }
    public void release(int key) { activationKeys.remove(key); }
    public void presented(UUID id) {
        if (mode==Mode.CONFIRM && quote!=null && quote.id().equals(id)) presented=id;
    }
    public Rac1ShopNetwork.Command request(int quantity) {
        return request(quantity,Rac1ShopOffer.BLASTER);
    }
    public Rac1ShopNetwork.Command request(int quantity,Rac1ShopOffer offer) {
        if (quantity<1 || quantity>200) throw new IllegalArgumentException("Invalid original unit quantity");
        Objects.requireNonNull(offer);
        if (mode==Mode.CLOSED || mode==Mode.UNCERTAIN || mode==Mode.WAIT_BUY || mode==Mode.WAIT_QUOTE) return null;
        if (next>Rac1ShopSessions.MAX_REQUESTS) { mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.EXHAUSTED; return null; }
        requestedKind=mode==Mode.CONFIRM && quote!=null && quote.offer()==offer ? quote.kind():null;
        quote=null; presented=null; age=0; pending=next++;
        deliberateBuy=false;
        requestedOffer=offer;
        command=new Rac1ShopNetwork.Command(token,pending,Rac1ShopNetwork.Action.QUOTE,quantity,null,offer);
        mode=Mode.WAIT_QUOTE; return command;
    }
    public Rac1ShopNetwork.Command confirm() {
        if (mode!=Mode.CONFIRM || quote==null || !quote.id().equals(presented) || age>=Rac1ShopPurchases.QUOTE_TICKS) return null;
        if (next>Rac1ShopSessions.MAX_REQUESTS) { mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.EXHAUSTED; quote=null; return null; }
        pending=next++; age=0;
        command=new Rac1ShopNetwork.Command(token,pending,Rac1ShopNetwork.Action.BUY,0,quote.id());
        mode=Mode.WAIT_BUY; return command;
    }
    public boolean receive(Rac1ShopNetwork.Reply reply) {
        if (!reply.token().equals(token) || mode==Mode.CLOSED) return false;
        if (reply.result().status()==Rac1ShopPurchases.Status.INVALID_SESSION) {
            mode=Mode.CLOSED; command=null; quote=null; return true;
        }
        if (command==null || reply.sequence()!=pending || mode!=Mode.WAIT_QUOTE && mode!=Mode.WAIT_BUY && mode!=Mode.UNCERTAIN) return false;
        var result=reply.result();
        boolean initialDenial=mode==Mode.WAIT_QUOTE && deliberateBuy && !result.replay()
                && (result.status()==Rac1ShopPurchases.Status.STOCK_CAP || result.status()==Rac1ShopPurchases.Status.INSUFFICIENT);
        if (mode==Mode.WAIT_QUOTE && result.status()==Rac1ShopPurchases.Status.QUOTED) {
            if (result.quote().quantity()!=command.quantity() || result.quote().offer()!=requestedOffer) return false;
            if (requestedKind!=null && result.quote().kind()!=requestedKind) {
                mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.STALE_OFFER; command=null; age=0;
                return true; // An ammo quantity edit must never become a weapon review.
            }
            quote=result.quote(); mode=Mode.CONFIRM;
        } else if ((mode==Mode.WAIT_BUY || mode==Mode.UNCERTAIN) && result.status()==Rac1ShopPurchases.Status.PURCHASED) {
            if (!result.quote().id().equals(command.quote()) || result.quote().offer()!=requestedOffer
                    || !result.quote().equals(quote)) return false;
            quote=null; mode=Mode.BROWSE;
        } else {
            quote=null;
            mode=switch (result.status()) {
                case INSUFFICIENT -> Mode.DENIED;
                case STOCK_CAP -> Mode.MAXED;
                default -> Mode.FAILURE;
            };
        }
        if (initialDenial) deniedOpening++;
        deliberateBuy=false;
        outcome=result.status(); command=null; age=0; return true;
    }
    /** Retransmit the EXACT request, never a fresh sequence after an uncertain outcome. */
    public Rac1ShopNetwork.Command tick() {
        age++;
        if (command!=null && (age==40 || age==80)) return command;
        if (command!=null && age>=120) {
            if (command.action()==Rac1ShopNetwork.Action.BUY) mode=Mode.UNCERTAIN;
            else { mode=Mode.FAILURE; command=null; quote=null; }
        }
        if (mode==Mode.CONFIRM && age>=Rac1ShopPurchases.QUOTE_TICKS) { quote=null; mode=Mode.FAILURE; outcome=Rac1ShopPurchases.Status.STALE_QUOTE; }
        return null;
    }
    public void cancel() {
        if (mode==Mode.WAIT_BUY || mode==Mode.UNCERTAIN) return;
        quote=null; presented=null; command=null; outcome=null; deliberateBuy=false; mode=Mode.BROWSE;
    }
    public Rac1ShopNetwork.Command close() {
        mode=Mode.CLOSED; quote=null; presented=null; command=null; deliberateBuy=false;
        return new Rac1ShopNetwork.Command(token,0,Rac1ShopNetwork.Action.CLOSE,0,null);
    }
}
