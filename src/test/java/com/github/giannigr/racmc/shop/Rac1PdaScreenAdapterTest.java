package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.client.pda.*;
import com.github.giannigr.racmc.client.shop.*;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status.*;

/** Actual screen adapter -> actual wire -> production planner/atomic inventory commit -> wire -> adapter. */
class Rac1PdaScreenAdapterTest {
    @Test void adapterCancelRejectsOldYesAndHeldBrowseEdgeNeverRequestsOrPays() {
        var f=new Fixture();
        assertTrue(f.ui.flow().activationDown(262)); assertTrue(f.ui.move(1));
        assertFalse(f.ui.flow().activationDown(262));
        assertEquals(Rac1PdaClientFlow.Mode.BROWSE,f.ui.flow().mode());
        assertEquals(0,f.debits);
        f.ui.flow().release(262);
        assertTrue(f.ui.flow().activationDown(263)); assertTrue(f.ui.move(-1));
        f.exchange(f.ui.request());
        var old=f.ui.flow().quote();
        f.ui.flow().cancelQuote(); f.ui.flow().presented(old.id());
        assertNull(f.ui.confirm()); assertEquals(0,f.debits);
        assertTrue(f.ui.move(1)); f.exchange(f.ui.request());
        assertEquals(Rac1ShopOffer.BOMB_AMMO,f.ui.flow().quote().offer());
        assertFalse(f.ui.receive(new Rac1PdaNetwork.Reply(f.token,1,
                new Rac1ShopPurchases.Result(QUOTED,old,false))),"Late old Blaster quote cannot bind Bomb UI");
        assertEquals(Rac1ShopOffer.BOMB_AMMO,f.ui.flow().quote().offer());
        assertEquals(0,f.debits);
        f.ui.flow().close(); assertNull(f.ui.confirm());
    }
    private static final Rac1ShopSessions.Vendor VENDOR=new Rac1ShopSessions.Vendor("minecraft:overworld",1,UUID.randomUUID());
    @Test void realKioskAdapterBindsPaidBombWeaponAndThenOffersOnlyRefill() {
        var f=new Fixture();
        f.inventory.setItem(2,ItemStack.EMPTY);
        f.inventory.setItem(40,new ItemStack(RacMcItems.BOMB_GLOVE_AMMO.get(),4));
        var server=new Rac1ShopPurchases();
        var ui=new Rac1ShopScreenCommerce(server.open(f.owner,VENDOR,0).token(),true);
        assertTrue(ui.move(1));
        assertTrue(ui.receive(kiosk(f,server,ui.request()),f.balance));
        var quote=ui.flow().quote();
        assertEquals(Rac1ShopDelivery.Kind.WEAPON,quote.kind());
        assertEquals(Rac1ShopOffer.BOMB_AMMO,quote.offer());
        assertEquals(1000,quote.price()); assertEquals(6,quote.deliveredAmmo());
        assertFalse(ui.adjust(1)); assertNull(ui.flow().confirm());
        assertEquals(0,f.debits); assertEquals(4,f.ammo(Rac1ShopOffer.BOMB_AMMO));
        ui.flow().presented(quote.id());
        var buy=ui.flow().confirm();
        assertTrue(ui.receive(kiosk(f,server,buy),f.balance)); assertEquals(PURCHASED,ui.flow().outcome());
        assertEquals(0,f.balance); assertEquals(10,f.ammo(Rac1ShopOffer.BOMB_AMMO));
        assertTrue(Rac1AmmoPolicy.BOMB_GLOVE.owns(f.inventory));
        assertTrue(kiosk(f,server,buy).result().replay()); assertEquals(1,f.debits);
        assertTrue(ui.receive(kiosk(f,server,ui.request()),f.balance)); assertEquals(INSUFFICIENT,ui.flow().outcome());
    }
    private static Rac1ShopNetwork.Reply kiosk(Fixture f,Rac1ShopPurchases server,Rac1ShopNetwork.Command command) {
        assertNotNull(command);
        var bytes=new FriendlyByteBuf(Unpooled.buffer());
        Rac1ShopNetwork.Command.CODEC.encode(bytes,command);
        var c=Rac1ShopNetwork.Command.CODEC.decode(bytes); bytes.release();
        var result=c.action()==Rac1ShopNetwork.Action.QUOTE
                ? server.quote(f.owner,c.token(),VENDOR,c.sequence(),c.offer(),c.quantity(),++f.tick,f.inventory,RacMcItems.BLASTER.get(),f)
                : server.buy(f.owner,c.token(),VENDOR,c.sequence(),c.quote(),++f.tick,f.inventory,RacMcItems.BLASTER.get(),f);
        bytes=new FriendlyByteBuf(Unpooled.buffer());
        Rac1ShopNetwork.Reply.CODEC.encode(bytes,new Rac1ShopNetwork.Reply(c.token(),c.sequence(),result));
        var reply=Rac1ShopNetwork.Reply.CODEC.decode(bytes); bytes.release(); return reply;
    }
    @Test void kioskProductionAdapterBothOffersQuantityPurchaseZeroDenialAndCap() {
        for (var offer:new Rac1ShopOffer[]{Rac1ShopOffer.BLASTER,Rac1ShopOffer.BOMB_AMMO}) {
            var f=new Fixture(); var server=new Rac1ShopPurchases();
            var opened=server.open(f.owner,VENDOR,0);
            var ui=new Rac1ShopScreenCommerce(opened.token(),true);
            assertTrue(ui.move(1)); assertTrue(ui.move(-1));
            if (offer==Rac1ShopOffer.BOMB_AMMO) ui.move(1);
            var initial=ui.request(); assertEquals(offer,initial.offer());
            assertTrue(ui.receive(kiosk(f,server,initial),f.balance));
            assertNull(ui.flow().confirm());
            assertFalse(ui.move(-1)); assertTrue(ui.adjust(1));
            assertTrue(ui.receive(kiosk(f,server,ui.requote(10)),f.balance));
            var quote=ui.flow().quote(); assertEquals(offer,quote.offer()); assertEquals(2,quote.quantity());
            assertEquals(2L*offer.stock().unitPrice(false),quote.price());
            assertEquals(0,f.debits); assertEquals(0,f.ammo(offer));
            ui.flow().presented(quote.id());
            var buy=ui.flow().confirm(); var receipt=kiosk(f,server,buy);
            assertTrue(ui.receive(receipt,f.balance)); assertEquals(PURCHASED,ui.flow().outcome());
            assertEquals(2,f.ammo(offer)); assertEquals(1,f.debits);
            assertTrue(kiosk(f,server,buy).result().replay()); assertEquals(1,f.debits);
            f.balance=0;
            assertTrue(ui.receive(kiosk(f,server,ui.request()),f.balance));
            assertEquals(INSUFFICIENT,ui.flow().outcome()); assertEquals(Rac1ShopClientFlow.Mode.DENIED,ui.flow().mode());
            assertEquals(1,ui.flow().deniedOpening()); ui.flow().cancel();
            f.balance=1000;
            f.inventory.setItem(3,new ItemStack(offer.stock().ammo().orElseThrow(),offer.stock().cap()));
            assertTrue(ui.receive(kiosk(f,server,ui.request()),f.balance));
            assertEquals(STOCK_CAP,ui.flow().outcome()); assertEquals(Rac1ShopClientFlow.Mode.MAXED,ui.flow().mode());
            assertEquals(2,ui.flow().deniedOpening()); assertEquals(1,f.debits);
        }
    }
    private static final class Fixture implements Rac1ShopPurchases.Account,Rac1ShopAtomicCommit.Debit {
        final Inventory inventory=new Inventory(null);
        final Rac1PdaPurchases server=new Rac1PdaPurchases();
        final UUID owner=UUID.randomUUID(),token=server.open(owner,0);
        final Rac1PdaScreenCommerce ui=new Rac1PdaScreenCommerce(token,true);
        long balance=1000,revision,tick;
        int debits;
        Fixture() {
            inventory.setItem(0,new ItemStack(RacMcItems.PDA.get()));
            inventory.setItem(1,new ItemStack(RacMcItems.BLASTER.get()));
            inventory.setItem(2,new ItemStack(RacMcItems.BOMB_GLOVE.get()));
        }
        public long balance() { return balance; }
        public long revision() { return revision; }
        public boolean tryDebit(long amount) { if (balance<amount) return false; balance-=amount; revision++; debits++; return true; }
        public boolean commit(Rac1ShopDelivery d,long b,long r) {
            return Rac1ShopAtomicCommit.run(inventory,d,b,r,this).committed();
        }
        Rac1PdaNetwork.Reply dispatch(Rac1PdaNetwork.Command packet) {
            assertNotNull(packet);
            var bytes=new FriendlyByteBuf(Unpooled.buffer());
            Rac1PdaNetwork.Command.CODEC.encode(bytes,packet);
            var c=Rac1PdaNetwork.Command.CODEC.decode(bytes).intent(); bytes.release();
            var result=c.action()==Rac1ShopNetwork.Action.QUOTE
                    ? server.quote(owner,c.token(),c.sequence(),c.offer(),c.quantity(),++tick,inventory,RacMcItems.BLASTER.get(),this)
                    : server.buy(owner,c.token(),c.sequence(),c.quote(),++tick,inventory,RacMcItems.BLASTER.get(),this);
            var response=new Rac1PdaNetwork.Reply(c.token(),c.sequence(),result);
            bytes=new FriendlyByteBuf(Unpooled.buffer());
            Rac1PdaNetwork.Reply.CODEC.encode(bytes,response);
            response=Rac1PdaNetwork.Reply.CODEC.decode(bytes); bytes.release();
            return response;
        }
        void exchange(Rac1PdaNetwork.Command c) { assertTrue(ui.receive(dispatch(c))); }
        int ammo(Rac1ShopOffer offer) { return Rac1AmmoPolicy.count(inventory,offer.stock().ammo().orElseThrow()); }
    }
    @Test void bothWayBrowseTypedOfferQuantityConsentAtomicFundedBuyAndReplay() {
        for (var offer:new Rac1ShopOffer[]{Rac1ShopOffer.BLASTER,Rac1ShopOffer.BOMB_AMMO}) {
            var f=new Fixture();
            assertTrue(f.ui.move(1)); assertTrue(f.ui.move(-1));
            if (offer==Rac1ShopOffer.BOMB_AMMO) assertTrue(f.ui.move(1));
            var c=f.ui.request(); assertEquals(offer,c.intent().offer());
            assertEquals(f.token,c.intent().token()); assertEquals(1,c.intent().sequence());
            f.exchange(c);
            assertEquals(offer,f.ui.flow().quote().offer());
            assertEquals(1000,f.balance); assertEquals(0,f.ammo(offer)); assertEquals(0,f.debits);
            assertNull(f.ui.confirm(),"Unpresented quote cannot buy");
            assertFalse(f.ui.move(-1),"Quote locks selection");
            f.exchange(f.ui.adjust(1,f.balance));
            assertEquals(2,f.ui.quantity()); assertEquals(2,f.ui.flow().quote().quantity());
            assertEquals(2L*offer.stock().unitPrice(true),f.ui.flow().quote().price());
            assertEquals(1000,f.balance);
            f.ui.flow().presented(f.ui.flow().quote().id());
            var buy=f.ui.confirm(); assertNotNull(buy);
            assertNull(f.ui.confirm(),"Held/repeated Yes cannot issue another spend");
            var result=f.dispatch(buy); assertEquals(PURCHASED,result.result().status());
            assertTrue(f.ui.receive(result));
            assertEquals(2,f.ammo(offer)); assertEquals(1,f.debits);
            assertEquals(1000-2L*offer.stock().unitPrice(true),f.balance);
            assertTrue(f.dispatch(buy).result().replay()); assertEquals(1,f.debits);
            assertFalse(f.ui.receive(result));
        }
    }
    @Test void zeroAndShortFundsAreNotStaleMissingWeaponOrCapAndSessionsStayPrivate() {
        for (var offer:new Rac1ShopOffer[]{Rac1ShopOffer.BLASTER,Rac1ShopOffer.BOMB_AMMO}) {
            for (long balance:new long[]{0,offer.stock().unitPrice(true)-1}) {
                var f=new Fixture(); f.balance=balance;
                if (offer==Rac1ShopOffer.BOMB_AMMO) f.ui.move(1);
                f.exchange(f.ui.request()); assertEquals(INSUFFICIENT,f.ui.flow().outcome());
                assertEquals(balance,f.balance); assertEquals(0,f.debits);
            }
            var f=new Fixture();
            if (offer==Rac1ShopOffer.BOMB_AMMO) f.ui.move(1);
            f.inventory.setItem(offer==Rac1ShopOffer.BLASTER ? 1:2,ItemStack.EMPTY);
            f.balance=0;
            f.exchange(f.ui.request()); assertEquals(STALE_OFFER,f.ui.flow().outcome());
            assertEquals(0,f.debits);
            f=new Fixture(); if (offer==Rac1ShopOffer.BOMB_AMMO) f.ui.move(1);
            f.inventory.setItem(3,new ItemStack(offer.stock().ammo().orElseThrow(),offer.stock().cap()));
            f.exchange(f.ui.request()); assertEquals(STOCK_CAP,f.ui.flow().outcome());
            assertEquals(0,f.debits);
        }
        var a=new Fixture(); var b=new Fixture();
        var reply=a.dispatch(a.ui.request());
        assertFalse(b.ui.receive(reply));
        assertTrue(a.ui.receive(reply));
        a.ui.flow().presented(reply.result().quote().id());
        var buy=a.ui.confirm();
        var foreign=b.dispatch(buy);
        assertEquals(INVALID_SESSION,foreign.result().status());
        assertEquals(0,a.debits); assertEquals(0,b.debits);
        a.ui.flow().cancelQuote(); // WAIT_BUY cannot cancel into a fresh spend.
        assertNull(a.ui.request());
        assertEquals(PURCHASED,a.dispatch(buy).result().status());
    }
}
