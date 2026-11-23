package com.github.giannigr.racmc.shop;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.shop.Rac1ShopDelivery.*;
import static com.github.giannigr.racmc.shop.ShopTestAmmo.stage;
import static com.github.giannigr.racmc.shop.ShopTestAmmo.arrows;

/** Actual vanilla inventory/stack staging; singleton Bow is a synthetic stand-in, not a mod registry test. */
class Rac1ShopDeliveryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static Inventory empty() { return new Inventory(null); }
    static Inventory owned() { var inv=empty(); inv.setItem(0,new ItemStack(Items.BOW)); return inv; }
    static Inventory full() {
        var inv=empty(); for (int i=0;i<36;i++) inv.setItem(i,new ItemStack(Items.STONE,64)); return inv;
    }
    @Test void weaponAndWholeInitialFillAreStagedWithoutChangingAnyLiveSlot() {
        var inv=empty(); var result=stage(inv,Items.BOW,Kind.WEAPON,1);
        assertNull(result.failure()); assertEquals(2500,result.delivery().price());
        assertEquals(100,result.delivery().grantedArrows()); assertEquals(0,arrows(inv));
        assertEquals(Kind.WEAPON,offer(inv,Items.BOW));
        install(inv,result.delivery().replacements());
        assertEquals(100,arrows(inv)); assertEquals(Kind.AMMO,offer(inv,Items.BOW));
    }
    @Test void initialFillIsMaximumNotAdditionAndDoesNotRemoveOverCapArrows() {
        for (int count:new int[]{0,40,99,100,150,200,225}) {
            var inv=empty();
            for (int i=0,left=count;left>0;i++) { int n=Math.min(64,left); inv.setItem(i,new ItemStack(Items.PAPER,n)); left-=n; }
            var result=stage(inv,Items.BOW,Kind.WEAPON,1);
            assertNull(result.failure()); assertEquals(Math.max(0,100-count),result.delivery().grantedArrows());
            install(inv,result.delivery().replacements()); assertEquals(Math.max(count,100),arrows(inv));
        }
    }
    @Test void pickup25IsNotAShopBundleAndRequestedUnitsAreNeverSilentlyClipped() {
        for (int qty:new int[]{1,25,100,200}) {
            var inv=owned(); var d=stage(inv,Items.BOW,Kind.AMMO,qty).delivery();
            assertEquals(qty,d.price()); assertEquals(qty,d.grantedArrows());
        }
        var inv=owned(); inv.setItem(1,new ItemStack(Items.PAPER,199));
        assertEquals(Failure.STOCK_CAP,stage(inv,Items.BOW,Kind.AMMO,2).failure());
        assertEquals(199,arrows(inv));
        assertNull(stage(inv,Items.BOW,Kind.AMMO,1).failure());
    }
    @Test void atAndAboveCapRejectWithoutChangingExistingStacks() {
        for (int count:new int[]{200,201,500}) {
            var inv=owned(); inv.setItem(1,new ItemStack(Items.PAPER,count));
            assertEquals(Failure.STOCK_CAP,stage(inv,Items.BOW,Kind.AMMO,1).failure());
            assertEquals(count,arrows(inv));
        }
    }
    @Test void oneOrTwoFreeSlotsCannotHoldWeaponPlusOneHundredArrowsButThreeCan() {
        for (int free=1;free<=3;free++) {
            var inv=full(); for (int i=0;i<free;i++) inv.setItem(i,ItemStack.EMPTY);
            var staged=stage(inv,Items.BOW,Kind.WEAPON,1);
            if (free<3) {
                assertEquals(Failure.FULL,staged.failure()); assertEquals(Kind.WEAPON,offer(inv,Items.BOW));
                assertEquals(0,arrows(inv)); assertTrue(inv.getItem(0).isEmpty());
            } else assertNull(staged.failure());
        }
    }
    @Test void completeDeliveryCanUseCompatibleMergeCapacityWithNoEmptySlot() {
        var inv=full(); inv.setItem(0,new ItemStack(Items.BOW));
        inv.setItem(1,new ItemStack(Items.PAPER,63)); inv.setItem(40,new ItemStack(Items.PAPER,63));
        var d=stage(inv,Items.BOW,Kind.AMMO,2).delivery(); assertNotNull(d);
        install(inv,d.replacements());
        assertEquals(64,inv.getItem(1).getCount()); assertEquals(64,inv.getItem(40).getCount());
        assertEquals(Failure.FULL,stage(inv,Items.BOW,Kind.AMMO,1).failure());
    }
    @Test void offhandCountsButEmptyOffhandAndArmorAreNotAutomaticDeliverySlots() {
        var inv=full(); inv.setItem(40,new ItemStack(Items.BOW));
        assertEquals(Kind.AMMO,offer(inv,Items.BOW));
        assertEquals(Failure.FULL,stage(inv,Items.BOW,Kind.AMMO,1).failure());
        inv.setItem(40,new ItemStack(Items.PAPER,63)); inv.setItem(0,new ItemStack(Items.BOW));
        inv.setItem(36,new ItemStack(Items.PAPER,100));
        assertEquals(63,arrows(inv));
        assertNull(stage(inv,Items.BOW,Kind.AMMO,1).failure());
    }
    @Test void tippedSpectralAndIncompatibleComponentsDoNotCreateFalseInsertionCapacity() {
        var inv=full(); inv.setItem(0,new ItemStack(Items.BOW));
        inv.setItem(1,new ItemStack(Items.TIPPED_ARROW,64)); inv.setItem(2,new ItemStack(Items.SPECTRAL_ARROW,64));
        var named=new ItemStack(Items.PAPER,63); named.set(DataComponents.CUSTOM_NAME,Component.literal("synthetic"));
        inv.setItem(3,named);
        assertEquals(63,arrows(inv));
        assertEquals(Failure.FULL,stage(inv,Items.BOW,Kind.AMMO,1).failure());
        inv.setItem(40,new ItemStack(Items.PAPER,63));
        assertNull(stage(inv,Items.BOW,Kind.AMMO,1).failure());
    }
    @Test void invalidQuantityAndInventoryDerivedOfferFailClosed() {
        var inv=empty();
        for (int qty:new int[]{Integer.MIN_VALUE,-1,0,2,201,Integer.MAX_VALUE})
            assertEquals(Failure.INVALID_QUANTITY,stage(inv,Items.BOW,Kind.WEAPON,qty).failure());
        assertEquals(Failure.STALE_OFFER,stage(inv,Items.BOW,Kind.AMMO,1).failure());
        inv.setItem(0,new ItemStack(Items.BOW));
        assertEquals(Failure.STALE_OFFER,stage(inv,Items.BOW,Kind.WEAPON,1).failure());
    }
    @Test void quoteSnapshotDetectsSlotCountComponentsAndArmorChanges() {
        var inv=empty(); var d=stage(inv,Items.BOW,Kind.WEAPON,1).delivery();
        assertTrue(d.unchanged(inv)); inv.setItem(36,new ItemStack(Items.STONE)); assertFalse(d.unchanged(inv));
        inv.setItem(36,ItemStack.EMPTY); assertTrue(d.unchanged(inv));
        inv.setItem(1,new ItemStack(Items.ARROW)); assertFalse(d.unchanged(inv));
    }
}
