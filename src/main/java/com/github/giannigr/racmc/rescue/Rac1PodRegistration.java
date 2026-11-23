package com.github.giannigr.racmc.rescue;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Only generation supplies a rescue site; the item places an empty decorative hull. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1PodRegistration {
    private static final ResourceLocation POD = id("crashed_pod");
    public static final DeferredHolder<Block, Rac1PodBlock> BLOCK =
            DeferredHolder.create(Registries.BLOCK, POD);
    public static final DeferredHolder<Item, Item> DECORATIVE =
            DeferredHolder.create(Registries.ITEM, id("decorative_crashed_pod"));
    static final DeferredHolder<Block, Rac1PodPartBlock> PART =
            DeferredHolder.create(Registries.BLOCK, id("crashed_pod_hull"));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Rac1PodMarker>> MARKER =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, POD);

    private Rac1PodRegistration() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, POD, () -> new Rac1PodBlock(hullProperties()));
        event.register(Registries.BLOCK, PART.getId(), () -> new Rac1PodPartBlock(hullProperties().dynamicShape()));
        event.register(Registries.ITEM, DECORATIVE.getId(), () -> new Rac1DecorativePodItem(new Item.Properties()));
        event.register(Registries.BLOCK_ENTITY_TYPE, POD, () ->
                BlockEntityType.Builder.of(Rac1PodMarker::new, BLOCK.get()).build(null));
        event.register(Registries.FEATURE, id("clank_crash_site"), () -> (Feature<?>) new Rac1CrashSiteFeature());
    }

    private static BlockBehaviour.Properties hullProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noOcclusion().noLootTable()
                .strength(1, 3_600_000).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }
}
