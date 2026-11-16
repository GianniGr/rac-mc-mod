package com.github.giannigr.racmc.loot;

import java.util.ArrayList;
import java.util.List;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;

/** Minecraft adaptation: bounded sphere/voxel-box sweep and depenetration, not PS2 mesh collision. */
public final class Rac1BoltCollision implements Collision {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    @FunctionalInterface public interface Geometry { List<Box> boxes(V from, V to, double radius); }
    private final Geometry geometry;
    public Rac1BoltCollision(Geometry geometry) { this.geometry = geometry; }

    @Override public Movement move(V from, V displacement, double radius) {
        List<Box> boxes = geometry.boxes(from, from.add(displacement), radius);
        List<Contact> contacts = new ArrayList<>();
        V p = from;
        // At most .5 radius per sample. Ordinary launches need 1-2 samples, not a global tick loop.
        int samples = Math.max(1, (int) Math.ceil(displacement.length() / (radius * .5)));
        V step = displacement.mul(1.0 / samples);
        for (int sample = 0; sample < samples; sample++) {
            p = p.add(step);
            for (int pass = 0; pass < 8; pass++) {
                boolean changed = false;
                for (Box box : boxes) {
                    V point = new V(clamp(p.x(), box.minX, box.maxX),
                            clamp(p.y(), box.minY, box.maxY), clamp(p.z(), box.minZ, box.maxZ));
                    V separation = p.sub(point);
                    double distance = separation.length();
                    if (distance >= radius) continue;
                    V normal;
                    if (distance < 1e-12) {
                        // Embedded ring spawn: nearest exit face, including from inside a full block.
                        double[] depths = {p.x() - box.minX, box.maxX - p.x(),
                                p.y() - box.minY, box.maxY - p.y(), p.z() - box.minZ, box.maxZ - p.z()};
                        int face = 0;
                        for (int i = 1; i < 6; i++) if (depths[i] < depths[face]) face = i;
                        normal = switch (face) {
                            case 0 -> new V(-1, 0, 0); case 1 -> new V(1, 0, 0);
                            case 2 -> new V(0, -1, 0); case 3 -> V.UP;
                            case 4 -> new V(0, 0, -1); default -> new V(0, 0, 1);
                        };
                        point = p.add(normal.mul(depths[face]));
                        p = point.add(normal.mul(radius + 1e-7));
                    } else {
                        normal = separation.unit();
                        p = point.add(normal.mul(radius + 1e-7));
                    }
                    // Polygon face normal is separate from edge/corner sphere separation.
                    V faceNormal = faceNormal(normal);
                    contacts.add(new Contact(point, normal, faceNormal));
                    double into = step.dot(normal);
                    if (into < 0) step = step.sub(normal.mul(2 * into));
                    changed = true;
                }
                if (!changed) break;
            }
        }
        return new Movement(p, List.copyOf(contacts));
    }
    private static V faceNormal(V n) {
        if (Math.abs(n.y()) >= Math.max(Math.abs(n.x()), Math.abs(n.z()))) return V.UP.mul(Math.signum(n.y()));
        if (Math.abs(n.x()) >= Math.abs(n.z())) return new V(Math.signum(n.x()), 0, 0);
        return new V(0, 0, Math.signum(n.z()));
    }
    private static double clamp(double x, double min, double max) { return Math.max(min, Math.min(max, x)); }
}
