package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Positive-gain observer only; P34 owns the item registration and insertion rules. */
public final class BombGloveAmmoPickup {
    private static final ResourceKey<Item> AMMO = ResourceKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bomb_glove_ammo"));

    private BombGloveAmmoPickup() {}

    public static boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(holder -> holder.is(AMMO));
    }

    public static boolean observe(Inventory inventory, ItemStack stack, BooleanSupplier insert,
                                  IntConsumer accepted) {
        return observe(stack.getCount(), () -> count(inventory), insert, accepted);
    }

    static boolean observe(int offered, IntSupplier inventoryCount, BooleanSupplier insert,
                           IntConsumer accepted) {
        int before = inventoryCount.getAsInt();
        boolean result = insert.getAsBoolean();
        int added = Math.min(offered, inventoryCount.getAsInt() - before);
        if (added > 0) accepted.accept(added);
        return result;
    }

    private static int count(Inventory inventory) {
        var offhand = inventory.player.getOffhandItem();
        int count = matches(offhand) ? offhand.getCount() : 0;
        for (var stack : inventory.items) {
            if (matches(stack)) count += stack.getCount();
        }
        return count;
    }
}
