package com.github.giannigr.racmc.client.shop;

/** Owner policy: only the active shopper owns a private composition; world vendors stay idle. */
public final class Rac1ShopAudience {
    private Rac1ShopAudience() {}
    public static boolean suppressWorld(Object drawnVendor,Object shopperVendor,boolean privateCompositionVisible) {
        return privateCompositionVisible && drawnVendor!=null && drawnVendor==shopperVendor;
    }
}
