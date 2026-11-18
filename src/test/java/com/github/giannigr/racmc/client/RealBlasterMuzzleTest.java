package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Read-only source measurement; no proprietary geometry/animation fixtures or output files.
 * This verifies the original ring plus rigid anchor, not final live world/GPU presentation.
 */
class RealBlasterMuzzleTest {
    @Test void measureGripPreservingSourceAttachmentAgainstActualAnimatedRing() throws Exception {
        String iso = System.getProperty("racmc.isoImage", "");
        assumeTrue(!iso.isBlank());
        try (var inspection = new Rac1DiscValidator().inspect(Path.of(iso))) {
            assertTrue(inspection.isSupported());
            var asset = Rac1AssetExtractor.readGadgetAsset(Rac1LevelAssets.load(inspection.image(), 1), 168);
            var indices = new java.util.ArrayList<Integer>();
            var unique = new HashSet<String>();
            float[] bind = asset.mesh().positions();
            var center = new org.joml.Vector3f();
            for (int i = 0; i < bind.length; i += 3)
                if (Math.abs(bind[i + 2] - .633463502) < .000001
                        && unique.add(bind[i] + "," + bind[i + 1] + "," + bind[i + 2])) {
                    indices.add(i); center.add(bind[i], bind[i + 1], bind[i + 2]);
                }
            assertEquals(12, indices.size());
            center.div(12);
            int[] chain = asset.attachmentChain(0);
            assertTrue(chain.length > 0, "Do not substitute a guessed attachment joint");
            int joint = chain[chain.length - 1];
            var inverse = new org.joml.Matrix4f().set(java.util.Arrays.copyOfRange(
                    asset.skeleton().inverseBind(), joint * 16, joint * 16 + 16));
            var held = new org.joml.Matrix4f().set(
                    com.github.giannigr.racmc.asset.Rac1AttachmentSampler.sample(asset, "sequence_1", 0, 0).orElseThrow())
                    .mul(inverse).transformPosition(new org.joml.Vector3f(center));
            var pose = new Rac1BlasterPose();
            double maximumResidual = 0;
            for (float blend : new float[]{0, .5f, 1})
                for (int key = 0; key < 30; key++) {
                    float time = key / 60f; // source keys AND half-keys, not just the bind pose
                    var fire = new org.joml.Matrix4f().set(
                            com.github.giannigr.racmc.asset.Rac1AttachmentSampler.sample(
                                    asset, "sequence_4", time, 0).orElseThrow())
                            .mul(inverse).transformPosition(new org.joml.Vector3f(center));
                    var sampled = new org.joml.Vector3f(held).lerp(fire, blend);
                    pose.update(asset, blend, time);
                    var actual = new org.joml.Vector3f();
                    for (int at : indices) actual.add(pose.positions()[at], pose.positions()[at + 1], pose.positions()[at + 2]);
                    actual.div(12);
                    maximumResidual = Math.max(maximumResidual, actual.distance(sampled));
                }
            assertTrue(Double.isFinite(maximumResidual));
            assertTrue(maximumResidual < 1e-6, "Verified attachment must track the original ring, not a guessed socket");
            System.out.println(inspection.result().revision()
                    + " grip-preserving source attachment (90 samples) joint=" + joint
                    + " maximum ring residual=" + maximumResidual
                    + "; measurement only, not a no-media authoritative strategy approval");
        }
    }
    @Test void measuredOriginalRingIsAnchoredForEveryFireKeyAndBlend() throws Exception {
        String iso = System.getProperty("racmc.isoImage", "");
        assumeTrue(!iso.isBlank());
        try (var inspection = new Rac1DiscValidator().inspect(Path.of(iso))) {
            assertTrue(inspection.isSupported());
            var asset = Rac1AssetExtractor.readGadgetAsset(Rac1LevelAssets.load(inspection.image(), 1), 168);
            float[] bind = asset.mesh().positions();
            var indices = new java.util.ArrayList<Integer>();
            var unique = new HashSet<String>();
            // Independently measured absolute front plane, not production's maximum-plane selector.
            for (int i = 0; i < bind.length; i += 3)
                if (Math.abs(bind[i + 2] - .633463502) < .000001
                        && unique.add(bind[i] + "," + bind[i + 1] + "," + bind[i + 2])) indices.add(i);
            assertEquals(12, indices.size());
            var pose = new Rac1BlasterPose();
            double largestTranslation = 0;
            for (float blend : new float[]{0, .5f, 1})
                for (int key = 0; key < 15; key++) {
                    pose.update(asset, blend, key / 30f);
                    var correction = pose.muzzleCorrection();
                    largestTranslation = Math.max(largestTranslation, Math.sqrt(correction.lengthSquared()));
                    double x = 0, y = 0, z = 0;
                    for (int at : indices) {
                        x += pose.positions()[at] + correction.x();
                        y += pose.positions()[at + 1] + correction.y();
                        z += pose.positions()[at + 2] + correction.z();
                    }
                    assertEquals(.2305, x / 12, 2e-8);
                    assertEquals(-.03, y / 12, 2e-8);
                    assertEquals(.6335, z / 12, 2e-8);
                }
            assertTrue(largestTranslation > .17 && largestTranslation < .3,
                    "Test must exercise substantial source animation, not only the already-centered bind ring");
            System.out.println(inspection.result().revision() + " anchored muzzle: 45 key/blend samples; max rigid correction=" + largestTranslation);
        }
    }
}
