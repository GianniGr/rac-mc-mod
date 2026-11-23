package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/** Bounded stock catalog. Shop caps are not limits on firing, pickup or item storage. */
public enum Rac1AmmoPolicy {
    BLASTER("blaster", "blaster_ammo", 25, 200, 1, 10, 2500, 100),
    BOMB_GLOVE("bomb_glove", "bomb_glove_ammo", 3, 40, 5, 25, 1000, 10);

    public static final int MAX_ORDER = 200;
    private final ResourceLocation weaponId, ammoId;
    private final int pickupUnits, cap, kioskPrice, pdaPrice, weaponPrice, weaponFill;

    Rac1AmmoPolicy(String weaponId, String ammoId, int pickupUnits, int cap, int kioskPrice, int pdaPrice,
                   int weaponPrice, int weaponFill) {
        this.weaponId = key(weaponId);
        this.ammoId = key(ammoId);
        this.pickupUnits = pickupUnits;
        this.cap = cap;
        this.kioskPrice = kioskPrice;
        this.pdaPrice = pdaPrice;
        this.weaponPrice = weaponPrice;
        this.weaponFill = weaponFill;
    }

    public ResourceLocation weaponId() { return weaponId; }
    public ResourceLocation ammoId() { return ammoId; }
    public Optional<Item> weapon() { return BuiltInRegistries.ITEM.getOptional(weaponId); }
    public Optional<Item> ammo() { return BuiltInRegistries.ITEM.getOptional(ammoId); }
    public int pickupUnits() { return pickupUnits; }
    public int cap() { return cap; }
    public int unitPrice(boolean portable) { return portable ? pdaPrice : kioskPrice; }
    public int weaponPrice() { return weaponPrice; }
    public int weaponFill() { return weaponFill; }
    public boolean owns(Inventory inventory) { return weapon().filter(item -> owns(inventory, item)).isPresent(); }
    public int count(Inventory inventory) { return ammo().map(item -> count(inventory, item)).orElse(0); }

    public static boolean owns(Inventory inventory, Item weapon) {
        return inventory.items.stream().anyMatch(stack -> stack.is(weapon)) || inventory.offhand.getFirst().is(weapon);
    }

    public static int count(Inventory inventory, Item ammo) {
        var offhand = inventory.offhand.getFirst();
        int count = offhand.is(ammo) ? offhand.getCount() : 0;
        for (var stack : inventory.items) if (stack.is(ammo)) count += stack.getCount();
        return count;
    }

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }
}
