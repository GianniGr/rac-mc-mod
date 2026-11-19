package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.item.RacMcItems;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Registered content identity, not a shop ownership or mutation service. */
public final class RangedWeapons {
    private RangedWeapons() {}
    public static Optional<WeaponDefinition> definition(ItemStack stack) {
        return stack.is(RacMcItems.BLASTER.get()) ? Optional.of(Rac1BlasterDefinition.INSTANCE) : Optional.empty();
    }
}
