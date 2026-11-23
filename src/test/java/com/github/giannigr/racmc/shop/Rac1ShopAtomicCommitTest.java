package com.github.giannigr.racmc.shop;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the SAME mutation sequence as the production adapter, not an alternate insertion/debit algorithm. */
class Rac1ShopAtomicCommitTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static final class Debit implements Rac1ShopAtomicCommit.Debit {
        final Inventory inventory;
        long balance=10000,revision;
        int calls;
        boolean reject,throwBefore,throwAfter;
        Debit(Inventory inventory) { this.inventory=inventory; }
        public long balance() { return balance; }
        public long revision() { return revision; }
        public boolean tryDebit(long amount) {
            calls++;
            if (throwBefore) throw new IllegalStateException("synthetic pre-debit failure");
            if (reject) return false;
            // Model the ledger boundary, not transport: complete inventory is already installed.
            assertEquals(Rac1ShopDelivery.Kind.AMMO,Rac1ShopDelivery.offer(inventory,Items.BOW));
            assertEquals(100,ShopTestAmmo.arrows(inventory));
            balance-=amount; revision++;
            if (throwAfter) throw new IllegalStateException("synthetic post-debit notification failure");
            return true;
        }
    }
    @Test void completeInventoryPrecedesDebitAndSuccessChangesBothExactlyOnce() {
        var inv=new Inventory(null); var untouched=new ItemStack(Items.STONE,23); inv.setItem(35,untouched);
        var debit=new Debit(inv);
        var plan=ShopTestAmmo.stage(inv,Items.BOW,Rac1ShopDelivery.Kind.WEAPON,1).delivery();
        var result=Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit);
        assertTrue(result.committed()); assertNull(result.failure());
        assertSame(untouched,inv.getItem(35),"Do not replace unrelated live stack identities");
        assertEquals(7500,debit.balance); assertEquals(1,debit.revision);
        assertFalse(Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit).committed());
        assertEquals(1,debit.calls);
    }
    @Test void falseOrPreDebitExceptionRestoresAllSlotsAndDoesNotChangeCurrency() {
        for (boolean throwing:new boolean[]{false,true}) {
            var inv=new Inventory(null); inv.setItem(5,new ItemStack(Items.STONE,23));
            var debit=new Debit(inv); debit.reject=!throwing; debit.throwBefore=throwing;
            var plan=ShopTestAmmo.stage(inv,Items.BOW,Rac1ShopDelivery.Kind.WEAPON,1).delivery();
            var result=Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit);
            assertFalse(result.committed()); assertEquals(throwing,result.failure()!=null);
            assertTrue(plan.unchanged(inv)); assertEquals(23,inv.getItem(5).getCount());
            assertEquals(10000,debit.balance); assertEquals(0,debit.revision);
        }
    }
    @Test void postDebitExceptionKeepsWholeDeliveryAndNeverRefundsOrRegrants() {
        var inv=new Inventory(null); var debit=new Debit(inv); debit.throwAfter=true;
        var plan=ShopTestAmmo.stage(inv,Items.BOW,Rac1ShopDelivery.Kind.WEAPON,1).delivery();
        var result=Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit);
        assertTrue(result.committed()); assertNotNull(result.failure());
        assertEquals(7500,debit.balance); assertEquals(100,ShopTestAmmo.arrows(inv));
        assertFalse(Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit).committed());
        assertEquals(1,debit.calls); assertEquals(7500,debit.balance);
    }
    @Test void lastMomentInventoryBalanceRevisionAndFundsChangesNeverEnterDebit() {
        for (int mutation=0;mutation<4;mutation++) {
            var inv=new Inventory(null); var debit=new Debit(inv);
            var plan=ShopTestAmmo.stage(inv,Items.BOW,Rac1ShopDelivery.Kind.WEAPON,1).delivery();
            if (mutation==0) inv.setItem(35,new ItemStack(Items.STONE));
            if (mutation==1) debit.balance++;
            if (mutation==2) debit.revision++;
            if (mutation==3) debit.balance=2499;
            assertFalse(Rac1ShopAtomicCommit.run(inv,plan,10000,0,debit).committed());
            assertEquals(0,debit.calls); assertEquals(0,ShopTestAmmo.arrows(inv));
        }
    }
}
