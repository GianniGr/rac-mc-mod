package com.github.giannigr.racmc.shop;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;

/** Coordinator unit tests with actual vanilla inventory; no network, live server or mod registry claimed. */
class Rac1ShopPurchasesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static final Rac1ShopSessions.Vendor VENDOR=new Rac1ShopSessions.Vendor("minecraft:overworld",1,UUID.randomUUID());
    private static final class Account implements Rac1ShopPurchases.Account,Rac1ShopAtomicCommit.Debit {
        final Inventory inventory=new Inventory(null);
        long balance=10000,revision;
        int commits;
        boolean reject;
        public long balance() { return balance; }
        public long revision() { return revision; }
        public boolean commit(Rac1ShopDelivery d,long b,long r) {
            return Rac1ShopAtomicCommit.run(inventory,d,b,r,this).committed();
        }
        public boolean tryDebit(long amount) {
            if (reject || balance<amount) return false;
            balance-=amount; revision++; commits++; return true;
        }
    }
    private static final class Fixture {
        final Rac1ShopPurchases shop=new Rac1ShopPurchases(ShopTestAmmo::stage);
        final UUID owner=UUID.randomUUID();
        final Account account=new Account();
        Rac1ShopSessions.Opened session=shop.open(owner,VENDOR,0);
        Result quote(long seq,int qty,long tick) {
            return shop.quote(owner,session.token(),VENDOR,seq,qty,tick,account.inventory,Items.BOW,account);
        }
        Result buy(long seq,UUID quote,long tick) {
            return shop.buy(owner,session.token(),VENDOR,seq,quote,tick,account.inventory,Items.BOW,account);
        }
    }
    @Test void weaponThenAmmoUseOriginalPriceFillAndExactUnitQuantity() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote();
        assertEquals(2500,q.price()); assertEquals(100,q.deliveredArrows());
        assertEquals(Status.PURCHASED,f.buy(2,q.id(),2).status());
        assertEquals(7500,f.account.balance); assertEquals(100,ShopTestAmmo.arrows(f.account.inventory));
        q=f.quote(3,25,3).quote(); assertEquals(25,q.price());
        assertEquals(Rac1ShopDelivery.Kind.AMMO,q.kind());
        assertEquals(Status.PURCHASED,f.buy(4,q.id(),4).status());
        assertEquals(7475,f.account.balance); assertEquals(125,ShopTestAmmo.arrows(f.account.inventory));
    }
    @Test void retryIsAnIdenticalReceiptNotAnotherDebitOrDeliveryAndConflictsReject() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote(); var bought=f.buy(2,q.id(),2);
        assertEquals(Status.PURCHASED,bought.status()); assertFalse(bought.replay());
        var replay=f.buy(2,q.id(),3); assertTrue(replay.replay()); assertEquals(Status.PURCHASED,replay.status());
        assertEquals(1,f.account.commits); assertEquals(7500,f.account.balance);
        assertEquals(Status.CONFLICT,f.buy(2,UUID.randomUUID(),4).status());
        assertEquals(Status.STALE_QUOTE,f.buy(3,q.id(),5).status()); assertEquals(1,f.account.commits);
    }
    @Test void losingWeaponAllowsPaidReacquisitionWithoutPermanentEntitlement() {
        var f=new Fixture(); var first=f.quote(1,1,1).quote();
        assertEquals(Status.PURCHASED,f.buy(2,first.id(),2).status());
        f.account.inventory.setItem(0,ItemStack.EMPTY);
        f.account.inventory.setItem(1,new ItemStack(Items.PAPER,40)); f.account.inventory.setItem(2,ItemStack.EMPTY);
        var second=f.quote(3,1,3).quote();
        assertEquals(Rac1ShopDelivery.Kind.WEAPON,second.kind()); assertEquals(60,second.deliveredArrows());
        assertEquals(Status.PURCHASED,f.buy(4,second.id(),4).status());
        assertEquals(5000,f.account.balance); assertEquals(100,ShopTestAmmo.arrows(f.account.inventory));
    }
    @Test void outOfOrderRequestsDoNotConsumeOrCommitAndQuoteReplacementInvalidatesOldId() {
        var f=new Fixture(); var first=f.quote(1,1,1).quote();
        assertEquals(Status.STALE_REQUEST,f.buy(3,first.id(),2).status());
        var second=f.quote(2,1,3).quote();
        assertEquals(Status.STALE_QUOTE,f.buy(3,first.id(),4).status());
        assertEquals(Status.PURCHASED,f.buy(4,second.id(),5).status());
    }
    @Test void insufficientFundsAtQuoteOrAtBuyNeverGrantOrCharge() {
        var f=new Fixture(); f.account.balance=2499;
        assertEquals(Status.INSUFFICIENT,f.quote(1,1,1).status()); assertEquals(0,f.account.commits);
        f.account.balance=2500; var q=f.quote(2,1,2).quote();
        f.account.balance=2499; f.account.revision++;
        assertEquals(Status.INSUFFICIENT,f.buy(3,q.id(),3).status());
        assertEquals(0,ShopTestAmmo.arrows(f.account.inventory)); assertEquals(2499,f.account.balance);
    }
    @Test void changedBalanceRevisionIsStaleEvenWhenStillAffordable() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote(); f.account.revision++;
        assertEquals(Status.STALE_BALANCE,f.buy(2,q.id(),2).status()); assertEquals(0,f.account.commits);
    }
    @Test void pickedUpOrLostWeaponInvalidatesQuoteRatherThanChangingTheChargedOffer() {
        var f=new Fixture(); var weapon=f.quote(1,1,1).quote(); f.account.inventory.setItem(0,new ItemStack(Items.BOW));
        assertEquals(Status.STALE_OFFER,f.buy(2,weapon.id(),2).status());
        var ammo=f.quote(3,1,3).quote(); f.account.inventory.setItem(0,ItemStack.EMPTY);
        assertEquals(Status.STALE_OFFER,f.buy(4,ammo.id(),4).status());
        assertEquals(0,f.account.commits); assertEquals(10000,f.account.balance);
    }
    @Test void inventoryChangeIncludingCapacityOrArrowCountRequiresRequote() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote();
        for (int i=0;i<36;i++) f.account.inventory.setItem(i,new ItemStack(Items.STONE,64));
        assertEquals(Status.STALE_OFFER,f.buy(2,q.id(),2).status());
        assertEquals(Status.FULL,f.quote(3,1,3).status()); assertEquals(0,f.account.commits);
    }
    @Test void capAndPreexistingOverCapRejectWholeRequestedAmountWithoutCharge() {
        var f=new Fixture(); f.account.inventory.setItem(0,new ItemStack(Items.BOW));
        f.account.inventory.setItem(1,new ItemStack(Items.PAPER,199));
        assertEquals(Status.STOCK_CAP,f.quote(1,2,1).status());
        var q=f.quote(2,1,2).quote(); assertEquals(Status.PURCHASED,f.buy(3,q.id(),3).status());
        assertEquals(200,ShopTestAmmo.arrows(f.account.inventory));
        f.account.inventory.getItem(1).grow(30);
        assertEquals(Status.STOCK_CAP,f.quote(4,1,4).status());
        assertEquals(230,ShopTestAmmo.arrows(f.account.inventory)); assertEquals(9999,f.account.balance);
    }
    @Test void exactExpiryAndCommitRejectionAreConsumedWithoutMutationOrAutomaticRetry() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote();
        assertEquals(Status.STALE_QUOTE,f.buy(2,q.id(),201).status());
        q=f.quote(3,1,202).quote(); f.account.reject=true;
        assertEquals(Status.COMMIT_REJECTED,f.buy(4,q.id(),203).status());
        f.account.reject=false; assertTrue(f.buy(4,q.id(),204).replay());
        assertEquals(0,f.account.commits); assertEquals(Status.STALE_QUOTE,f.buy(5,q.id(),205).status());
    }
    @Test void simultaneousViewersAreIndependentAndDisconnectCannotInvalidateAnother() {
        var f=new Fixture(); UUID other=UUID.randomUUID(); var account=new Account();
        var opened=f.shop.open(other,VENDOR,1);
        var first=f.quote(1,1,2).quote();
        var second=f.shop.quote(other,opened.token(),VENDOR,1,1,2,account.inventory,Items.BOW,account).quote();
        f.shop.disconnect(f.owner);
        assertEquals(Status.INVALID_SESSION,f.buy(2,first.id(),3).status());
        assertEquals(Status.PURCHASED,f.shop.buy(other,opened.token(),VENDOR,2,second.id(),3,account.inventory,Items.BOW,account).status());
        assertEquals(0,f.account.commits); assertEquals(1,account.commits);
    }
    @Test void reconnectReopenRestartWrongOwnerAndVendorIncarnationRejectOldTokens() {
        var f=new Fixture(); var old=f.session; var q=f.quote(1,1,1).quote();
        f.shop.disconnect(f.owner); f.session=f.shop.open(f.owner,VENDOR,2);
        assertEquals(Status.INVALID_SESSION,f.shop.buy(f.owner,old.token(),VENDOR,2,q.id(),3,f.account.inventory,Items.BOW,f.account).status());
        assertEquals(Status.INVALID_SESSION,f.shop.buy(UUID.randomUUID(),f.session.token(),VENDOR,1,q.id(),3,f.account.inventory,Items.BOW,f.account).status());
        var replaced=new Rac1ShopSessions.Vendor(VENDOR.dimension(),VENDOR.position(),UUID.randomUUID());
        assertEquals(Status.INVALID_SESSION,f.shop.buy(f.owner,f.session.token(),replaced,1,q.id(),3,f.account.inventory,Items.BOW,f.account).status());
        f.shop.clear(); assertFalse(f.shop.active(f.owner,f.session.token()));
        assertEquals(Status.INVALID_SESSION,f.buy(1,q.id(),4).status());
    }
    @Test void idleExpiryAndRateLimitDoNotMutateAndRunOnlyOnOwningThread() throws Exception {
        var f=new Fixture();
        for (int i=1;i<=8;i++) assertEquals(Status.INVALID_QUANTITY,f.quote(i,0,1).status());
        assertEquals(Status.RATE_LIMIT,f.quote(9,1,1).status());
        f.shop.expire(1201); assertEquals(Status.INVALID_SESSION,f.quote(9,1,1201).status());
        Throwable[] error={null};
        var thread=new Thread(() -> { try { f.shop.clear(); } catch (Throwable t) { error[0]=t; } });
        thread.start(); thread.join(); assertInstanceOf(IllegalStateException.class,error[0]);
    }
    @Test void requestBudgetAndInvalidQuantitiesStayBounded() {
        var f=new Fixture();
        for (int i=1;i<=Rac1ShopSessions.MAX_REQUESTS;i++)
            assertEquals(Status.INVALID_QUANTITY,f.quote(i,0,i).status());
        assertEquals(Status.EXHAUSTED,f.quote(257,1,257).status());
        assertEquals(0,f.account.commits); assertEquals(10000,f.account.balance);
    }
}
