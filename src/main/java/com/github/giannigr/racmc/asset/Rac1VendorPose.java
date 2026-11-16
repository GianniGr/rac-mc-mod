package com.github.giannigr.racmc.asset;

/** Vendor-only pose layer, deliberately not a change to the shared importer/evaluator. */
public final class Rac1VendorPose {
    public static final float HOLOGRAM_HEIGHT = 2.95f;
    public static final float HOLOGRAM_SCALE = 2.5f;

    private Rac1VendorPose() {
    }

    public record Mesh(float[] positions, float[] normals) {
    }

    public static Mesh closed(Rac1VendorAsset vendor) {
        return pose(vendor.body(), Rac1VendorAsset.closedSequence(vendor.body()));
    }

    public static Mesh hologram(Rac1VendorAsset vendor, float seconds) {
        int joints = vendor.hologram().skeleton().jointCount();
        float[] rotations = new float[joints * 4];
        for (int joint = 0; joint < joints; joint++) {
            rotations[joint * 4 + 3] = 1;
        }
        // ReRAC interaction.md §9.1: joints 1/2 rotate +/- .01 rad per retail frame.
        // Original Z-up becomes Minecraft Y-up. Class scale is already in the imported data.
        float angle = (float) ((seconds * 0.6) % (Math.PI * 2));
        for (int joint = 1; joint <= 2; joint++) {
            float half = (joint == 1 ? angle : -angle) * 0.5f;
            rotations[joint * 4 + 1] = (float) Math.sin(half);
            rotations[joint * 4 + 3] = (float) Math.cos(half);
        }
        Rac1Animation animation = new Rac1Animation("vendor_logo_spin", new float[] {0},
                false, rotations, new int[0], new int[0], new float[0],
                new int[0], new int[0], new float[0], new boolean[0]);
        return pose(vendor.hologram(), animation);
    }

    private static Mesh pose(Rac1AnimatedAsset asset, Rac1Animation animation) {
        float[] positions = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, 0, 1);
        Rac1AnimationEvaluator.pose(asset.mesh(), matrices, positions, normals);
        return new Mesh(positions, normals);
    }
}
