package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.loot.Rac1ChestAcquisition.Reward;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** One additive award after NeoForge has checked the exact table and the single chance roll. */
final class Rac1ChestAcquisitionModifier extends LootModifier {
    static final MapCodec<Rac1ChestAcquisitionModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(Reward.CODEC.fieldOf("reward").forGetter(modifier -> modifier.reward))
                    .apply(instance, Rac1ChestAcquisitionModifier::new));

    private final Reward reward;

    Rac1ChestAcquisitionModifier(LootItemCondition[] conditions, Reward reward) {
        super(conditions);
        this.reward = reward;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Rac1ChestAcquisition.award(reward, generatedLoot, context);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
