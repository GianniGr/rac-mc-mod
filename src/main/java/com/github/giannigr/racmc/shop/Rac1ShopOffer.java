package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;

/** Server allowlist; the client selects a row, never a price, an item or a grant. */
public enum Rac1ShopOffer {
    BLASTER(Rac1AmmoPolicy.BLASTER),
    BOMB_AMMO(Rac1AmmoPolicy.BOMB_GLOVE),
    BOMB_STARTER(Rac1AmmoPolicy.BOMB_GLOVE);

    private final Rac1AmmoPolicy stock;
    Rac1ShopOffer(Rac1AmmoPolicy stock) { this.stock = stock; }
    public Rac1AmmoPolicy stock() { return stock; }
    /** BOMB_AMMO is the stable Bomb stock wire ID; kiosk kind is derived by the server.
     * The legacy free-claim ordinal is reserved and never purchasable. */
    public boolean permitted(boolean portable) { return this != BOMB_STARTER; }
}
