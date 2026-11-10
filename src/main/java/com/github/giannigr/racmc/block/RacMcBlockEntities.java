package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The kinds of block entity of the mod.
 *
 * <p>The crates of the game are drawn by the renderer of the block entities of the game of
 * blocks, so every crate of the mod has a block entity. All the crates share one kind of block
 * entity, because they are all the same thing: a picture of a crate of the game.
 *
 * <p>The registration holds no types of the client, so that a dedicated server can start with
 * it.
 */
public final class RacMcBlockEntities {

    /** The kinds of block entity of the mod, registered under the identifier of the mod. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RacMcConstants.MOD_ID);

    /** The block entity of the crates of the game. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Rac1CrateBlockEntity>>
            CRATE = BLOCK_ENTITIES.register("crate", () -> BlockEntityType.Builder
                    .of(Rac1CrateBlockEntity::new, RacMcBlocks.crateBlocks())
                    .build(null));

    private RacMcBlockEntities() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers the kinds of block entity of the mod.
     *
     * @param modEventBus the event bus of the mod
     */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
