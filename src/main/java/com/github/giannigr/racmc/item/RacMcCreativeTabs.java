package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The tab of the creative mode of the mod.
 *
 * <p>The items of the mod have a tab of their own, so that they are easy to find in the lists of
 * the creative mode. The tab is named after the mod and shows the Blaster as its sign: the sign is
 * the Blaster itself, drawn by the renderer of the Blaster, so that no new picture is needed.
 *
 * <p>The tab holds the items of the mod. The Blaster is the first of them; every item that the
 * mod gets later is added to the same tab.
 *
 * <p>The registration of the tab holds no types of the client, so that a dedicated server can
 * start with it.
 */
public final class RacMcCreativeTabs {

    /** The key of the name of the tab in the language file. */
    public static final String TITLE_KEY = "itemGroup.racmc";

    /** The name of the tab in the registry of the game. */
    public static final String TAB_NAME = "racmc";

    /** The tabs of the creative mode of the mod, registered under the identifier of the mod. */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RacMcConstants.MOD_ID);

    /**
     * The tab of the mod.
     *
     * <p>The sign of the tab is the picture of the mod. The picture is drawn as an item, because
     * the game draws the sign of a tab as an item; the item is a sign and nothing else.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RACMC =
            TABS.register(TAB_NAME, () -> CreativeModeTab.builder()
                    .title(Component.translatable(TITLE_KEY))
                    .icon(() -> new ItemStack(RacMcItems.TAB_ICON.get()))
                    .displayItems((parameters, output) -> output.accept(RacMcItems.BLASTER.get()))
                    .build());

    private RacMcCreativeTabs() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers the tabs of the creative mode of the mod.
     *
     * @param modEventBus the event bus of the mod
     */
    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
