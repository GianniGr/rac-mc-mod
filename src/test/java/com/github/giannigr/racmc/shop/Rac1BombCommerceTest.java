package com.github.giannigr.racmc.shop;

import static com.github.giannigr.racmc.shop.Rac1ShopDelivery.Kind.*;
import static com.github.giannigr.racmc.shop.Rac1ShopOffer.*;
import static com.github.giannigr.racmc.shop.Rac1ShopPurchases.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercise production protocol/delivery with synthetic items; no placeholder registered gameplay item. */
class Rac1BombCommerceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static Rac1ShopDelivery.Staged stage(Inventory inv,Rac1ShopOffer offer,int qty,boolean portable) {
        return Rac1ShopDelivery.stageStock(inv,Items.SPYGLASS,Items.EGG,offer,
                portable ? AMMO:Rac1ShopDelivery.offer(inv,Items.SPYGLASS),qty,
                Rac1AmmoPolicy.count(inv,Items.EGG),portable);
    }

    private static final class Account implements Rac1ShopPurchases.Account,Rac1ShopAtomicCommit.Debit {
        final Inventory inventory=new Inventory(null);
        long balance,revision;
        boolean rejectDebit;
        public long balance() { return balance; }
        public long revision() { return revision; }
        public boolean commit(Rac1ShopDelivery d,long b,long r) {
            return Rac1ShopAtomicCommit.run(inventory,d,b,r,this).committed();
        }
        public boolean tryDebit(long amount) {
            if (rejectDebit || balance<amount) return false;
            balance-=amount; revision++; return true;
        }
        int bombs() { return Rac1AmmoPolicy.count(inventory,Items.EGG); }
    }

    private static final class Kiosk {
        final Account account;
        final Rac1ShopPurchases service=new Rac1ShopPurchases(ShopTestAmmo::stage,Rac1BombCommerceTest::stage);
        final Rac1ShopSessions.Vendor vendor=new Rac1ShopSessions.Vendor("minecraft:overworld",1,UUID.randomUUID());
        final UUID player=UUID.randomUUID();
        UUID token=service.open(player,vendor,0).token();
        Kiosk(Account account) { this.account=account; }
        Rac1ShopPurchases.Result quote(long seq,Rac1ShopOffer offer,int qty) {
            return service.quote(player,token,vendor,seq,offer,qty,seq,account.inventory,Items.BOW,account);
        }
        Rac1ShopPurchases.Result buy(long seq,UUID id) {
            return service.buy(player,token,vendor,seq,id,seq,account.inventory,Items.BOW,account);
        }
    }

    @Test void fullFitAndRejectedDebitNeverPartiallyDeliverThePaidWeapon() {
        var a=new Account(); var k=new Kiosk(a);
        a.balance=1000;
        for (int i=1;i<36;i++) a.inventory.setItem(i,new ItemStack(Items.STONE,64));
        assertEquals(FULL,k.quote(1,BOMB_AMMO,1).status()); // only the weapon would fit
        assertTrue(a.inventory.getItem(0).isEmpty());
        assertEquals(1000,a.balance);
        a.inventory.setItem(1,ItemStack.EMPTY);
        var first=k.quote(2,BOMB_AMMO,1).quote();
        a.rejectDebit=true;
        assertEquals(COMMIT_REJECTED,k.buy(3,first.id()).status());
        assertEquals(1000,a.balance); assertEquals(0,a.bombs()); assertTrue(a.inventory.getItem(0).isEmpty());
        assertTrue(k.buy(3,first.id()).replay());
        var next=k.quote(4,BOMB_AMMO,1).quote();
        a.rejectDebit=false;
        assertEquals(PURCHASED,k.buy(5,next.id()).status());
        assertEquals(10,a.bombs());
        assertTrue(a.inventory.getItem(0).is(Items.SPYGLASS));
        assertEquals(0,a.balance); assertEquals(1,a.revision);
        assertTrue(k.buy(5,next.id()).replay());
        assertEquals(STALE_QUOTE,k.buy(6,next.id()).status());
    }

    @Test void ownershipDrivesKindAndReacquisitionWithOffhandTopUpOnly() {
        var a=new Account(); var k=new Kiosk(a);
        a.balance=2000;
        a.inventory.setItem(40,new ItemStack(Items.EGG,4));
        var quote=k.quote(1,BOMB_AMMO,1).quote();
        assertEquals(WEAPON,quote.kind()); assertEquals(1000,quote.price()); assertEquals(4,quote.currentAmmo());
        assertEquals(6,quote.deliveredAmmo());
        assertEquals(PURCHASED,k.buy(2,quote.id()).status());
        assertEquals(10,a.bombs()); assertEquals(1000,a.balance);
        assertEquals(AMMO,k.quote(3,BOMB_AMMO,1).quote().kind());
        a.inventory.setItem(0,ItemStack.EMPTY);
        assertEquals(STALE_OFFER,k.quote(4,BOMB_STARTER,1).status());
        quote=k.quote(5,BOMB_AMMO,1).quote();
        assertEquals(WEAPON,quote.kind()); assertEquals(0,quote.deliveredAmmo());
        assertEquals(PURCHASED,k.buy(6,quote.id()).status());
        assertEquals(10,a.bombs()); assertEquals(0,a.balance);
    }

    @Test void distinctBoundedPaidQuotesAndIndependentSessions() {
        var a=new Account(); var k=new Kiosk(a);
        var pda=new Rac1PdaPurchases(Rac1ShopDelivery::stagePdaAmmo,Rac1BombCommerceTest::stage);
        var token=pda.open(k.player,0);
        a.balance=30;
        a.inventory.setItem(1,new ItemStack(Items.SPYGLASS)); // not held
        a.inventory.setItem(2,new ItemStack(Items.PAPER,64)); // unrelated Blaster fixture ammo
        a.inventory.setItem(40,new ItemStack(Items.EGG,16));
        a.inventory.setItem(3,new ItemStack(Items.EGG,23));
        assertEquals(STOCK_CAP,k.quote(1,BOMB_AMMO,2).status());
        assertEquals(STALE_OFFER,pda.quote(k.player,token,1,BOMB_STARTER,1,1,a.inventory,Items.BOW,a).status());
        var kiosk=k.quote(2,BOMB_AMMO,1).quote();
        assertEquals(5,kiosk.price()); assertEquals(BOMB_AMMO,kiosk.offer()); assertEquals(39,kiosk.currentAmmo());
        assertEquals(CONFLICT,k.quote(2,BLASTER,1).status());
        var portable=pda.quote(k.player,token,2,BOMB_AMMO,1,2,a.inventory,Items.BOW,a).quote();
        assertEquals(25,portable.price());
        assertEquals(STALE_QUOTE,k.buy(3,portable.id()).status());
        assertEquals(PURCHASED,pda.buy(k.player,token,3,portable.id(),3,a.inventory,Items.BOW,a).status());
        assertEquals(STALE_OFFER,k.buy(4,kiosk.id()).status());
        assertEquals(40,a.bombs()); assertEquals(64,a.inventory.getItem(2).getCount());
        assertEquals(5,a.balance);
    }
}
