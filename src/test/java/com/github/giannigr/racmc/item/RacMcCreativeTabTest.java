package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.RacMcBlocks;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the tab of the creative mode of the mod.
 *
 * <p>The tests run with the mod loaded, so that the registry of the game holds the tab of the mod.
 */
class RacMcCreativeTabTest {

    /**
     * The access to the holders of the game.
     *
     * <p>The tab of the mod shows its items by their own name, so that it does not need any
     * holder. The tab of the combat of the game does the same.
     */
    private static final HolderLookup.Provider NO_HOLDERS = new HolderLookup.Provider() {

        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
            return Stream.empty();
        }

        @Override
        public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(
                ResourceKey<? extends Registry<? extends T>> registryKey) {
            return Optional.empty();
        }
    };

    /** The tab of the mod. */
    private static CreativeModeTab tab;

    /** The tab of the combat of the game. */
    private static CreativeModeTab combat;

    @BeforeAll
    static void findTabs() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                com.github.giannigr.racmc.RacMcConstants.MOD_ID, RacMcCreativeTabs.TAB_NAME);
        tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        combat = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.COMBAT);
        // The game fills the tabs of the creative mode when the list of the creative mode is
        // opened. The test does the same, so that the contents of the tab can be looked at.
        CreativeModeTab.ItemDisplayParameters parameters =
                new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, NO_HOLDERS);
        tab.buildContents(parameters);
        combat.buildContents(parameters);
    }

    @Test
    @DisplayName("The mod has a tab of the creative mode of its own")
    void tabIsRegistered() {
        assertNotNull(tab, "The mod must have a tab of the creative mode of its own");
    }

    @Test
    @DisplayName("The tab is named after the mod")
    void tabIsNamedAfterTheMod() {
        assertNotNull(tab);
        Component name = tab.getDisplayName();
        assertNotNull(name, "The tab of the mod must have a name");
        assertTrue(name.getContents() instanceof TranslatableContents,
                "The name of the tab must be a name of the language file");
        assertEquals(RacMcCreativeTabs.TITLE_KEY,
                ((TranslatableContents) name.getContents()).getKey());
    }

    @Test
    @DisplayName("The sign of the tab is the picture of the mod")
    void signOfTheTabIsThePictureOfTheMod() {
        assertNotNull(tab);
        ItemStack icon = tab.getIconItem();
        assertNotNull(icon, "The tab of the mod must have a sign");
        assertFalse(icon.isEmpty(), "The sign of the tab must not be empty");
        assertTrue(icon.is(RacMcItems.TAB_ICON.get()),
                "The sign of the tab must be the picture of the mod");
    }

    @Test
    @DisplayName("The tab of the mod holds the Blaster")
    void tabHoldsTheBlaster() {
        assertNotNull(tab);
        Collection<ItemStack> items = tab.getDisplayItems();
        assertNotNull(items);
        assertFalse(items.isEmpty(), "The tab of the mod must hold something");
        assertTrue(items.stream().anyMatch(stack -> stack.is(RacMcItems.BLASTER.get())),
                "The tab of the mod must hold the Blaster");
    }

    @Test
    @DisplayName("The Blaster is in the tab of the mod and in no other tab of the game")
    void blasterIsOnlyInTheTabOfTheMod() {
        assertNotNull(combat);
        assertTrue(combat.getDisplayItems().stream()
                .noneMatch(stack -> stack.is(RacMcItems.BLASTER.get())),
                "The Blaster must not be in the tab of the combat of the game");
    }

    @Test
    @DisplayName("The tab of the mod holds the crates of the game")
    void tabHoldsTheCrates() {
        assertNotNull(tab);
        Collection<ItemStack> items = tab.getDisplayItems();
        assertNotNull(items);
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            Block block = RacMcBlocks.crate(definition).get();
            assertTrue(items.stream().anyMatch(stack -> stack.is(block.asItem())),
                    "The tab of the mod must hold the crate " + definition.key());
        }
    }

    @Test
    @DisplayName("Every crate of the game is a block of the mod with an item of its own")
    void everyCrateIsABlockWithAnItem() {
        for (Rac1CrateDefinition definition : Rac1Crates.definitions()) {
            Block block = RacMcBlocks.crate(definition).get();
            assertEquals(definition, ((Rac1CrateBlock) block).definition(),
                    "The block has to know the crate it shows");
            assertFalse(block.asItem() == net.minecraft.world.item.Items.AIR,
                    "The crate " + definition.key() + " needs an item to be placed with");
        }
    }
}
