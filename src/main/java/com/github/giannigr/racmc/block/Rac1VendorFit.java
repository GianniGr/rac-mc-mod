package com.github.giannigr.racmc.block;

/** Uniform fit of the CLOSED footprint, not the bind pose or the unfolded menu pose. */
public record Rac1VendorFit(float scale, float x, float y, float z) {
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
