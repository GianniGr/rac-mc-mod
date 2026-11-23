package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.FireService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Item identity/count for the future catalog; this inventory count is not a purchase cap. */
public final class BombGloveAmmo {
    private BombGloveAmmo() {}

    public static boolean matches(ItemStack stack) {
        return stack.is(RacMcItems.BOMB_GLOVE_AMMO.get());
    }

    public static int count(Player player) {
        var inventory = player.getInventory();
        int amount = matches(player.getOffhandItem()) ? player.getOffhandItem().getCount() : 0;
        for (var stack : inventory.items) if (matches(stack)) amount += stack.getCount();
        return amount;
    }

    static FireService.AmmoReservation reserve(Player player) {
        if (player.getAbilities().instabuild) return new FireService.AmmoReservation() {
            public void commit() {}
            public void refund() {}
        };
        var inventory = player.getInventory();
        int slot = net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;
        if (!matches(player.getOffhandItem())) {
            for (slot = 0; slot < inventory.items.size(); slot++) {
                if (matches(inventory.items.get(slot))) break;
            }
            if (slot == inventory.items.size()) return null;
        }
        int source = slot;
        ItemStack payment = inventory.getItem(source).split(1);
        inventory.setChanged();
        return new FireService.AmmoReservation() {
            private boolean resolved;
            public void commit() { resolved = true; }
            public void refund() {
                if (resolved) return;
                resolved = true;
                var current = inventory.getItem(source);
                if (current.isEmpty()) inventory.setItem(source, payment);
                else if (ItemStack.isSameItemSameComponents(current, payment)
                        && current.getCount() < current.getMaxStackSize()) current.grow(1);
                else inventory.placeItemBackInInventory(payment);
                inventory.setChanged();
            }
        };
    }
}
