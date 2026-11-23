package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.item.Rac1PdaItem;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1ChestAcquisition.Reward;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Rac1BetaChestRewardBindingsTest {
    @BeforeAll
    static void vanillaBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registeredFactoriesAwardFreshUsablePlainStacksWithoutPlayerContext() {
        assertInstanceOf(Rac1PdaItem.class, RacMcItems.PDA.get());
        check(Reward.BOLT_GRABBER, "racmc:bolt_grabber", RacMcItems.BOLT_GRABBER.get());
        check(Reward.PDA, "racmc:pda", RacMcItems.PDA.get());
    }

    private static void check(Reward reward, String id, Item item) {
        assertSame(item, BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        var vanilla = new ItemStack(Items.BOOK, 2);
        var loot = new ObjectArrayList<ItemStack>();
        loot.add(vanilla);
        // Only prove the registered provider here. Loot context and chance are native-fixture cases.
        Rac1ChestAcquisition.award(reward, loot, null);
        assertEquals(2, loot.size());
        assertSame(vanilla, loot.getFirst());
        var first = loot.getLast();
        assertEquals(1, first.getCount());
        assertEquals(1, first.getMaxStackSize());
        assertTrue(ItemStack.matches(new ItemStack(item), first));

        var repeat = new ObjectArrayList<ItemStack>();
        Rac1ChestAcquisition.award(reward, repeat, null);
        assertEquals(1, repeat.size());
        assertNotSame(first, repeat.getFirst(), "Each generated grant must use a new stack");
        assertTrue(ItemStack.matches(first, repeat.getFirst()));
    }
}
