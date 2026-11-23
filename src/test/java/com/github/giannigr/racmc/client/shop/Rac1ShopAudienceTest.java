package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopAudienceTest {
    @Test void shopperLocalClocksKeepFullUnfoldAndFoldWithoutSharingExitOrReentry() {
        var a=new com.github.giannigr.racmc.shop.Rac1ShopMotion();
        var b=new com.github.giannigr.racmc.shop.Rac1ShopMotion();
        var idle=new com.github.giannigr.racmc.shop.Rac1ShopMotion();
        var unfold=com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.UNFOLD;
        var open=com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.OPEN;
        var closed=com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.CLOSED;
        assertEquals(unfold,a.update(true,0).phase());
        assertEquals(unfold,a.update(true,39).phase(),"Do not shorten the original ~0.7s unfold");
        assertEquals(open,a.update(true,40).phase());
        assertEquals(unfold,b.update(true,40).phase());
        assertEquals(closed,idle.update(false,40).phase(),"Other clients remain idle");
        a.update(false,40);
        assertEquals(com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.FOLD,a.update(false,48).phase());
        assertEquals(open,b.update(true,80).phase(),"A exit cannot fold B's private kiosk");
        assertEquals(closed,a.update(false,88).phase());
        assertEquals(unfold,a.update(true,89).phase(),"Fresh entry starts a full private unfold");
        assertEquals(closed,idle.update(false,89).phase());
    }
    @Test void onlyOwnReadyCompositionSuppressesExactTargetNeverAnotherVendorOrIdleObserver() {
        Object a=new Object(),b=new Object();
        assertTrue(Rac1ShopAudience.suppressWorld(a,a,true));
        assertFalse(Rac1ShopAudience.suppressWorld(a,a,false),"Loading must retain the idle world kiosk");
        assertFalse(Rac1ShopAudience.suppressWorld(a,b,true));
        assertFalse(Rac1ShopAudience.suppressWorld(a,null,true));
        assertFalse(Rac1ShopAudience.suppressWorld(null,null,true));
        assertTrue(Rac1ShopAudience.suppressWorld(b,b,true),"Independent shopper has its own composition");
    }
}
