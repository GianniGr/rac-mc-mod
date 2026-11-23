package com.github.giannigr.racmc.worldgen;

import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Cell;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Slot;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One bounded packing pass in a start chunk, with access reserved for all starts in that chunk. */
final class StructureCratePacking {
    static final int MAX_CANDIDATES = 192;
    static final int SMALLEST_BONUS = 6; // Never turn the big roll into another normal group.

    private final Terrain terrain;
    private final Map<Cell, List<Slot>> approaches = new HashMap<>();
    private final Set<Cell> occupied = new HashSet<>();
    private final Set<Cell> clearance = new HashSet<>();
    private final Set<Cell> groupBuffer = new HashSet<>();

    StructureCratePacking(Terrain terrain, List<Slot> vendors) {
        this.terrain = terrain;
        for (Slot vendor : vendors) reserve(vendor);
    }

    /**
     * Try compact two-wide formations with an aisle on either side, or a short row for
     * ordinary groups. Each crate must have an access route outside the entire formation.
     * Smaller patterns are tried only after exhausting the requested size.
     */
    List<Slot> addGroup(List<Slot> ranked, int target, boolean bonus, boolean allowSingle) {
        var free = new ArrayList<Slot>();
        for (Slot base : ranked) {
            Cell pos = base.pos();
            if (occupied.contains(pos) || clearance.contains(pos) || groupBuffer.contains(pos)) continue;
            Slot available = availableApproach(pos, Set.of());
            if (available != null) {
                free.add(available);
                if (free.size() == MAX_CANDIDATES) break;
            }
        }
        if (free.isEmpty()) return List.of();

        var cells = new HashSet<Cell>();
        for (Slot slot : free) cells.add(slot.pos());
        if (bonus) {
            // Fast rejection in small rooms; the extra draw does not prevent normal placements.
            for (int size = Math.min(target, free.size()); size >= SMALLEST_BONUS; size--) {
                List<Slot> group = formation(free, cells, size, 2);
                if (!group.isEmpty()) return accept(group);
            }
            return List.of();
        }

        for (int size = Math.min(target, free.size()); size >= 2; size--) {
            List<Slot> group = formation(free, cells, size, 2);
            if (group.isEmpty()) group = formation(free, cells, size, 1);
            if (!group.isEmpty()) return accept(group);
            // Uneven terrain may have no flat footprint at this size; try a stepped group.
            group = StructureSupplyPlan.irregularGroup(free, size);
            if (group.size() == size) return accept(group);
        }
        return allowSingle ? accept(List.of(free.getFirst())) : List.of();
    }

    private List<Slot> formation(List<Slot> ranked, Set<Cell> cells, int size, int width) {
        for (Slot root : ranked) {
            for (int axis = 0; axis < 2; axis++) {
                for (int along = 1; along >= -1; along -= 2) {
                    for (int across = 1; across >= (width == 1 ? 1 : -1); across -= 2) {
                        List<Slot> group = footprint(root.pos(), cells, size, width, axis != 0, along, across);
                        if (!group.isEmpty()) return group;
                    }
                }
            }
        }
        return List.of();
    }

    private List<Slot> footprint(Cell origin, Set<Cell> available, int size, int width,
            boolean transpose, int along, int across) {
        // Check the map before allocating lists or testing clearance.
        for (int i = 0; i < size; i++) {
            if (!available.contains(offset(origin, i, width, transpose, along, across))) return List.of();
        }
        var positions = new ArrayList<Cell>(size);
        for (int i = 0; i < size; i++) positions.add(offset(origin, i, width, transpose, along, across));
        var targets = new HashSet<>(positions);
        var group = new ArrayList<Slot>(size);
        for (Cell pos : positions) {
            Slot approach = availableApproach(pos, targets);
            if (approach == null) return List.of();
            group.add(approach);
        }
        return group;
    }

    private static Cell offset(Cell origin, int index, int width, boolean transpose, int along, int across) {
        int a = (index / width) * along;
        int b = (index % width) * across;
        return transpose ? origin.offset(b, 0, a) : origin.offset(a, 0, b);
    }

    private Slot availableApproach(Cell pos, Set<Cell> targets) {
        for (Slot choice : approaches.computeIfAbsent(pos, p -> StructureSupplyPlan.approaches(terrain, p, true))) {
            boolean usable = true;
            for (Cell access : choice.clearance()) {
                if (occupied.contains(access) || targets.contains(access)) {
                    usable = false;
                    break;
                }
            }
            if (usable) return choice;
        }
        return null;
    }

    private List<Slot> accept(List<Slot> group) {
        for (Slot slot : group) {
            reserve(slot);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        groupBuffer.add(slot.pos().offset(dx, dy, dz));
                    }
                }
            }
        }
        return List.copyOf(group);
    }

    private void reserve(Slot slot) {
        occupied.add(slot.pos());
        clearance.addAll(slot.clearance());
    }
}
