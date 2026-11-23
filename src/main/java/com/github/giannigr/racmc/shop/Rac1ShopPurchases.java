package com.github.giannigr.racmc.shop;

import java.util.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/**
 * Main-thread purchase/session coordinator, called by the real server adapter.
 * Not a transport. Quotes reserve nothing; every buy revalidates live state.
 */
public final class Rac1ShopPurchases {
    public static final int QUOTE_TICKS=200;
    public enum Status { QUOTED, PURCHASED, INVALID_SESSION, STALE_REQUEST, CONFLICT, RATE_LIMIT,
        EXHAUSTED, INVALID_QUANTITY, STALE_QUOTE, STALE_OFFER, STOCK_CAP, FULL, INSUFFICIENT, STALE_BALANCE, COMMIT_REJECTED }
    public record Quote(UUID id,Rac1ShopDelivery.Kind kind,int quantity,long price,int currentArrows,
                        int deliveredArrows,long expires,Rac1ShopOffer offer) {
        public Quote(UUID id,Rac1ShopDelivery.Kind kind,int quantity,long price,int currentArrows,int deliveredArrows,long expires) {
            this(id,kind,quantity,price,currentArrows,deliveredArrows,expires,Rac1ShopOffer.BLASTER);
        }
        public int currentAmmo() { return currentArrows; }
        public int deliveredAmmo() { return deliveredArrows; }
        boolean matches(Rac1ShopDelivery d) {
            return kind==d.kind() && quantity==d.quantity() && price==d.price()
                    && currentArrows==d.arrows() && deliveredArrows==d.grantedArrows();
        }
    }
    public record Result(Status status,Quote quote,boolean replay) {
        Result replayed() { return new Result(status,quote,true); }
    }
    /** Production supplies the canonical ledger revision and a complete, non-reentrant server commit. */
    public interface Account {
        long balance();
        long revision();
        boolean commit(Rac1ShopDelivery delivery,long expectedBalance,long expectedRevision);
    }
    private record Request(boolean buy,UUID quote,int quantity,Rac1ShopOffer offer) {}
    private record Receipt(Request request,Result result) {}
    private static final class State {
        final Rac1ShopSessions.Opened opened;
        final Map<Long,Receipt> receipts=new HashMap<>();
        Quote quote;
        Rac1ShopDelivery delivery;
        long balance,revision,lastTick;
        State(Rac1ShopSessions.Opened opened,long tick) { this.opened=opened; lastTick=tick; }
    }
    private final Rac1ShopSessions sessions=new Rac1ShopSessions();
    private final Map<UUID,State> owners=new HashMap<>();
    private final Thread thread=Thread.currentThread();
    @FunctionalInterface interface Planner {
        Rac1ShopDelivery.Staged stage(Inventory inventory,Item weapon,Rac1ShopDelivery.Kind kind,int quantity);
    }
    private final Planner planner;
    @FunctionalInterface interface SelectedPlanner {
        Rac1ShopDelivery.Staged stage(Inventory inventory,Rac1ShopOffer offer,int quantity,boolean portable);
    }
    private final SelectedPlanner selectedPlanner;
    public Rac1ShopPurchases() { this(Rac1ShopDelivery::stage); }
    Rac1ShopPurchases(Planner planner) { this(planner,Rac1ShopDelivery::stageSelected); }
    Rac1ShopPurchases(Planner planner,SelectedPlanner selectedPlanner) {
        this.planner=Objects.requireNonNull(planner);
        this.selectedPlanner=Objects.requireNonNull(selectedPlanner);
    }
    public Rac1ShopSessions.Opened open(UUID owner,Rac1ShopSessions.Vendor vendor,long tick) {
        check(); expire(tick);
        var opened=sessions.open(owner,vendor,tick); owners.put(owner,new State(opened,tick)); return opened;
    }
    public Result quote(UUID owner,UUID token,Rac1ShopSessions.Vendor vendor,long sequence,int quantity,
                        long tick,Inventory inventory,Item weapon,Account account) {
        return quote(owner,token,vendor,sequence,Rac1ShopOffer.BLASTER,quantity,tick,inventory,weapon,account);
    }
    public Result quote(UUID owner,UUID token,Rac1ShopSessions.Vendor vendor,long sequence,Rac1ShopOffer offer,int quantity,
                        long tick,Inventory inventory,Item weapon,Account account) {
        return request(owner,token,vendor,sequence,new Request(false,null,quantity,Objects.requireNonNull(offer)),
                tick,inventory,weapon,account);
    }
    public Result buy(UUID owner,UUID token,Rac1ShopSessions.Vendor vendor,long sequence,UUID quote,
                      long tick,Inventory inventory,Item weapon,Account account) {
        return request(owner,token,vendor,sequence,new Request(true,quote,0,Rac1ShopOffer.BLASTER),tick,inventory,weapon,account);
    }
    private Result request(UUID owner,UUID token,Rac1ShopSessions.Vendor vendor,long sequence,Request request,
                           long tick,Inventory inventory,Item weapon,Account account) {
        check();
        State s=owners.get(owner);
        if (s==null || !s.opened.token().equals(token) || !s.opened.vendor().equals(vendor))
            return result(Status.INVALID_SESSION);
        // Range/liveness/vendor validation is the adapter's responsibility BEFORE every invocation,
        // including replay. Session admission still authenticates token, incarnation, rate and order.
        int quantity=Math.max(0,Math.min(65535,request.quantity));
        var admission=sessions.accept(owner,token,vendor,sequence,
                new Rac1ShopSessions.Intent(request.buy ? 1:0,request.offer.ordinal(),quantity),tick);
        if (admission.status()==Rac1ShopSessions.Status.REPLAY) {
            Receipt old=s.receipts.get(sequence);
            return old!=null && old.request.equals(request) ? old.result.replayed():result(Status.CONFLICT);
        }
        if (admission.status()!=Rac1ShopSessions.Status.ACCEPTED) {
            if (admission.status()==Rac1ShopSessions.Status.INVALID_SESSION) owners.remove(owner);
            return result(switch (admission.status()) {
                case INVALID_SESSION -> Status.INVALID_SESSION;
                case CONFLICT -> Status.CONFLICT;
                case RATE_LIMIT -> Status.RATE_LIMIT;
                case EXHAUSTED -> Status.EXHAUSTED;
                default -> Status.STALE_REQUEST;
            });
        }
        s.lastTick=tick;
        Result result;
        if (!request.buy) {
            s.quote=null; s.delivery=null;
            var staged=stage(inventory,weapon,request.offer,request.quantity,account);
            if (staged.failure()!=null) result=result(Status.valueOf(staged.failure().name()));
            else if (account.balance()<staged.delivery().price())
                result=result(Status.INSUFFICIENT);
            else {
                var d=staged.delivery(); s.delivery=d;
                s.balance=account.balance(); s.revision=account.revision();
                s.quote=new Quote(UUID.randomUUID(),d.kind(),d.quantity(),d.price(),d.arrows(),d.grantedArrows(),
                        tick+QUOTE_TICKS,request.offer);
                result=new Result(Status.QUOTED,s.quote,false);
            }
        } else if (s.quote==null || !s.quote.id().equals(request.quote) || tick>=s.quote.expires()) {
            result=result(Status.STALE_QUOTE);
        } else {
            Quote q=s.quote; var delivery=s.delivery;
            s.quote=null; s.delivery=null; // one quote can be consumed only once, even with a fresh sequence
            if (!delivery.unchanged(inventory))
                result=result(Status.STALE_OFFER);
            else if (account.balance()<q.price()) result=result(Status.INSUFFICIENT);
            else if (account.balance()!=s.balance || account.revision()!=s.revision) result=result(Status.STALE_BALANCE);
            else {
                // Explicitly rederive the offer, cap, full delivery and original price at purchase time.
                var fresh=stage(inventory,weapon,q.offer(),q.quantity(),account);
                if (fresh.failure()!=null) result=result(Status.valueOf(fresh.failure().name()));
                else if (!q.matches(fresh.delivery())) result=result(Status.STALE_OFFER);
                else {
                    boolean committed=account.commit(fresh.delivery(),s.balance,s.revision);
                    result=new Result(committed ? Status.PURCHASED:Status.COMMIT_REJECTED,q,false);
                }
            }
        }
        s.receipts.put(sequence,new Receipt(request,result));
        return result;
    }
    private Rac1ShopDelivery.Staged stage(Inventory inventory,Item weapon,Rac1ShopOffer offer,int quantity,Account account) {
        return offer==Rac1ShopOffer.BLASTER
                ? planner.stage(inventory,weapon,Rac1ShopDelivery.offer(inventory,weapon),quantity)
                : selectedPlanner.stage(inventory,offer,quantity,false);
    }
    public void close(UUID owner,UUID token) {
        check(); var s=owners.get(owner);
        if (s!=null && s.opened.token().equals(token)) { owners.remove(owner); sessions.close(owner,token); }
    }
    public void disconnect(UUID owner) { check(); owners.remove(owner); sessions.invalidate(owner); }
    public boolean active(UUID owner,UUID token) {
        check(); var s=owners.get(owner); return s!=null && s.opened.token().equals(token);
    }
    public void expire(long tick) {
        check(); sessions.expire(tick);
        owners.values().removeIf(s -> tick<s.lastTick || tick-s.lastTick>=Rac1ShopSessions.IDLE_TICKS);
    }
    public void clear() { check(); owners.clear(); sessions.clear(); }
    private static Result result(Status status) { return new Result(status,null,false); }
    private void check() { if (Thread.currentThread()!=thread) throw new IllegalStateException("Shop purchases require their server thread"); }
}
