package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.item.RacMcItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared, uncapped identity/count boundary for firing and future shop delivery. */
public final class BlasterAmmo {
    private BlasterAmmo() {}
    public static boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(RacMcItems.BLASTER_AMMO.get());
    }

    /** Main inventory (including the selected slot) and offhand, each counted exactly once. */
    public static int count(Player player) {
        return Rac1AmmoPolicy.count(player.getInventory(), RacMcItems.BLASTER_AMMO.get());
    }
}
