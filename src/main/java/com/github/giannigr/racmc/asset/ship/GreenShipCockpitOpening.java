package com.github.giannigr.racmc.asset.ship;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import java.util.ArrayList;
import java.util.List;

/** Subtracts only an authored, ship-local opening. Does not move or scale any exterior vertex. */
public final class GreenShipCockpitOpening {
    private GreenShipCockpitOpening() {}

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Bounds {
            if (!(Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                    && minX < maxX && minY < maxY && minZ < maxZ)) {
                throw new IllegalArgumentException("Invalid cockpit opening");
            }
        }
    }

    /** Coordinates are in ship-local Minecraft blocks; attributes interpolate only on cut edges. */
    public static Rac1Mesh subtract(Rac1Mesh mesh, Bounds bounds) {
        var planes = new Plane[] {
                new Plane(0, bounds.minX(), 1), new Plane(0, bounds.maxX(), -1),
                new Plane(1, bounds.minY(), 1), new Plane(1, bounds.maxY(), -1),
                new Plane(2, bounds.minZ(), 1), new Plane(2, bounds.maxZ(), -1)
        };
        var position = new ArrayList<Float>();
        var normals = new ArrayList<Float>();
        var uv = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();
        var materials = new ArrayList<Integer>();
        for (int t = 0; t < mesh.triangleCount(); t++) {
            List<Vertex> face = List.of(vertex(mesh, mesh.indices()[3 * t]),
                    vertex(mesh, mesh.indices()[3 * t + 1]), vertex(mesh, mesh.indices()[3 * t + 2]));
            int material = mesh.materials()[t];
            for (var plane : planes) {
                if (face.size() < 3) break;
                var inside = new ArrayList<Vertex>(face.size() + 1);
                var outside = new ArrayList<Vertex>(face.size() + 1);
                var a = face.getLast();
                float da = plane.distance(a);
                for (var b : face) {
                    float db = plane.distance(b);
                    if ((da < 0) != (db < 0)) {
                        Vertex cut = a.mix(b, da / (da - db), plane);
                        inside.add(cut);
                        outside.add(cut);
                    }
                    (db < 0 ? outside : inside).add(b);
                    a = b;
                    da = db;
                }
                // Fragments outside successive planes are disjoint; discard only their remainder.
                for (int j = 1; j + 1 < outside.size(); j++) {
                    var first = outside.getFirst();
                    var second = outside.get(j);
                    var third = outside.get(j + 1);
                    if (!area(first, second, third)) continue;
                    for (var v : List.of(first, second, third)) {
                        indices.add(position.size() / 3);
                        position.add(v.x); position.add(v.y); position.add(v.z);
                        normals.add(v.nx); normals.add(v.ny); normals.add(v.nz);
                        uv.add(v.u); uv.add(v.v);
                    }
                    materials.add(material);
                }
                face = inside;
            }
        }
        return new Rac1Mesh(floats(position), floats(normals), floats(uv),
                indices.stream().mapToInt(Integer::intValue).toArray(),
                materials.stream().mapToInt(Integer::intValue).toArray());
    }

    private static boolean area(Vertex a, Vertex b, Vertex c) {
        double x1 = b.x - a.x, y1 = b.y - a.y, z1 = b.z - a.z;
        double x2 = c.x - a.x, y2 = c.y - a.y, z2 = c.z - a.z;
        return Math.abs(y1 * z2 - z1 * y2) + Math.abs(z1 * x2 - x1 * z2)
                + Math.abs(x1 * y2 - y1 * x2) > 1e-10;
    }

    private static float[] floats(List<Float> source) {
        float[] values = new float[source.size()];
        for (int i = 0; i < values.length; i++) values[i] = source.get(i);
        return values;
    }

    private static Vertex vertex(Rac1Mesh mesh, int i) {
        var p = mesh.positions();
        var n = mesh.normals();
        var uv = mesh.uvs();
        return new Vertex(p[3 * i], p[3 * i + 1], p[3 * i + 2],
                n[3 * i], n[3 * i + 1], n[3 * i + 2], uv[2 * i], uv[2 * i + 1]);
    }

    private record Plane(int axis, float edge, int sign) {
        float distance(Vertex v) {
            return sign * ((axis == 0 ? v.x : axis == 1 ? v.y : v.z) - edge);
        }
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        Vertex mix(Vertex b, float t, Plane plane) {
            return new Vertex(plane.axis == 0 ? plane.edge : lerp(x, b.x, t),
                    plane.axis == 1 ? plane.edge : lerp(y, b.y, t),
                    plane.axis == 2 ? plane.edge : lerp(z, b.z, t),
                    lerp(nx, b.nx, t), lerp(ny, b.ny, t), lerp(nz, b.nz, t),
                    lerp(u, b.u, t), lerp(v, b.v, t));
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }
}
