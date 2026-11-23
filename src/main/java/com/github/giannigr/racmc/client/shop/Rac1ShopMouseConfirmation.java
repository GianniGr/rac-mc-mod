package com.github.giannigr.racmc.client.shop;

import java.util.UUID;

/**
 * Minecraft mouse-only consent adaptation: the original Buy/Yes monitor shares
 * a hit target. A double-click must not count as a second deliberate gesture.
 * Keyboard/controller button edges remain governed by Rac1ShopClientFlow.
 */
public final class Rac1ShopMouseConfirmation {
    public static final long READ_MILLIS=500;
    private UUID quote;
    private long shown;
    private boolean down,leftBuyControl;

    public boolean press() {
        if (down) return false;
        down=true;
        return true;
    }
    public void release() { down=false; }
    /** Call only after the matching quote's actual frame is submitted. */
    public void presented(UUID id,long millis) {
        if (id==null) { clear(); return; }
        if (!id.equals(quote)) {
            quote=id; shown=millis; leftBuyControl=false;
        }
    }
    /** Only a released pointer moving outside the original Buy monitor can arm Yes. */
    public void movedOutsideBuy() {
        if (quote!=null && !down) leftBuyControl=true;
    }
    /** Called on a fresh press; caller still must satisfy the normal flow/quote guards. */
    public boolean approve(UUID id,long millis) {
        if (!down || quote==null || !quote.equals(id) || !leftBuyControl
                || millis<shown || millis-shown<READ_MILLIS) return false;
        clear(); // consume this gesture; cannot arm a replacement quote
        return true;
    }
    /** Cancellation/quote replacement clears consent, not a physically held button. */
    public void clear() { quote=null; leftBuyControl=false; }
}
