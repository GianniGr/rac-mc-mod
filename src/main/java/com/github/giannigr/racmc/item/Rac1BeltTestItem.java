package com.github.giannigr.racmc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Provisional physical fixture; the only equip path is RaC Gear. */
final class Rac1BeltTestItem extends Item {
    Rac1BeltTestItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.racmc.belt_fixture").withStyle(ChatFormatting.GRAY));
    }
}
