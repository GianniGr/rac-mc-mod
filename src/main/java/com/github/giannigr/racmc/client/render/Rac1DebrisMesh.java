package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/** CPU-only particle material/UV selection from imported triangles, never a flat-color proxy. */
public final class Rac1DebrisMesh {
    public record Patch(int material, float u0, float u1, float v0, float v1) {}
    private record Face(int material, double area, double nx, double ny, double nz, float u, float v) {}
    private final List<Face> faces = new ArrayList<>();
    private final Rac1Texture[] textures;

    public Rac1DebrisMesh(float[] positions, float[] uvs, int[] indices, int[] materials,
                         Rac1Texture[] textures) {
        this.textures = textures;
        for (int t = 0; t < materials.length; t++) {
            int material = materials[t];
            if (material < 0 || material >= textures.length) continue;
            int a = indices[t * 3], b = indices[t * 3 + 1], c = indices[t * 3 + 2];
            double ax = positions[b * 3] - positions[a * 3];
            double ay = positions[b * 3 + 1] - positions[a * 3 + 1];
            double az = positions[b * 3 + 2] - positions[a * 3 + 2];
            double bx = positions[c * 3] - positions[a * 3];
            double by = positions[c * 3 + 1] - positions[a * 3 + 1];
            double bz = positions[c * 3 + 2] - positions[a * 3 + 2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!Double.isFinite(length) || length <= 1e-12) continue;
            float u = wrap((uvs[a * 2] + uvs[b * 2] + uvs[c * 2]) / 3);
            float v = wrap((uvs[a * 2 + 1] + uvs[b * 2 + 1] + uvs[c * 2 + 1]) / 3);
            if (!Float.isFinite(u) || !Float.isFinite(v)) continue;
            // Transparent packets are not useful debris icons. Preserve actual RGB/alpha otherwise.
            var texture = textures[material];
            if ((texture.pixel(Math.min(texture.width() - 1, (int) (u * texture.width())),
                    Math.min(texture.height() - 1, (int) (v * texture.height()))) >>> 24) == 0) continue;
            faces.add(new Face(material, length / 2, nx / length, ny / length, nz / length, u, v));
        }
    }

    public Rac1Texture texture(int material) { return textures[material]; }

    /** Face-aware hit sampling; whole-surface area weighting for destruction. */
    public Patch sample(Direction side, double fraction) {
        double sum = faces.stream().mapToDouble(f -> weight(f, side)).sum();
        if (sum <= 0 && side != null) return sample(null, fraction);
        if (sum <= 0) return null;
        double choice = Math.clamp(Double.isFinite(fraction) ? fraction : 0, 0, Math.nextDown(1.0)) * sum;
        Face chosen = faces.getLast();
        for (Face face : faces) {
            double weight = weight(face, side);
            if (weight > 0 && choice < weight) { chosen = face; break; }
            choice -= weight;
        }
        // A small real-texture patch at the triangle centroid avoids unrelated atlas backgrounds.
        float half = 1f / 32;
        float u0 = Math.clamp(chosen.u - half, 0, 1 - 2 * half);
        float v0 = Math.clamp(chosen.v - half, 0, 1 - 2 * half);
        return new Patch(chosen.material, u0, u0 + 2 * half, v0, v0 + 2 * half);
    }

    private static double weight(Face f, Direction side) {
        return f.area * (side == null ? 1 : Math.max(0,
                f.nx * side.getStepX() + f.ny * side.getStepY() + f.nz * side.getStepZ()));
    }

    private static float wrap(float uv) { return uv - (float) Math.floor(uv); }
}
