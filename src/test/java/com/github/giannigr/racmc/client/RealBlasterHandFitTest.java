package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Numeric full-source-silhouette fitting at fixed cameras, not screenshots/GPU/owner acceptance.
 * Keeps scale, original animation and the existing owner-held whole-body anchor unchanged.
 */
class RealBlasterHandFitTest {
    @Test void leftSilhouetteFitsAndBothSourceRingsAgreeAtFixedWideSlimCrouchFovCameras() throws Exception {
        String iso = System.getProperty("racmc.isoImage", "");
        assumeTrue(!iso.isBlank());
        try (var inspection = new Rac1DiscValidator().inspect(Path.of(iso))) {
            assertTrue(inspection.isSupported());
            var asset = Rac1AssetExtractor.readGadgetAsset(Rac1LevelAssets.load(inspection.image(), 1), 168);
            var ring = new ArrayList<Integer>();
            var unique = new HashSet<String>();
            var bind = asset.mesh().positions();
            for (int i = 0; i < bind.length; i += 3)
                if (Math.abs(bind[i + 2] - .633463502) < .000001
                        && unique.add(bind[i] + "," + bind[i + 1] + "," + bind[i + 2])) ring.add(i);
            assertEquals(12, ring.size());
            var display = BlasterWorldHandContractTest.display();
            var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1000f / 720, .05f, 1024);
            int samples = 0;
            double worstError = 0;
            double worstLeftBottom = 0;
            double worstRightBottom = 0;
            boolean fits = true;
            for (boolean left : new boolean[]{false, true})
                for (boolean slim : new boolean[]{false, true})
                    for (boolean crouch : new boolean[]{false, true})
                        for (int fov : new int[]{70, 77, 110}) {
                            var world = BlasterWorldHandContractTest.world(display, left, slim, crouch);
                            var target = world.transformPosition(new Vector3f(.2305f, -.03f, .6335f));
                            // Literal camera yaw0, pitch0: world+Z is camera-Z; physical eye height.
                            target.set(-target.x, target.y - (crouch ? 1.27f : 1.62f), -target.z);
                            var worldProjection = new Matrix4f().perspective((float) Math.toRadians(fov), 1000f / 720, .05f, 1024);
                            var wanted = new Vector4f(target, 1).mul(worldProjection);
                            wanted.div(wanted.w);
                            var stack = new PoseStack();
                            stack.translate(left ? -.56f : .56f, -.52f, -.72f);
                            display.getTransform(left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                                    : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(left, stack);
                            stack.translate(-.5, -.5, -.5);
                            stack.translate(.5, .5, .5);
                            var cameraChain = new Matrix4f(stack.last().pose());
                            var move = Rac1BlasterAim.align(cameraChain, new Matrix4f(), worldProjection, projection, target);
                            var aligned = new Matrix4f(cameraChain).translate(move);
                            var pose = new Rac1BlasterPose();
                            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                            double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                            for (float blend : new float[]{0, .5f, 1})
                                for (int key = 0; key < 15; key++) {
                                    pose.update(asset, blend, key / 30f);
                                    var correction = pose.muzzleCorrection();
                                    var draw = new Matrix4f(aligned).translate((float) correction.x(),
                                            (float) correction.y(), (float) correction.z());
                                    var positions = pose.positions();
                                    for (int at = 0; at < positions.length; at += 3) {
                                        var clip = new Vector4f(positions[at], positions[at + 1], positions[at + 2], 1)
                                                .mul(draw).mul(projection);
                                        assertTrue(clip.w > .05, "Source vertex behind/inside camera near plane");
                                        double x = (clip.x / clip.w + 1) * 500;
                                        double y = (1 - clip.y / clip.w) * 360;
                                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                                    }
                                    var center = new Vector3f();
                                    for (int at : ring) center.add(positions[at], positions[at + 1], positions[at + 2]);
                                    center.div(12);
                                    var actualRing = new Vector4f(center, 1).mul(draw).mul(projection);
                                    actualRing.div(actualRing.w);
                                    worstError = Math.max(worstError, Math.hypot(
                                            (actualRing.x - wanted.x) * 500, (actualRing.y - wanted.y) * 360));
                                    samples++;
                                }
                            System.out.printf(java.util.Locale.ROOT,
                                    "%s left=%s slim=%s crouch=%s worldFov=%d 1000x720 fullsource x=[%.3f,%.3f] y=[%.3f,%.3f]%n",
                                    inspection.result().revision(), left, slim, crouch, fov, minX, maxX, minY, maxY);
                            // Right is an unchanged reference, not waived/certified by this left-only repair.
                            if (left) fits &= minX >= 10 && maxX <= 990 && minY >= 7.2 && maxY <= 676.8;
                            if (left) worstLeftBottom = Math.max(worstLeftBottom, maxY);
                            else worstRightBottom = Math.max(worstRightBottom, maxY);
                        }
            System.out.println("Independent fixed-camera original-source samples=" + samples + " maxRingErrorPixels="
                    + worstError + " leftBottom=" + worstLeftBottom + " rightBottom=" + worstRightBottom);
            assertEquals(1080, samples);
            assertTrue(worstError < .001, "Actual animated source ring must agree with independent world hand target");
            assertTrue(fits, "Left FP source silhouette exceeds fixed-camera margins despite matching ring center");
        }
    }
}
