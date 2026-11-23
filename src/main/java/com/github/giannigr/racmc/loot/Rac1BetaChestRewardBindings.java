package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.loot.Rac1ChestAcquisition.Reward;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Exact beta gadget stack factories; no player or acquisition-specific item data is required. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BetaChestRewardBindings {
    private Rac1BetaChestRewardBindings() {}

    @SubscribeEvent
    public static void bind(FMLCommonSetupEvent event) {
        Rac1ChestAcquisition.bind(Reward.BOLT_GRABBER, RacMcItems.BOLT_GRABBER.getId(),
                context -> new ItemStack(RacMcItems.BOLT_GRABBER.get()));
        Rac1ChestAcquisition.bind(Reward.PDA, RacMcItems.PDA.getId(),
                context -> new ItemStack(RacMcItems.PDA.get()));
    }
}
