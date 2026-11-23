package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import java.util.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;

/**
 * Portable commerce checkpoint. No placed-vendor identity, assets or sound callbacks.
 * Adapter must revalidate the exact held item lifetime before EVERY call, including replay.
 */
public final class Rac1PdaPurchases {
    /** R12 retained both-disc price field + pinned ReRAC menus.md, item 15. */
    public static final int UNIT_PRICE=Rac1AmmoPolicy.BLASTER.unitPrice(true);
    public static final long LIFETIME_TICKS=1200;
    private record Request(UUID quote,int quantity,Rac1ShopOffer offer) {}
    private record Receipt(Request request,Result result) {}
    private static final class Session {
        final UUID token=UUID.randomUUID();
        final long opened;
        final Map<Long,Receipt> receipts=new HashMap<>();
        long next=1,lastTick,rateTick,balance,revision;
        int rate;
        Quote quote;
        Rac1ShopDelivery delivery;
        Session(long tick) { opened=lastTick=rateTick=tick; }
    }
    @FunctionalInterface interface Planner {
        Rac1ShopDelivery.Staged stage(Inventory inventory,Item weapon,int quantity);
    }
    private final Planner planner;
    private final SelectedPlanner selectedPlanner;
    private final Map<UUID,Session> owners=new HashMap<>();
    private final Thread thread=Thread.currentThread();
    public Rac1PdaPurchases() { this(Rac1ShopDelivery::stagePdaAmmo); }
    Rac1PdaPurchases(Planner planner) { this(planner,Rac1ShopDelivery::stageSelected); }
    Rac1PdaPurchases(Planner planner,SelectedPlanner selectedPlanner) {
        this.planner=Objects.requireNonNull(planner);
        this.selectedPlanner=Objects.requireNonNull(selectedPlanner);
    }

    public UUID open(UUID owner,long tick) {
        check(tick); expire(tick);
        if (!owners.containsKey(owner) && owners.size()>=Rac1ShopSessions.MAX_SESSIONS)
            throw new IllegalStateException("PDA session limit reached");
        var s=new Session(tick); owners.put(Objects.requireNonNull(owner),s); return s.token;
    }
    public Result quote(UUID owner,UUID token,long sequence,int quantity,long tick,
                        Inventory inventory,Item weapon,Account account) {
        return quote(owner,token,sequence,Rac1ShopOffer.BLASTER,quantity,tick,inventory,weapon,account);
    }
    public Result quote(UUID owner,UUID token,long sequence,Rac1ShopOffer offer,int quantity,long tick,
                        Inventory inventory,Item weapon,Account account) {
        return request(owner,token,sequence,new Request(null,quantity,Objects.requireNonNull(offer)),tick,inventory,weapon,account);
    }
    public Result buy(UUID owner,UUID token,long sequence,UUID quote,long tick,
                      Inventory inventory,Item weapon,Account account) {
        return request(owner,token,sequence,new Request(Objects.requireNonNull(quote),0,Rac1ShopOffer.BLASTER),tick,inventory,weapon,account);
    }
    private Result request(UUID owner,UUID token,long sequence,Request request,long tick,
                           Inventory inventory,Item weapon,Account account) {
        check(tick);
        var s=owners.get(owner);
        if (s==null || !s.token.equals(token)) return result(Status.INVALID_SESSION);
        if (expired(s,tick)) { owners.remove(owner); return result(Status.INVALID_SESSION); }
        if (s.rateTick!=tick) { s.rateTick=tick; s.rate=0; }
        if (s.rate++>=Rac1ShopSessions.REQUESTS_PER_TICK) return result(Status.RATE_LIMIT);
        var old=s.receipts.get(sequence);
        if (old!=null) return old.request.equals(request)
                ? new Result(old.result.status(),old.result.quote(),true):result(Status.CONFLICT);
        if (sequence!=s.next) return result(Status.STALE_REQUEST);
        if (s.receipts.size()>=Rac1ShopSessions.MAX_REQUESTS) return result(Status.EXHAUSTED);
        s.next++; s.lastTick=tick;
        Result result;
        if (request.quote==null) {
            s.quote=null; s.delivery=null;
            var staged=stage(inventory,weapon,request.offer,request.quantity);
            if (staged.failure()!=null) result=result(Status.valueOf(staged.failure().name()));
            else if (account.balance()<staged.delivery().price()) result=result(Status.INSUFFICIENT);
            else {
                var d=staged.delivery();
                s.delivery=d; s.balance=account.balance(); s.revision=account.revision();
                s.quote=new Quote(UUID.randomUUID(),d.kind(),d.quantity(),d.price(),d.arrows(),
                        d.grantedArrows(),tick+QUOTE_TICKS,request.offer);
                result=new Result(Status.QUOTED,s.quote,false);
            }
        } else if (s.quote==null || !s.quote.id().equals(request.quote) || tick>=s.quote.expires()) {
            result=result(Status.STALE_QUOTE);
        } else {
            var q=s.quote; var delivery=s.delivery;
            s.quote=null; s.delivery=null; // confirmation is single-use, even on rejection
            if (!delivery.unchanged(inventory)) result=result(Status.STALE_OFFER);
            else if (account.balance()<q.price()) result=result(Status.INSUFFICIENT);
            else if (account.balance()!=s.balance || account.revision()!=s.revision) result=result(Status.STALE_BALANCE);
            else {
                var fresh=stage(inventory,weapon,q.offer(),q.quantity());
                if (fresh.failure()!=null) result=result(Status.valueOf(fresh.failure().name()));
                else if (!q.matches(fresh.delivery())) result=result(Status.STALE_OFFER);
                else result=new Result(account.commit(fresh.delivery(),s.balance,s.revision)
                        ? Status.PURCHASED:Status.COMMIT_REJECTED,q,false);
            }
        }
        s.receipts.put(sequence,new Receipt(request,result));
        return result;
    }
    private Rac1ShopDelivery.Staged stage(Inventory inventory,Item weapon,Rac1ShopOffer offer,int quantity) {
        if (!offer.permitted(true)) return new Rac1ShopDelivery.Staged(null,Rac1ShopDelivery.Failure.STALE_OFFER);
        return offer==Rac1ShopOffer.BLASTER ? planner.stage(inventory,weapon,quantity)
                : selectedPlanner.stage(inventory,offer,quantity,true);
    }
    public boolean active(UUID owner,UUID token,long tick) {
        check(tick); var s=owners.get(owner);
        return s!=null && s.token.equals(token) && !expired(s,tick);
    }
    public void close(UUID owner,UUID token) {
        check(0); var s=owners.get(owner);
        if (s!=null && s.token.equals(token)) owners.remove(owner);
    }
    public void disconnect(UUID owner) { check(0); owners.remove(owner); }
    public void expire(long tick) { check(tick); owners.values().removeIf(s -> expired(s,tick)); }
    public void clear() { check(0); owners.clear(); }
    private static boolean expired(Session s,long tick) {
        return tick<s.lastTick || tick-s.opened>=LIFETIME_TICKS || tick-s.lastTick>=Rac1ShopSessions.IDLE_TICKS;
    }
    private static Result result(Status status) { return new Result(status,null,false); }
    private void check(long tick) {
        if (Thread.currentThread()!=thread) throw new IllegalStateException("PDA commerce off server thread");
        if (tick<0) throw new IllegalArgumentException("Negative server tick");
    }
}
