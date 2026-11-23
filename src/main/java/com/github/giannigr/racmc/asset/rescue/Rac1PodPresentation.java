package com.github.giannigr.racmc.asset.rescue;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import java.util.Arrays;

/** Minecraft mounting and glass treatment, not an original landed pose or hatch animation. */
public record Rac1PodPresentation(Rac1Mesh hull, Rac1Mesh canopy, Rac1Mesh occupant, float hullCenterY) {
    static final int CANOPY_FIRST = 1570;
    static final int CANOPY_END = 1616;
    public static final float FULL_SPAN = 2.94f;
    public static final float LEGACY_SPAN = .84f;

    public static Rac1PodPresentation of(Rac1PodAssets assets, boolean full) throws AssetFormatException {
        var hull = assets.hull();
        var occupant = assets.occupant();
        verifyCanopy(hull);
        var hullBounds = Bounds.of(hull.positions());
        float span = full ? FULL_SPAN : LEGACY_SPAN;
        float centerY = span * hullBounds.halfHeight() / hullBounds.span();
        var hullPositions = place(hull.positions(), hullBounds, span, .5f, centerY, .5f, false);
        var hullMesh = hull.model().mesh();
        int[] indices = hullMesh.indices();
        int[] materials = hullMesh.materials();
        int[] shellIndices = new int[indices.length];
        int[] shellMaterials = new int[materials.length];
        int shellCount = 0;
        for (int i = 0; i < materials.length; i++) {
            // There is one untextured sentinel triangle in the verified regular hull import.
            if (isCanopy(i) || materials[i] == -1) continue;
            System.arraycopy(indices, i * 3, shellIndices, shellCount * 3, 3);
            shellMaterials[shellCount++] = materials[i];
        }
        var fittedHull = new Rac1Mesh(hullPositions, hull.normals(), hullMesh.uvs(),
                Arrays.copyOf(shellIndices, shellCount * 3), Arrays.copyOf(shellMaterials, shellCount));
        var fittedCanopy = new Rac1Mesh(hullPositions, hull.normals(), hullMesh.uvs(),
                Arrays.copyOfRange(indices, CANOPY_FIRST * 3, CANOPY_END * 3),
                Arrays.copyOfRange(materials, CANOPY_FIRST, CANOPY_END));
        var mountedOccupant = place(occupant.positions(), Bounds.of(occupant.positions()), span * .18f,
                .5f + span * .225f, centerY + span * .059f, .5f, true);
        var occupantMesh = occupant.model().mesh();
        float[] normals = occupant.normals().clone();
        for (int i = 0; i < normals.length; i += 3) {
            normals[i] = -normals[i];
            normals[i + 2] = -normals[i + 2];
        }
        return new Rac1PodPresentation(fittedHull, fittedCanopy,
                new Rac1Mesh(mountedOccupant, normals, occupantMesh.uvs(), occupantMesh.indices(),
                        occupantMesh.materials()), centerY);
    }

    static boolean isCanopy(int triangle) {
        return triangle >= CANOPY_FIRST && triangle < CANOPY_END;
    }

    private static void verifyCanopy(Rac1PodAssets.PosedModel hull) throws AssetFormatException {
        var mesh = hull.model().mesh();
        for (int i = CANOPY_FIRST; i < CANOPY_END; i++) {
            if (mesh.materials()[i] != 0) throw new AssetFormatException("Rescue canopy material mismatch");
            for (int corner = 0; corner < 3; corner++) {
                int at = mesh.indices()[i * 3 + corner] * 3;
                float x = hull.positions()[at], y = hull.positions()[at + 1], z = hull.positions()[at + 2];
                if (!(x > .3f && x < 2.2f && y > .3f && y < 1.1f && Math.abs(z) < .65f)) {
                    throw new AssetFormatException("Rescue canopy geometry mismatch");
                }
            }
        }
    }

    private static float[] place(float[] positions, Bounds bounds, float span,
                                 float x, float y, float z, boolean turn) {
        float[] mounted = new float[positions.length];
        float scale = span / bounds.span();
        float direction = turn ? -1 : 1;
        for (int i = 0; i < positions.length; i += 3) {
            mounted[i] = x + direction * (positions[i] - bounds.x) * scale;
            mounted[i + 1] = y + (positions[i + 1] - bounds.y) * scale;
            mounted[i + 2] = z + direction * (positions[i + 2] - bounds.z) * scale;
        }
        return mounted;
    }

    private record Bounds(float x, float y, float z, float span, float halfHeight) {
        static Bounds of(float[] xyz) throws AssetFormatException {
            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int i = 0; i < xyz.length; i++) {
                if (!Float.isFinite(xyz[i])) throw new AssetFormatException("Nonfinite rescue mesh");
                min[i % 3] = Math.min(min[i % 3], xyz[i]);
                max[i % 3] = Math.max(max[i % 3], xyz[i]);
            }
            float span = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
            if (!(span > 0 && Float.isFinite(span))) throw new AssetFormatException("Invalid rescue mesh bounds");
            return new Bounds((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2,
                    span, (max[1] - min[1]) / 2);
        }
    }
}
