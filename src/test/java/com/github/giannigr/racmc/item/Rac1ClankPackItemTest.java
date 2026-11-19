package com.github.giannigr.racmc.item;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Rac1ClankPackItemTest {
    @Test void hydroHasNoRegisteredItemCreativeFixtureOrRecipe() throws Exception {
        assertFalse(net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
                .anyMatch(id->id.getNamespace().equals("racmc")&&id.getPath().contains("hydro")));
        var root=Path.of(System.getProperty("racmc.projectDir"),"src/main/resources");
        try(var files=Files.walk(root)) {
            assertFalse(files.anyMatch(path->path.getFileName().toString().contains("hydro")),
                    "No Hydro item/recipe/model entry may ship");
        }
    }
    @Test void registeredItemsHaveImmutableDistinctModulesAndNoEquipmentSlotOrDurability() {
        var heli=RacMcItems.CLANK_HELI_PACK.get();var thruster=RacMcItems.CLANK_THRUSTER_PACK.get();
        assertEquals(Module.HELI,heli.module());assertEquals(Module.THRUSTER,thruster.module());
        for(var item:new net.minecraft.world.item.Item[]{heli,thruster}) {
            assertEquals(1,item.getDefaultInstance().getMaxStackSize());
            assertFalse(item.getDefaultInstance().isDamageableItem());
            assertFalse(item instanceof net.minecraft.world.item.ArmorItem);
        }
    }
    @Test void entriesHaveDistinctNamesAndClientLocalEntityModelsWithoutBundledTextures()throws Exception {
        var root=Path.of(System.getProperty("racmc.projectDir"),"src/main/resources/assets/racmc");
        var lang=JsonParser.parseString(Files.readString(root.resolve("lang/en_us.json"))).getAsJsonObject();
        assertEquals("Clank Heli-Pack",lang.get("item.racmc.clank_heli_pack").getAsString());
        assertEquals("Clank Thruster-Pack",lang.get("item.racmc.clank_thruster_pack").getAsString());
        var heli=JsonParser.parseString(Files.readString(root.resolve("models/item/clank_heli_pack.json"))).getAsJsonObject();
        var thruster=JsonParser.parseString(Files.readString(root.resolve("models/item/clank_thruster_pack.json"))).getAsJsonObject();
        assertEquals("builtin/entity",heli.get("parent").getAsString());
        assertEquals("racmc:item/clank_heli_pack",thruster.get("parent").getAsString());
        assertFalse(heli.has("textures"));assertFalse(thruster.has("textures"));
        assertTrue(lang.has("item.racmc.clank_pack.creative_hint"));
    }
}
