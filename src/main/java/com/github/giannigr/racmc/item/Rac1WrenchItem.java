package com.github.giannigr.racmc.item;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Reusable original OmniWrench. Native Player.attack owns charge, armor and protection. */
public final class Rac1WrenchItem extends Item {
    public static final double TOTAL_DAMAGE = 5.1;
    public static final int COOLDOWN_TICKS = 10;

    public Rac1WrenchItem(Properties properties) {
        super(properties.stacksTo(1).attributes(attributes()));
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player) {
        var action = Rac1WrenchEvents.action(state);
        return action != Rac1WrenchBlockPolicy.Action.PROTECTED
                && action != Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED
                && super.canAttackBlock(state, level, pos, player);
    }

    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        TOTAL_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                        20.0 / COOLDOWN_TICKS - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
