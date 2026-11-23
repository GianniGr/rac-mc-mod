package com.github.giannigr.racmc.worldgen;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Cell;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Crate;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Slot;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Space;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureSupplyPlanTest {
    @Test
    void allCratesCanMixAndBoltsHaveTheStrongestWeight() {
        var distribution = new EnumMap<Crate, Integer>(Crate.class);
        for (int i = 0; i < 100; i++) distribution.merge(StructureSupplyPlan.crate(i), 1, Integer::sum);
        assertEquals(60, distribution.get(Crate.BOLTS));
        assertEquals(15, distribution.get(Crate.HEALTH));
        assertEquals(15, distribution.get(Crate.AMMO));
        assertEquals(10, distribution.get(Crate.EXPLOSIVE));
        for (Crate crate : Crate.values()) assertTrue(Rac1Crates.byKey(crate.key).isPresent());

        int mixed = 0;
        int bonus = 0;
        int vendors = 0;
        int vendorAndBonus = 0;
        for (long seed = 0; seed < 4096; seed++) {
            var normal = StructureSupplyPlan.draw(seed, "minecraft:shipwreck", 0, -1, false);
            var forced = StructureSupplyPlan.draw(seed, "minecraft:shipwreck", 0, -1, true);
            assertTrue(forced.vendor(), "Villages must not roll for the vendor");
            assertEquals(normal.groups(), forced.groups(), "Vendor decisions must not alter crate draws");
            assertEquals(normal.bonus(), forced.bonus());
            if (normal.vendor()) vendors++;
            assertTrue(normal.groups().size() >= 3 && normal.groups().size() <= 5);
            for (var group : normal.groups()) {
                assertTrue(group.size() >= 3 && group.size() <= 5);
                if (group.stream().distinct().count() > 1) mixed++;
            }
            if (!normal.bonus().isEmpty()) {
                bonus++;
                if (normal.vendor()) vendorAndBonus++;
                assertTrue(normal.bonus().size() >= 12 && normal.bonus().size() <= 16);
                assertTrue(normal.bonus().stream().filter(c -> c == Crate.EXPLOSIVE).count() <= 3);
            }
        }
        assertTrue(vendors > 500 && vendors < 750, "Unexpected pinned 15% vendor sample");
        assertTrue(bonus > 730 && bonus < 910, "Unexpected pinned 20% bonus sample");
        assertTrue(vendorAndBonus > 85 && vendorAndBonus < 170, "Bonus draw should not follow the vendor draw");
        assertTrue(mixed > 10000);
    }

    @Test
    void drawsAreIndependentOfChunkArrivalAndOtherStartsInTheSameChunk() {
        record Instance(String id, int x, int z) {}
        var instances = List.of(new Instance("minecraft:village_plains", -1, 7),
                new Instance("minecraft:mineshaft", 0, 7),
                new Instance("minecraft:buried_treasure", 0, 7),
                new Instance("minecraft:shipwreck", 1, 7));
        var draws = new HashMap<Instance, StructureSupplyPlan.Draw>();
        for (var i : instances) draws.put(i, StructureSupplyPlan.draw(228, i.id, i.x, i.z, i.id.contains("village")));
        for (var i : instances.reversed()) {
            assertEquals(draws.get(i), StructureSupplyPlan.draw(228, i.id, i.x, i.z, i.id.contains("village")));
        }
        assertEquals(4, draws.size());
        assertTrue(java.util.stream.LongStream.range(0, 40).anyMatch(seed ->
                !StructureSupplyPlan.draw(seed, "minecraft:mineshaft", 0, 7, false).equals(
                        StructureSupplyPlan.draw(seed, "minecraft:buried_treasure", 0, 7, false))));
    }

    @Test
    void floorHeadroomBypassAndLocalityRuleOutUnsafeSites() {
        Cell pos = new Cell(7, 64, 7);
        assertNotNull(StructureSupplyPlan.slot(new Floor(Space.AIR), pos, false));
        assertNull(StructureSupplyPlan.slot(new Floor(Space.AIR), new Cell(1, 64, 7), false));
        assertNull(StructureSupplyPlan.slot(new Floor(Space.AIR), new Cell(7, 65, 7), false));
        assertNull(StructureSupplyPlan.slot(new Floor(Space.BLOCKED), pos, false));
        assertNull(StructureSupplyPlan.slot(new Floor(Space.WATER), pos, false));
        assertNotNull(StructureSupplyPlan.slot(new Floor(Space.WATER), pos, true));

        var protectedTerrain = new Floor(Space.AIR) {
            @Override public boolean protectedSite(Cell p) { return p.equals(pos); }
        };
        assertNull(StructureSupplyPlan.slot(protectedTerrain, pos, false));
        var narrow = new Floor(Space.AIR) {
            @Override public Space space(Cell p) { return p.x() == 7 ? super.space(p) : Space.BLOCKED; }
        };
        assertNull(StructureSupplyPlan.slot(narrow, pos, false));
    }

    @Test
    void openDryAndSubmergedFloorsFitDistinctNormalGroupsAndFullBonus() {
        for (Space medium : List.of(Space.AIR, Space.WATER)) {
            var floor = new Floor(medium);
            var vendor = StructureSupplyPlan.slot(floor, new Cell(7, 64, 7), true);
            var candidates = candidates(floor);
            var packing = new StructureCratePacking(floor, List.of(vendor));
            var groups = new ArrayList<List<Slot>>();
            groups.add(packing.addGroup(candidates, 5, false, true));
            var bonus = packing.addGroup(candidates, 16, true, false);
            groups.add(bonus);
            for (int i = 0; i < 4; i++) {
                groups.add(packing.addGroup(candidates, 5, false, false));
            }
            assertEquals(16, bonus.size(), "Open " + medium + " floor should support the complete bonus");
            assertEquals(List.of(5, 16, 5, 5, 5, 5), groups.stream().map(List::size).toList());
            for (var group : groups) {
                for (var member : group) {
                    assertTrue(member.fitsWith(vendor));
                    for (var other : group) if (other != member) assertTrue(member.fitsWith(other));
                    for (var otherGroup : groups) {
                        if (otherGroup == group) continue;
                        for (var other : otherGroup) {
                            assertTrue(member.fitsWith(other));
                            assertTrue(Math.max(Math.abs(member.pos().x() - other.pos().x()),
                                    Math.abs(member.pos().z() - other.pos().z())) > 1);
                        }
                    }
                }
            }
        }
    }

    @Test
    void crampedSitesFallBackWithoutConvertingFailedBonusToNormalGroups() {
        var tiny = new Floor(Space.AIR) {
            @Override public boolean protectedSite(Cell p) { return p.x() != 6 || p.z() != 6; }
        };
        var tinySites = candidates(tiny);
        var tinyPacking = new StructureCratePacking(tiny, List.of());
        assertTrue(tinyPacking.addGroup(tinySites, 16, true, false).isEmpty());
        assertEquals(1, tinyPacking.addGroup(tinySites, 5, false, true).size());
        assertTrue(tinyPacking.addGroup(tinySites, 5, false, true).isEmpty());
        var modest = new Floor(Space.AIR) {
            @Override public boolean protectedSite(Cell p) {
                return p.x() < 5 || p.x() > 6 || p.z() < 5 || p.z() > 8;
            }
        };
        assertEquals(8, new StructureCratePacking(modest, List.of())
                .addGroup(candidates(modest), 16, true, false).size());
        var impossible = new Floor(Space.BLOCKED);
        assertTrue(new StructureCratePacking(impossible, List.of())
                .addGroup(candidates(impossible), 5, false, true).isEmpty());
    }

    private static List<Slot> candidates(Floor floor) {
        var result = new ArrayList<Slot>();
        for (int x = 2; x < 14; x++) {
            for (int z = 2; z < 14; z++) {
                var slot = StructureSupplyPlan.slot(floor, new Cell(x, 64, z), true);
                if (slot != null) result.add(slot);
            }
        }
        result.sort(Comparator.comparingInt(slot ->
                Math.abs(slot.pos().x() - 7) + Math.abs(slot.pos().z() - 7)));
        return result;
    }

    private static class Floor implements StructureSupplyPlan.Terrain {
        private final Space open;

        Floor(Space open) { this.open = open; }

        @Override public Space space(Cell pos) {
            assertTrue(pos.inside(0), "Terrain request escaped the chunk");
            return pos.y() >= 64 ? open : Space.BLOCKED;
        }

        @Override public boolean supports(Cell pos) {
            assertTrue(pos.inside(0), "Support request escaped the chunk");
            return pos.y() == 63;
        }

        @Override public boolean protectedSite(Cell pos) { return false; }
    }
}
