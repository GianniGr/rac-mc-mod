package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import java.util.Arrays;

/** Separate closed-world baseline and enclosure-based item fits, both in block-corner space. */
public record Rac1VendorFit(float scale, float x, float y, float z) {
    /** Owner's interim world-only enlargement, not a claim that antenna-free body is exactly 1m. */
    public static final float WORLD_ENLARGEMENT = 2.0f;

    /** Enlarge around the block's grounded horizontal center, not its corner. */
    public Rac1VendorFit inWorld() {
        return new Rac1VendorFit(scale * WORLD_ENLARGEMENT,
                0.5f + (x - 0.5f) * WORLD_ENLARGEMENT, y * WORLD_ENLARGEMENT,
                0.5f + (z - 0.5f) * WORLD_ENLARGEMENT);
    }

    /**
     * Fit the panel/corner enclosure, not the projecting antenna arms or projector cap.
     * Class 11 materials 1/2 are the verified 552-panel/464-corner triangle groups.
     * No radius clipping, rescaling of individual parts or dependency on world enlargement.
     */
    public static Rac1VendorFit forItem(Rac1SkinnedMesh mesh, float[] closedPositions) {
        if (closedPositions.length != mesh.vertexCount() * 3) {
            throw new IllegalArgumentException("The vendor item pose does not match its mesh.");
        }
        float[] body = new float[mesh.indices().length * 3];
        int count = 0;
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            int material = mesh.materials()[triangle];
            if (material != 1 && material != 2) {
                continue;
            }
            for (int corner = 0; corner < 3; corner++) {
                int vertex = mesh.indices()[triangle * 3 + corner];
                System.arraycopy(closedPositions, vertex * 3, body, count, 3);
                count += 3;
            }
        }
        return of(Arrays.copyOf(body, count));
    }

    /** Baseline antenna-inclusive fit; retained unchanged for the accepted 2x world model. */
    public static Rac1VendorFit of(float[] closedPositions) {
        if (closedPositions.length == 0 || closedPositions.length % 3 != 0) {
            throw new IllegalArgumentException("The vendor has no complete closed vertices.");
        }
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxZ = maxX;
        for (int at = 0; at < closedPositions.length; at += 3) {
            float x = closedPositions[at], y = closedPositions[at + 1], z = closedPositions[at + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("The vendor has non-finite closed vertices.");
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        float footprint = Math.max(maxX - minX, maxZ - minZ);
        if (footprint <= 0) {
            throw new IllegalArgumentException("The vendor has no closed footprint.");
        }
        float scale = 1.0f / footprint;
        // Translation is in BLOCK units, applied before scale, including for the item cube.
        return new Rac1VendorFit(scale, 0.5f - (minX + maxX) * 0.5f * scale,
                -minY * scale, 0.5f - (minZ + maxZ) * 0.5f * scale);
    }
}
