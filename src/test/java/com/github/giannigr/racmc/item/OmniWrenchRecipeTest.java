package com.github.giannigr.racmc.item;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OmniWrenchRecipeTest {
    @Test
    void packagedRecipeCraftsOneExistingWrenchFromTheExactGrid() throws IOException {
        try (var data = getClass().getResourceAsStream("/data/racmc/recipe/omniwrench.json")) {
            assertNotNull(data, "Minecraft 1.21.1 recipe resource");
            var json = JsonParser.parseReader(new InputStreamReader(data, StandardCharsets.UTF_8));
            var recipe = assertInstanceOf(ShapedRecipe.class,
                    Recipe.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
            var block = new ItemStack(Items.IRON_BLOCK);
            var ingot = new ItemStack(Items.IRON_INGOT);
            var empty = ItemStack.EMPTY;
            var grid = List.of(block, empty, block, empty, ingot, empty, empty, ingot, empty);
            var input = CraftingInput.of(3, 3, grid);

            assertTrue(recipe.matches(input, null));
            var output = recipe.assemble(input, RegistryAccess.EMPTY);
            assertSame(RacMcItems.OMNIWRENCH.get(), output.getItem());
            assertEquals(1, output.getCount());
            assertFalse(recipe.canCraftInDimensions(2, 3));
            assertFalse(recipe.canCraftInDimensions(3, 2));

            for (int slot = 0; slot < grid.size(); slot++) {
                var altered = new ArrayList<>(grid);
                var original = grid.get(slot);
                altered.set(slot, original.is(Items.IRON_BLOCK) ? ingot : block);
                assertFalse(recipe.matches(CraftingInput.of(3, 3, altered), null),
                        "Wrong iron item or non-empty space at slot " + slot);
                if (!original.isEmpty()) {
                    altered.set(slot, empty);
                    assertFalse(recipe.matches(CraftingInput.of(3, 3, altered), null),
                            "Missing ingredient at slot " + slot);
                }
            }
        }
    }
}
