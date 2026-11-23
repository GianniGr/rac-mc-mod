package com.github.giannigr.racmc.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/** The reproducible, Minecraft-independent part of natural structure supplies. */
final class StructureSupplyPlan {
    static final int VENDOR_PERCENT = 15;
    static final int MIN_GROUPS = 3;
    static final int MAX_GROUPS = 5;
    static final int MIN_CRATES = 3;
    static final int MAX_CRATES = 5;
    static final int BONUS_PERCENT = 20;
    static final int MIN_BONUS = 12;
    static final int MAX_BONUS = 16;
    static final int MAX_BONUS_EXPLOSIVES = 3;

    enum Crate {
        BOLTS("bolt_crate"), HEALTH("health_crate"), AMMO("ammo_crate"),
        EXPLOSIVE("explosive_crate");

        final String key;

        Crate(String key) {
            this.key = key;
        }
    }

    record Draw(boolean vendor, List<List<Crate>> groups, List<Crate> bonus) {}

    static Draw draw(long seed, String structure, int chunkX, int chunkZ, boolean village) {
        long key = 0xcbf29ce484222325L;
        for (int i = 0; i < structure.length(); i++) {
            key = (key ^ structure.charAt(i)) * 0x100000001b3L;
        }
        key ^= mix(seed);
        key ^= mix((long) chunkX << 32 | Integer.toUnsignedLong(chunkZ));
        var vendor = new SplittableRandom(mix(key ^ 0x76656e646f725f31L));
        var supplies = new SplittableRandom(mix(key ^ 0x6372617465735f31L));
        var bonus = new SplittableRandom(mix(key ^ 0x626f6e75735f3031L));
        boolean placeVendor = village || vendor.nextInt(100) < VENDOR_PERCENT;
        var groups = new ArrayList<List<Crate>>();
        int count = supplies.nextInt(MIN_GROUPS, MAX_GROUPS + 1);
        for (int i = 0; i < count; i++) {
            groups.add(crates(supplies, supplies.nextInt(MIN_CRATES, MAX_CRATES + 1), false));
        }
        List<Crate> extra = bonus.nextInt(100) < BONUS_PERCENT
                ? crates(bonus, bonus.nextInt(MIN_BONUS, MAX_BONUS + 1), true) : List.of();
        return new Draw(placeVendor, List.copyOf(groups), extra);
    }

    private static List<Crate> crates(SplittableRandom random, int count, boolean bonus) {
        List<Crate> crates = new ArrayList<>(count);
        int explosives = 0;
        for (int i = 0; i < count; i++) {
            Crate type = crate(random.nextInt(100));
            if (type == Crate.EXPLOSIVE && bonus && ++explosives > MAX_BONUS_EXPLOSIVES) {
                type = crate(random.nextInt(90)); // Preserve the relative weights of the safe replacements.
            }
            crates.add(type);
        }
        return List.copyOf(crates);
    }

    static Crate crate(int roll) {
        if (roll < 60) return Crate.BOLTS;
        if (roll < 75) return Crate.HEALTH;
        if (roll < 90) return Crate.AMMO;
        return Crate.EXPLOSIVE;
    }

    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    enum Space { BLOCKED, AIR, WATER }

    record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }

        boolean inside(int margin) {
            return x >= margin && z >= margin && x < 16 - margin && z < 16 - margin;
        }
    }

    interface Terrain {
        Space space(Cell pos);
        boolean supports(Cell pos);
        boolean protectedSite(Cell pos);
    }

    /** Keep the standing/swimming side, its bypass, and target headroom open. */
    record Slot(Cell pos, Set<Cell> clearance) {
        boolean fitsWith(Slot other) {
            return !pos.equals(other.pos) && !clearance.contains(other.pos)
                    && !other.clearance.contains(pos);
        }
    }

    static final int SITE_MARGIN = 2;
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    static Slot slot(Terrain terrain, Cell pos, boolean allowWater) {
        var found = approaches(terrain, pos, allowWater, true);
        return found.isEmpty() ? null : found.getFirst();
    }

    static List<Slot> approaches(Terrain terrain, Cell pos, boolean allowWater) {
        return approaches(terrain, pos, allowWater, false);
    }

    private static List<Slot> approaches(Terrain terrain, Cell pos, boolean allowWater, boolean firstOnly) {
        if (!pos.inside(SITE_MARGIN)) return List.of();
        Space space = terrain.space(pos);
        if (space == Space.BLOCKED || (space == Space.WATER && !allowWater)
                || !terrain.supports(pos.offset(0, -1, 0))
                || terrain.space(pos.offset(0, 1, 0)) != space || terrain.protectedSite(pos)) {
            return List.of();
        }
        var found = new ArrayList<Slot>();
        for (int[] dir : SIDES) {
            int dx = dir[0];
            int dz = dir[1];
            // The two-wide bypass gives a way past the new block, not just a single free cell.
            Cell stand = pos.offset(dx, 0, dz);
            Cell out = pos.offset(2 * dx, 0, 2 * dz);
            if (!approachable(terrain, stand, space) || !approachable(terrain, out, space)) continue;
            for (int side = 1; side >= -1; side -= 2) {
                Cell around = stand.offset(side * dz, 0, -side * dx);
                if (!approachable(terrain, around, space)) continue;
                found.add(new Slot(pos, Set.of(pos.offset(0, 1, 0),
                        stand, stand.offset(0, 1, 0), out, out.offset(0, 1, 0),
                        around, around.offset(0, 1, 0))));
                if (firstOnly) return found;
            }
        }
        return found;
    }

    private static boolean approachable(Terrain terrain, Cell pos, Space space) {
        return pos.inside(0) && terrain.space(pos) == space
                && terrain.space(pos.offset(0, 1, 0)) == space
                && (space == Space.WATER || terrain.supports(pos.offset(0, -1, 0)));
    }

    static List<Slot> irregularGroup(List<Slot> free, int count) {
        var rank = new HashMap<Cell, Integer>();
        for (int i = 0; i < free.size(); i++) rank.put(free.get(i).pos, i);
        List<Slot> fallback = List.of();
        for (Slot root : free) {
            Set<Cell> touching = new HashSet<>();
            List<Slot> group = new ArrayList<>(count);
            add(group, touching, root);
            while (group.size() < count) {
                int best = free.size();
                for (Cell cell : touching) {
                    if (Math.abs(cell.x - root.pos.x) > 2 || Math.abs(cell.z - root.pos.z) > 2) continue;
                    Integer index = rank.get(cell);
                    if (index != null && index < best && fits(free.get(index), group)) best = index;
                }
                if (best == free.size()) break;
                add(group, touching, free.get(best));
            }
            if (group.size() == count) return List.copyOf(group);
            if (group.size() >= 2 && group.size() > fallback.size()) {
                fallback = List.copyOf(group);
            }
        }
        return fallback;
    }

    static boolean fits(Slot slot, List<Slot> reserved) {
        return reserved.stream().allMatch(slot::fitsWith);
    }

    private static void add(List<Slot> group, Set<Cell> touching, Slot slot) {
        group.add(slot);
        for (int[] dir : SIDES) {
            for (int dy = -1; dy <= 1; dy++) {
                touching.add(slot.pos.offset(dir[0], dy, dir[1]));
            }
        }
    }
}
