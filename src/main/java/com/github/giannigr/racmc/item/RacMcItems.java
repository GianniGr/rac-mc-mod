package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.RacMcBlocks;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The items of the mod.
 *
 * <p>The Blaster of the game is the first item of the mod. It is registered here and given to
 * the event bus of the mod in the constructor of {@link com.github.giannigr.racmc.RacMcMod}.
 *
 * <p>The crates of the game are blocks of the mod, so every crate of the table of
 * {@link Rac1Crates} gets an item of its own here. The item places the block of the crate.
 */
public final class RacMcItems {

    /** The items of the mod, registered under the identifier of the mod. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RacMcConstants.MOD_ID);

    /**
     * The Blaster of the game.
     *
     * <p>The weapon holds one round at a time, so a stack of it holds a single weapon.
     */
    public static final DeferredItem<BlasterItem> BLASTER =
            ITEMS.registerItem("blaster", BlasterItem::new, new Item.Properties().stacksTo(1));

    /**
     * The picture of the tab of the creative mode of the mod.
     *
     * <p>The game draws the sign of a tab as an item, so the picture of the mod is an item of its
     * own. The item is a sign and nothing else: it is in no list of the creative mode and it has
     * no use in a world. Its picture is the picture the owner of the project gave for the tab.
     */
    public static final DeferredItem<Item> TAB_ICON =
            ITEMS.registerItem("tab_icon", Item::new, new Item.Properties().stacksTo(1));

    /** The items of the crates of the game, by the key of the crate. */
    private static final Map<String, DeferredItem<BlockItem>> CRATES = new LinkedHashMap<>();

    static {
        // Every crate of the game is an item of the mod, so that it can be placed and so that
        // it is in the tab of the creative mode of the mod.
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            CRATES.put(definition.key(), ITEMS.registerSimpleBlockItem(definition.key(),
                    () -> RacMcBlocks.crate(definition).get()));
        }
    }

    private RacMcItems() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The items of the crates of the game, by the key of the crate.
     *
     * @return the items of the crates, in the order of the table of the crates
     */
    public static Map<String, DeferredItem<BlockItem>> crates() {
        return Collections.unmodifiableMap(CRATES);
    }

    /**
     * Registers the items of the mod.
     *
     * @param modEventBus the event bus of the mod
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
