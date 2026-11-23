package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.Rac1ShopMotion;

/** Shopper-local 20Hz tick clock. World time is server-correctable, not animation time. */
final class Rac1ShopLocalMotion {
    private final Rac1ShopMotion motion=new Rac1ShopMotion();
    private long frame;
    private Rac1ShopMotion.Stamp stamp;

    Rac1ShopMotion.Stamp tick(boolean active) {
        frame+=3;
        stamp=motion.update(active,frame);
        return stamp;
    }

    Rac1ShopMotion.Frame display(float partialTick) {
        if (stamp==null) throw new IllegalStateException("Physical motion has not ticked");
        return Rac1ShopMotion.display(stamp.phase(),stamp.startFrame(),frame+partialTick*3.0);
    }
}
