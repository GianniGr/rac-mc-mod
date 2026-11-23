package com.github.giannigr.racmc.shop;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.*;

/** Actual vanilla inventories, synthetic Bow/Paper; not mod-registry or live lifecycle acceptance. */
class Rac1PdaPurchasesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static final class Account implements Rac1ShopPurchases.Account,Rac1ShopAtomicCommit.Debit {
        final Inventory inventory=new Inventory(null);
        long balance=10000,revision;
        int commits;
        boolean reject,throwAfterDebit;
        public long balance() { return balance; }
        public long revision() { return revision; }
        public boolean commit(Rac1ShopDelivery d,long b,long r) {
            return Rac1ShopAtomicCommit.run(inventory,d,b,r,this).committed();
        }
        public boolean tryDebit(long amount) {
            if (reject || balance<amount) return false;
            balance-=amount; revision++; commits++;
            if (throwAfterDebit) throw new IllegalStateException("synthetic notification failure");
            return true;
        }
    }
    private static final class Fixture {
        final Rac1PdaPurchases pda=new Rac1PdaPurchases((inv,weapon,qty) ->
                Rac1ShopDelivery.stagePdaWithAmmo(inv,weapon,qty,Items.PAPER,ShopTestAmmo.arrows(inv)));
        final UUID owner=UUID.randomUUID();
        final Account account=new Account();
        UUID token=pda.open(owner,0);
        Fixture() { account.inventory.setItem(0,new ItemStack(Items.BOW)); }
        Result quote(long seq,int qty,long tick) {
            return pda.quote(owner,token,seq,qty,tick,account.inventory,Items.BOW,account);
        }
        Result buy(long seq,UUID quote,long tick) {
            return pda.buy(owner,token,seq,quote,tick,account.inventory,Items.BOW,account);
        }
        int ammo() { return ShopTestAmmo.arrows(account.inventory); }
    }
    @Test void originalTenBoltUnitPriceAndExactQuantityWithoutChangingKioskPrice() {
        var f=new Fixture(); var q=f.quote(1,25,1).quote();
        assertEquals(250,q.price()); assertEquals(Rac1ShopDelivery.Kind.AMMO,q.kind());
        assertEquals(25,q.deliveredArrows()); assertEquals(0,f.ammo());
        assertEquals(Status.PURCHASED,f.buy(2,q.id(),2).status());
        assertEquals(9750,f.account.balance); assertEquals(25,f.ammo());
        assertEquals(25,ShopTestAmmo.stage(f.account.inventory,Items.BOW,Rac1ShopDelivery.Kind.AMMO,25).delivery().price());
    }
    @Test void absentWeaponNeverBecomesAWeaponSaleOrTopup() {
        var f=new Fixture(); f.account.inventory.setItem(0,ItemStack.EMPTY);
        assertEquals(Status.STALE_OFFER,f.quote(1,1,1).status());
        assertEquals(0,f.ammo()); assertEquals(10000,f.account.balance); assertEquals(0,f.account.commits);
    }
    @Test void exactBalanceSucceedsButOneBoltShortRejects() {
        var f=new Fixture(); f.account.balance=9;
        assertEquals(Status.INSUFFICIENT,f.quote(1,1,1).status());
        f.account.balance=10; var q=f.quote(2,1,2).quote();
        assertEquals(Status.PURCHASED,f.buy(3,q.id(),3).status());
        assertEquals(0,f.account.balance); assertEquals(1,f.ammo());
    }
    @Test void capIncludesOffhandAndRejectsWholeOverCapRequest() {
        var f=new Fixture(); f.account.inventory.setItem(1,new ItemStack(Items.PAPER,136));
        f.account.inventory.setItem(40,new ItemStack(Items.PAPER,63));
        assertEquals(Status.STOCK_CAP,f.quote(1,2,1).status()); assertEquals(199,f.ammo());
        var q=f.quote(2,1,2).quote(); assertEquals(199,q.currentArrows());
        assertEquals(Status.PURCHASED,f.buy(3,q.id(),3).status()); assertEquals(200,f.ammo());
        assertEquals(Status.STOCK_CAP,f.quote(4,1,4).status()); assertEquals(9990,f.account.balance);
    }
    @Test void overCapInventoryIsNotReducedAndVanillaArrowsDoNotCountAsAmmo() {
        var f=new Fixture(); f.account.inventory.setItem(1,new ItemStack(Items.ARROW,64));
        f.account.inventory.setItem(2,new ItemStack(Items.TIPPED_ARROW,64));
        assertEquals(0,f.quote(1,1,1).quote().currentArrows());
        f.account.inventory.setItem(40,new ItemStack(Items.PAPER,201));
        assertEquals(Status.STOCK_CAP,f.quote(2,1,2).status()); assertEquals(201,f.ammo());
    }
    @Test void failedFullFitNeverChargesOrMutatesAndEmptyOffhandIsNotAnExtraSlot() {
        var f=new Fixture();
        for (int i=1;i<36;i++) f.account.inventory.setItem(i,new ItemStack(Items.STONE,64));
        assertEquals(Status.FULL,f.quote(1,1,1).status());
        assertEquals(0,f.ammo()); assertEquals(0,f.account.commits); assertEquals(10000,f.account.balance);
        assertTrue(f.account.inventory.getItem(40).isEmpty());
    }
    @Test void mergingIntoOffhandCanCompleteFitWithoutDropsOrExtraSlots() {
        var f=new Fixture();
        for (int i=1;i<36;i++) f.account.inventory.setItem(i,new ItemStack(Items.STONE,64));
        f.account.inventory.setItem(40,new ItemStack(Items.PAPER,63));
        var q=f.quote(1,1,1).quote();
        assertEquals(Status.PURCHASED,f.buy(2,q.id(),2).status());
        assertEquals(64,f.account.inventory.getItem(40).getCount()); assertEquals(9990,f.account.balance);
    }
    @Test void replayIsIdenticalReceiptAndConflictingOrFreshConfirmationCannotRecommit() {
        var f=new Fixture(); var q=f.quote(1,25,1).quote(); var first=f.buy(2,q.id(),2);
        assertEquals(Status.PURCHASED,first.status()); assertFalse(first.replay());
        var replay=f.buy(2,q.id(),3); assertTrue(replay.replay()); assertEquals(Status.PURCHASED,replay.status());
        assertEquals(Status.CONFLICT,f.buy(2,UUID.randomUUID(),4).status());
        assertEquals(Status.STALE_QUOTE,f.buy(3,q.id(),5).status());
        assertEquals(1,f.account.commits); assertEquals(25,f.ammo()); assertEquals(9750,f.account.balance);
    }
    @Test void wrongOwnerTokenAndReplacementSessionCannotSpend() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote(); UUID old=f.token;
        assertEquals(Status.INVALID_SESSION,f.pda.buy(UUID.randomUUID(),f.token,2,q.id(),2,f.account.inventory,Items.BOW,f.account).status());
        f.token=f.pda.open(f.owner,2);
        assertEquals(Status.INVALID_SESSION,f.pda.buy(f.owner,old,2,q.id(),3,f.account.inventory,Items.BOW,f.account).status());
        assertEquals(Status.STALE_QUOTE,f.buy(1,q.id(),4).status()); assertEquals(0,f.account.commits);
    }
    @Test void inventoryMutationOrWeaponLossRequiresRequote() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote();
        f.account.inventory.setItem(0,ItemStack.EMPTY);
        assertEquals(Status.STALE_OFFER,f.buy(2,q.id(),2).status());
        assertEquals(Status.STALE_OFFER,f.quote(3,1,3).status()); assertEquals(0,f.account.commits);
    }
    @Test void revisionChangeAndInsufficientFundsAtConfirmationDoNotCommit() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote(); f.account.revision++;
        assertEquals(Status.STALE_BALANCE,f.buy(2,q.id(),2).status());
        q=f.quote(3,1,3).quote(); f.account.balance=9;
        assertEquals(Status.INSUFFICIENT,f.buy(4,q.id(),4).status()); assertEquals(0,f.ammo());
    }
    @Test void quoteExpiresAtBoundaryAndSessionHasAbsoluteLifetimeEvenWhenActive() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote();
        assertEquals(Status.STALE_QUOTE,f.buy(2,q.id(),201).status());
        q=f.quote(3,1,1199).quote();
        assertEquals(Status.INVALID_SESSION,f.buy(4,q.id(),1200).status()); assertEquals(0,f.account.commits);
    }
    @Test void closeAndClockRollbackInvalidateButWrongTokenCloseDoesNot() {
        var f=new Fixture(); f.pda.close(f.owner,UUID.randomUUID());
        var q=f.quote(1,1,10).quote();
        assertEquals(Status.INVALID_SESSION,f.buy(2,q.id(),9).status());
        f.token=f.pda.open(f.owner,11); q=f.quote(1,1,12).quote();
        f.pda.close(f.owner,f.token); assertEquals(Status.INVALID_SESSION,f.buy(2,q.id(),13).status());
    }
    @Test void rejectedCommitRollsBackAndNotificationFailureAfterDebitDoesNotRegrant() {
        var f=new Fixture(); var q=f.quote(1,1,1).quote(); f.account.reject=true;
        assertEquals(Status.COMMIT_REJECTED,f.buy(2,q.id(),2).status()); assertEquals(0,f.ammo());
        f.account.reject=false; f.account.throwAfterDebit=true;
        q=f.quote(3,1,3).quote(); assertEquals(Status.PURCHASED,f.buy(4,q.id(),4).status());
        assertTrue(f.buy(4,q.id(),5).replay()); assertEquals(1,f.ammo()); assertEquals(1,f.account.commits);
    }
    @Test void independentOwnersAndDisconnectDoNotAffectAnotherSession() {
        var f=new Fixture(); var other=new Account(); other.inventory.setItem(0,new ItemStack(Items.BOW));
        UUID owner=UUID.randomUUID(),token=f.pda.open(owner,1);
        var q=f.pda.quote(owner,token,1,1,2,other.inventory,Items.BOW,other).quote();
        f.pda.disconnect(f.owner);
        assertEquals(Status.PURCHASED,f.pda.buy(owner,token,2,q.id(),3,other.inventory,Items.BOW,other).status());
        assertEquals(9990,other.balance); assertEquals(10000,f.account.balance);
    }
    @Test void kioskAndPortableQuotesRemainSeparateButShareCanonicalInventoryAndBalance() {
        var f=new Fixture();
        var kiosk=new Rac1ShopPurchases(ShopTestAmmo::stage);
        var vendor=new Rac1ShopSessions.Vendor("minecraft:overworld",123,UUID.randomUUID());
        var opened=kiosk.open(f.owner,vendor,0);
        var ordinary=kiosk.quote(f.owner,opened.token(),vendor,1,1,1,f.account.inventory,Items.BOW,f.account).quote();
        var portable=f.quote(1,1,1).quote();
        assertEquals(1,ordinary.price()); assertEquals(10,portable.price());
        assertEquals(Status.INVALID_SESSION,kiosk.buy(f.owner,f.token,vendor,2,portable.id(),2,
                f.account.inventory,Items.BOW,f.account).status());
        assertEquals(Status.PURCHASED,f.buy(2,portable.id(),2).status());
        assertEquals(Status.STALE_OFFER,kiosk.buy(f.owner,opened.token(),vendor,2,ordinary.id(),3,
                f.account.inventory,Items.BOW,f.account).status());
        assertEquals(1,f.ammo()); assertEquals(9990,f.account.balance); assertEquals(1,f.account.commits);
    }
    @Test void atomicAmmoCommitPreservesUnchangedHeldStackObjectForItemLifetimeBinding() {
        var f=new Fixture();
        var handheld=new ItemStack(Items.SPYGLASS);
        f.account.inventory.setItem(40,handheld);
        var q=f.quote(1,1,1).quote();
        assertEquals(Status.PURCHASED,f.buy(2,q.id(),2).status());
        assertSame(handheld,f.account.inventory.getItem(40));
        // Identical replacement is nevertheless a different lifetime reference.
        f.account.inventory.setItem(40,handheld.copy());
        assertNotSame(handheld,f.account.inventory.getItem(40));
    }
    @Test void malformedOutOfOrderRateAndRequestBudgetAreBounded() {
        var f=new Fixture(); assertEquals(Status.STALE_REQUEST,f.quote(2,1,1).status());
        for (int i=1;i<=7;i++) assertEquals(Status.INVALID_QUANTITY,f.quote(i,0,1).status());
        assertEquals(Status.RATE_LIMIT,f.quote(8,1,1).status());
        for (int i=8;i<=256;i++) assertEquals(Status.INVALID_QUANTITY,f.quote(i,Integer.MAX_VALUE,i).status());
        assertEquals(Status.EXHAUSTED,f.quote(257,1,257).status()); assertEquals(0,f.account.commits);
    }
    @Test void negativeTickAndOffThreadMutationReject() throws Exception {
        var f=new Fixture(); assertThrows(IllegalArgumentException.class,() -> f.quote(1,1,-1));
        Throwable[] error={null};
        var thread=new Thread(() -> { try { f.pda.clear(); } catch (Throwable t) { error[0]=t; } });
        thread.start(); thread.join(); assertInstanceOf(IllegalStateException.class,error[0]);
    }
}
