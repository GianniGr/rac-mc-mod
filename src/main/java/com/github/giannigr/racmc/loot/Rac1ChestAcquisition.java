package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The item owners supply the canonical new Survival stacks; this layer owns only acquisition. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1ChestAcquisition {
    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "chest_acquisition");
    private static final ConcurrentHashMap<Reward, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final Set<Reward> WARNED = ConcurrentHashMap.newKeySet();

    public enum Reward implements StringRepresentable {
        BOLT_GRABBER("bolt_grabber"),
        PDA("pda");

        static final Codec<Reward> CODEC = StringRepresentable.fromEnum(Reward::values);
        private final String alias;

        Reward(String alias) {
            this.alias = alias;
        }

        /** A loot reward alias, not an item registry ID. */
        @Override
        public String getSerializedName() {
            return alias;
        }
    }

    private record Binding(ResourceLocation item, Function<LootContext, ItemStack> factory) {}

    private Rac1ChestAcquisition() {}

    @SubscribeEvent
    public static void registerModifier(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                MODIFIER_ID, () -> Rac1ChestAcquisitionModifier.CODEC);
    }

    /**
     * Bind once during mod loading. The factory is called only after a matching table wins its
     * loot roll and must return a fresh, usable count-one Survival stack with any required
     * components/provenance. The context provides a server level, but might have no player.
     */
    public static void bind(Reward reward, ResourceLocation item, Function<LootContext, ItemStack> factory) {
        Objects.requireNonNull(reward);
        Objects.requireNonNull(item);
        Objects.requireNonNull(factory);
        if (!item.getNamespace().equals(RacMcConstants.MOD_ID)) {
            throw new IllegalArgumentException("Chest acquisition must bind a RaC item: " + item);
        }
        if (BINDINGS.putIfAbsent(reward, new Binding(item, factory)) != null) {
            throw new IllegalStateException("Chest reward already bound: " + reward.getSerializedName());
        }
    }

    static void award(Reward reward, ObjectArrayList<ItemStack> loot, LootContext context) {
        Binding binding = BINDINGS.get(reward);
        if (binding == null) {
            warnOnce(reward, "no combined-build item factory");
            return;
        }
        try {
            if (!appendOne(loot, binding.item(), binding.factory().apply(context))) {
                warnOnce(reward, "factory did not return one " + binding.item());
            }
        } catch (RuntimeException exception) {
            // An item integration fault must not interfere with the vanilla chest filling.
            if (WARNED.add(reward)) {
                RacMcMod.LOGGER.error("Could not construct chest reward {}; keeping vanilla loot",
                        reward.getSerializedName(), exception);
            }
        }
    }

    static boolean appendOne(ObjectArrayList<ItemStack> loot, ResourceLocation expected, ItemStack reward) {
        if (reward == null || reward.isEmpty() || reward.getCount() != 1
                || !expected.equals(BuiltInRegistries.ITEM.getKey(reward.getItem()))) {
            return false;
        }
        loot.add(reward);
        return true;
    }

    private static void warnOnce(Reward reward, String message) {
        if (WARNED.add(reward)) {
            RacMcMod.LOGGER.warn("Skipping chest reward {}: {}; keeping vanilla loot",
                    reward.getSerializedName(), message);
        }
    }
}
