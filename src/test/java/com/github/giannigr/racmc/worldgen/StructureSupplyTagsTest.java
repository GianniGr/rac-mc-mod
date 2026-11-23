package com.github.giannigr.racmc.worldgen;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureSupplyTagsTest {
    @Test
    void includesEveryVanillaOverworldStructureAndOnlyThoseDefaults() throws IOException {
        var villages = entries("structure_supplies_villages");
        assertEquals(Set.of("minecraft:village_desert", "minecraft:village_plains",
                "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga"), villages);
        var eligible = entries("structure_supplies");
        assertTrue(eligible.remove("#racmc:structure_supplies_villages"));
        assertTrue(eligible.addAll(villages));
        assertEquals("""
                ancient_city buried_treasure desert_pyramid igloo jungle_pyramid mansion
                mineshaft mineshaft_mesa monument ocean_ruin_cold ocean_ruin_warm
                pillager_outpost ruined_portal ruined_portal_desert ruined_portal_jungle
                ruined_portal_mountain ruined_portal_ocean ruined_portal_swamp shipwreck
                shipwreck_beached stronghold swamp_hut trail_ruins trial_chambers
                village_desert village_plains village_savanna village_snowy village_taiga
                """.strip().transform(pattern -> Set.of(pattern.split("\\s+"))),
                eligible.stream().map(id -> {
                    assertTrue(id.startsWith("minecraft:"));
                    return id.substring("minecraft:".length());
                }).collect(java.util.stream.Collectors.toSet()));
        assertTrue(eligible.containsAll(entries("structure_supplies_aquatic")));
        assertTrue(eligible.containsAll(entries("structure_supplies_near_surface")));
    }

    private static Set<String> entries(String name) throws IOException {
        Path file = Path.of(System.getProperty("racmc.projectDir"), "src/main/resources",
                "data/racmc/tags/worldgen/structure", name + ".json");
        var result = new HashSet<String>();
        try (var reader = Files.newBufferedReader(file)) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values").forEach(value ->
                    assertTrue(result.add(value.getAsString()), "Duplicate structure tag entry"));
        }
        return result;
    }
}
