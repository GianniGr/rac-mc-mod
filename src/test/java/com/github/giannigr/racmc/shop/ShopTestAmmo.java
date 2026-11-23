package com.github.giannigr.racmc.shop;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

/** Vanilla paper substitutes for an unbound DeferredHolder only in JVM inventory fixtures. */
final class ShopTestAmmo {
    static int arrows(Inventory inventory) {
        int count=0;
        for (var stack:inventory.items) if (stack.is(Items.PAPER)) count+=stack.getCount();
        for (var stack:inventory.offhand) if (stack.is(Items.PAPER)) count+=stack.getCount();
        return count;
    }
    static Rac1ShopDelivery.Staged stage(Inventory inventory,Item weapon,Rac1ShopDelivery.Kind kind,int quantity) {
        return Rac1ShopDelivery.stageWithAmmo(inventory,weapon,kind,quantity,Items.PAPER,arrows(inventory));
    }
}
