package com.github.giannigr.racmc.item.weapon;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Read-only identity/count boundary for the accepted arrow PoC, NOT original ammo ownership. */
public final class BlasterAmmo {
    private BlasterAmmo() {}
    public static boolean matches(ItemStack stack) { return !stack.isEmpty() && stack.is(Items.ARROW); }
    public static int count(Player player) {
        int count = matches(player.getOffhandItem()) ? player.getOffhandItem().getCount() : 0;
        for (var stack : player.getInventory().items) if (matches(stack)) count += stack.getCount();
        return count;
    }
}
