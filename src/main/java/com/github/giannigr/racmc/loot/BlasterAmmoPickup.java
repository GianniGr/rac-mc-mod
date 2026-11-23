package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.item.weapon.BlasterAmmo;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Observes native item collection without changing admission, insertion, or its return value. */
public final class BlasterAmmoPickup {
    private BlasterAmmoPickup() {}

    public static boolean observe(Inventory inventory, ItemStack stack, BooleanSupplier insert,
                                  IntConsumer accepted) {
        if (!BlasterAmmo.matches(stack)) return insert.getAsBoolean();
        int offered = stack.getCount();
        int before = BlasterAmmo.count(inventory.player);
        boolean result = insert.getAsBoolean();
        int added = Math.min(offered, BlasterAmmo.count(inventory.player) - before);
        if (added > 0) accepted.accept(added);
        return result;
    }
}
