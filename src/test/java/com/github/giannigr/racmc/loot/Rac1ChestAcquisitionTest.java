package com.github.giannigr.racmc.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Rac1ChestAcquisitionTest {
    @BeforeAll
    static void startVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void indexAppendsExactlyOneModifierPerReward() throws IOException {
        assertSame(Rac1ChestAcquisitionModifier.CODEC, NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS
                .get(ResourceLocation.parse("racmc:chest_acquisition")));
        JsonObject index = resource("neoforge/loot_modifiers/global_loot_modifiers");
        assertFalse(index.get("replace").getAsBoolean(), "Do not replace any other mod's loot");
        var entries = new HashSet<String>();
        index.getAsJsonArray("entries").forEach(value -> assertTrue(entries.add(value.getAsString())));
        assertEquals(Set.of("racmc:chest_bolt_grabber", "racmc:chest_pda"), entries);
    }

    @Test
    void modifiersDecodeWithExactDisjointTableIdsAndOneFivePercentRoll() throws IOException {
        var boltTables = tablesFor("bolt_grabber");
        var pdaTables = tablesFor("pda");
        assertEquals(Set.of("minecraft:chests/underwater_ruin_big", "minecraft:chests/underwater_ruin_small",
                "minecraft:chests/shipwreck_treasure"), boltTables);
        assertEquals(Set.of("minecraft:chests/village/village_cartographer",
                "minecraft:chests/stronghold_library"), pdaTables);
        assertTrue(java.util.Collections.disjoint(boltTables, pdaTables));
    }

    @Test
    void onlyOneMatchingStackIsAppendedAndComponentsStayIntact() {
        // Vanilla fixture items exercise validation only; neither is a production reward or binding.
        var vanilla = new ItemStack(Items.BOOK, 3);
        var loot = new ObjectArrayList<ItemStack>();
        loot.add(vanilla);
        var id = ResourceLocation.withDefaultNamespace("stick");
        var award = new ItemStack(Items.STICK);
        award.set(DataComponents.CUSTOM_NAME, Component.literal("fixture component"));

        assertFalse(Rac1ChestAcquisition.appendOne(loot, id, null));
        assertFalse(Rac1ChestAcquisition.appendOne(loot, id, ItemStack.EMPTY));
        assertFalse(Rac1ChestAcquisition.appendOne(loot, id, new ItemStack(Items.BOOK)));
        assertFalse(Rac1ChestAcquisition.appendOne(loot, id, new ItemStack(Items.STICK, 2)));
        assertEquals(1, loot.size());

        assertTrue(Rac1ChestAcquisition.appendOne(loot, id, award));
        assertEquals(2, loot.size());
        assertSame(vanilla, loot.getFirst());
        assertEquals(3, vanilla.getCount());
        assertSame(award, loot.getLast());
        assertEquals("fixture component", loot.getLast().get(DataComponents.CUSTOM_NAME).getString());
    }

    private static Set<String> tablesFor(String reward) throws IOException {
        var modifier = resource("racmc/loot_modifiers/chest_" + reward);
        assertEquals("racmc:chest_acquisition", modifier.get("type").getAsString());
        assertEquals(reward, modifier.get("reward").getAsString());
        assertTrue(Rac1ChestAcquisitionModifier.CODEC.codec().parse(JsonOps.INSTANCE, modifier).isSuccess(),
                "Data must decode with the real NeoForge and vanilla condition codecs");
        var conditions = modifier.getAsJsonArray("conditions");
        assertEquals(2, conditions.size(), "Table gate followed by a single roll");
        var gate = conditions.get(0).getAsJsonObject();
        assertEquals("minecraft:any_of", gate.get("condition").getAsString());
        var tables = new HashSet<String>();
        for (var term : gate.getAsJsonArray("terms")) {
            var exact = term.getAsJsonObject();
            assertEquals("neoforge:loot_table_id", exact.get("condition").getAsString());
            assertTrue(tables.add(exact.get("loot_table_id").getAsString()), "Duplicate exact table ID");
        }
        var roll = conditions.get(1).getAsJsonObject();
        assertEquals("minecraft:random_chance", roll.get("condition").getAsString());
        assertEquals(0.05, roll.get("chance").getAsDouble());
        return tables;
    }

    private static JsonObject resource(String relative) throws IOException {
        Path path = Path.of(System.getProperty("racmc.projectDir"), "src/main/resources/data", relative + ".json");
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
