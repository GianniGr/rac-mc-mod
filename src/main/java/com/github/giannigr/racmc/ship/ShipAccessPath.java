package com.github.giannigr.racmc.ship;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Server-owned ship-local feet path; the renderer samples it instead of inventing a diagonal. */
public record ShipAccessPath(List<Vec3> waypoints, long startTick, int serial, boolean rollback) {
    public static final int TICKS_PER_LEG = 8;

    public ShipAccessPath {
        waypoints = List.copyOf(waypoints);
        if (waypoints.size() < 2 || waypoints.size() > 4
                || waypoints.stream().anyMatch(p -> !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z))) {
            throw new IllegalArgumentException("Invalid ship access path");
        }
    }

    public int durationTicks() { return (waypoints.size() - 1) * TICKS_PER_LEG; }

    public Vec3 sample(double elapsedTicks) {
        if (elapsedTicks <= 0) return waypoints.getFirst();
        if (elapsedTicks >= durationTicks()) return waypoints.getLast();
        double t = Math.clamp(elapsedTicks / TICKS_PER_LEG, 0, waypoints.size() - 1);
        int leg = Math.min((int)t, waypoints.size() - 2);
        return waypoints.get(leg).lerp(waypoints.get(leg + 1), t - leg);
    }

    List<Vec3> reversePrefix(int elapsedTicks) {
        var result = new ArrayList<Vec3>();
        result.add(sample(elapsedTicks));
        int last = Math.min(elapsedTicks / TICKS_PER_LEG, waypoints.size() - 1);
        for (int i = last; i >= 0; i--) {
            if (!result.getLast().equals(waypoints.get(i))) result.add(waypoints.get(i));
        }
        if (result.size() == 1) result.add(result.getFirst());
        return List.copyOf(result);
    }

    CompoundTag encode() {
        var tag = new CompoundTag();
        tag.putInt("Count", waypoints.size());
        tag.putBoolean("Rollback", rollback);
        tag.putLong("Start", startTick);
        tag.putInt("Serial", serial);
        for (int i = 0; i < waypoints.size(); i++) {
            Vec3 p = waypoints.get(i);
            tag.putDouble("X" + i, p.x);
            tag.putDouble("Y" + i, p.y);
            tag.putDouble("Z" + i, p.z);
        }
        return tag;
    }

    static ShipAccessPath decode(CompoundTag tag) {
        int size = tag.getInt("Count");
        if (size < 2 || size > 4) return null;
        var points = new ArrayList<Vec3>(size);
        for (int i = 0; i < size; i++) {
            if (!tag.contains("X" + i, 6) || !tag.contains("Y" + i, 6) || !tag.contains("Z" + i, 6)) return null;
            points.add(new Vec3(tag.getDouble("X" + i), tag.getDouble("Y" + i), tag.getDouble("Z" + i)));
        }
        try {
            return new ShipAccessPath(points, tag.getLong("Start"), tag.getInt("Serial"), tag.getBoolean("Rollback"));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
