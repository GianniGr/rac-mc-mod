package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.RacMcBlocks;
import org.junit.jupiter.api.Test;

class Rac1WrenchItemTest {
    @Test
    void attributesAreTotal5Point1HpAndTenTickChargeInMainHandOnly() {
        var attributes = Rac1WrenchItem.attributes();
        double damage = 1;
        double speed = 4;
        for (var entry : attributes.modifiers()) {
            assertEquals(EquipmentSlotGroup.MAINHAND, entry.slot());
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                damage += entry.modifier().amount();
            } else if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
                speed += entry.modifier().amount();
            } else {
                fail("Unexpected wrench attribute");
            }
        }
        assertEquals(5.1, damage, 1e-9);
        assertEquals(10, 20 / speed, 1e-9);
        assertEquals(2, attributes.modifiers().size());
    }

    @Test
    void itemIsReusableAndNotAStackOfTools() {
        var item = RacMcItems.OMNIWRENCH.get();
        var stack = item.getDefaultInstance();
        assertFalse(stack.isDamageableItem());
        assertEquals(1, stack.getMaxStackSize());
    }

    @Test
    void nativeCriticalEventIsSuppressedOnlyForWrenchMainHand() {
        var wrench = new CriticalHitEvent(null, null, 1.5f, true);
        Rac1WrenchEvents.applyCriticalPolicy(RacMcItems.OMNIWRENCH.get().getDefaultInstance(),
                wrench);
        assertFalse(wrench.isCriticalHit());
        assertEquals(1, wrench.getDamageMultiplier());
        var other = new CriticalHitEvent(null, null, 1.5f, true);
        Rac1WrenchEvents.applyCriticalPolicy(RacMcItems.BLASTER.get().getDefaultInstance(), other);
        assertTrue(other.isCriticalHit());
        assertEquals(1.5f, other.getDamageMultiplier());
    }

    @Test
    void nativeAttackGateDeniesExplosiveAndDoesNotChangeOtherBlockMining() {
        var item = RacMcItems.OMNIWRENCH.get();
        assertTrue(item.canAttackBlock(Blocks.STONE.defaultBlockState(), null, BlockPos.ZERO, null));
        assertTrue(item.canAttackBlock(Blocks.CHEST.defaultBlockState(), null, BlockPos.ZERO, null));
        for (var definition : Rac1Crates.definitions()) {
            var state = RacMcBlocks.crate(definition).get().defaultBlockState();
            assertEquals(!definition.key().equals("explosive_crate"),
                    item.canAttackBlock(state, null, BlockPos.ZERO, null));
        }
    }
}
