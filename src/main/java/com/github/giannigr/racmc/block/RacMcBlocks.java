package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import com.github.giannigr.racmc.asset.Rac1Crates;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks of the mod.
 *
 * <p>The blocks of the mod are the crates of the game: every crate of the table of
 * {@link Rac1Crates} becomes a block of the mod, and the block is registered under the key of
 * the crate. A crate that is added to the table is therefore a block of the mod, an item of the
 * mod and a place in the tab of the creative mode, without anything else having to be written
 * down.
 *
 * <p>The registration of the blocks holds no types of the client, so that a dedicated server can
 * start with them.
 */
public final class RacMcBlocks {

    /** The blocks of the mod, registered under the identifier of the mod. */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RacMcConstants.MOD_ID);

    /** The crates of the game, by the key they are known by. */
    private static final Map<String, DeferredHolder<Block, Rac1CrateBlock>> CRATES =
            new LinkedHashMap<>();

    static {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            CRATES.put(definition.key(), BLOCKS.registerBlock(definition.key(),
                    properties -> new Rac1CrateBlock(definition, properties),
                    Rac1CrateBlock.properties(definition)));
        }
    }

    private RacMcBlocks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The crates of the game, by the key they are known by.
     *
     * @return the holders of the blocks of the crates, in the order of the table of the crates
     */
    public static Map<String, DeferredHolder<Block, Rac1CrateBlock>> crates() {
        return Collections.unmodifiableMap(CRATES);
    }

    /**
     * The block of the given crate.
     *
     * @param definition the description of the crate
     * @return the holder of the block of the crate
     */
    public static DeferredHolder<Block, Rac1CrateBlock> crate(Rac1CrateDefinition definition) {
        DeferredHolder<Block, Rac1CrateBlock> holder = CRATES.get(definition.key());
        if (holder == null) {
            throw new IllegalArgumentException("The mod has no block for the crate "
                    + definition.key() + ".");
        }
        return holder;
    }

    /**
     * The blocks of all the crates of the game.
     *
     * @return the blocks of the crates
     */
    public static Block[] crateBlocks() {
        Block[] blocks = new Block[CRATES.size()];
        int index = 0;
        for (DeferredHolder<Block, Rac1CrateBlock> holder : CRATES.values()) {
            blocks[index++] = holder.get();
        }
        return blocks;
    }

    /**
     * Registers the blocks of the mod.
     *
     * @param modEventBus the event bus of the mod
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
