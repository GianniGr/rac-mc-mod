package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1Texture;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WrenchScaleGeometryTest {
    private static final ActionVector HAND = new ActionVector(5, 2, -3);
    private static final ActionVector AIM = new ActionVector(1, 0, 0);
    private static final ActionRotation ROTATION = new ActionRotation(0, 0, 1, 0);
    private static final WrenchActionState.Surface CLEAR = (a, b) -> null;

    @Test void sourceSlamHeadEnlargesAboutGripButNeverWidensLandingContacts() {
        var local = WrenchActionGeometry.head(attachments(), 10, 0.25)
                .getTranslation(new Vector3f());
        assertEquals(1.92, local.z, 1.0e-6);
        var root = worldSocket(HAND, ROTATION, 0);
        var physicalHead = vector(root.transformPosition(new Vector3f(local)));
        var visibleHead = vector(new Matrix4f(root).scale(1.18F).transformPosition(new Vector3f(local)));
        var frame = new WrenchActionState.ClipFrame(.25, .25, 1, 1, false, true, false, true);
        var landing = new WrenchSlamSampling.Pose(HAND, physicalHead, ActionVector.ZERO,
                ROTATION, true, false, true, frame);
        assertVector(visibleHead, landing.visibleHead());
        assertEquals(1.18 * physicalHead.subtract(HAND).length(), visibleHead.subtract(HAND).length(), 1.0e-6);
        assertTrue(WrenchSlamSampling.spheres(landing, at(visibleHead)),
                "default-rig enlarged attachment is still inside the unchanged landing tip sphere");
        var forward = physicalHead.subtract(HAND).normalized();
        var visualOnlyExtension = visibleHead.add(forward.multiply(.43));
        assertFalse(WrenchSlamSampling.intersects(landing, landing, at(visualOnlyExtension)));
        assertEquals(.085, WrenchSlamSampling.tip(landing).subtract(physicalHead).length(), 1.0e-6);

        var slam = new WrenchActionState();
        assertTrue(slam.start(1, WrenchActionState.Intent.SLAM, 0,
                new WrenchActionState.Eligibility(true, true, true, false, 4, 1, true, true),
                HAND, AIM));
        var atImpact = new WrenchActionState.SourceFrame(frame, HAND, ROTATION, true, landing.visibleHead());
        var edge = slam.tickSources(1, true, List.of(atImpact, atImpact, atImpact), CLEAR);
        assertVector(visibleHead, edge.position());
        assertEquals(WrenchActionPresentation.Phase.RECOVERY, slam.presentation().phase());
        assertNull(slam.tickSources(2, true, List.of(atImpact, atImpact, atImpact), CLEAR));
    }

    @Test void throwSocketAndHeadKeepTheirLegacyPathAndRenderedCatchMatchesHeldSizeAndGrip() {
        var asset = attachments();
        var state = new WrenchActionState();
        assertTrue(state.start(1, WrenchActionState.Intent.THROW, 0,
                new WrenchActionState.Eligibility(true, true, true, true, 0, 0, false, true),
                HAND, AIM));
        state.tick(1, true, false, HAND, ROTATION,
                new WrenchActionState.ClipFrame(.05, false, false, false), CLEAR);
        state.tick(2, true, false, HAND, ROTATION,
                new WrenchActionState.ClipFrame(.1, true, false, false), CLEAR);
        var first = state.flightSteps().getFirst();
        assertEquals(HAND, first.from());
        assertEquals(23 / 60.0, first.to().subtract(first.from()).length(), 1.0e-9);
        assertVector(visibleAnchor(asset, first.from(), first.fromRotation(), 0, 1, 1),
                visualFlightHead(asset, first, true));
        assertUnscaledAnchor(asset, first);

        ActionVector catchingHand = HAND.add(new ActionVector(0.2, 0.1, -0.1));
        WrenchActionState.FlightStep caught = null;
        for (int tick = 3; tick <= 80; tick++) {
            state.tick(tick, true, false, catchingHand, ROTATION,
                    new WrenchActionState.ClipFrame(tick / 20.0, false, false, false), CLEAR);
            for (var step : state.flightSteps()) assertUnscaledAnchor(asset, step);
            if (state.presentation().phase() == WrenchActionPresentation.Phase.CATCH) {
                caught = state.flightSteps().getLast();
                break;
            }
        }
        assertNotNull(caught);
        assertNotNull(caught.returnOrigin(), "the attraction origin still comes from the actual return update");
        assertEquals(catchingHand, caught.to());
        assertEquals(ROTATION, caught.toRotation());
        assertEquals(0, caught.toSpin());
        assertEquals(1, caught.sequence());
        assertEquals(1, caught.blend());
        assertVector(visibleAnchor(asset, catchingHand, ROTATION, 0, 1, 1),
                visualFlightHead(asset, caught, false));
        assertEquals(7.9796296, WrenchThrowGeometry.outwardTravelBudget(), 1.0e-5);
    }

    private static void assertUnscaledAnchor(Rac1AnimatedAsset asset, WrenchActionState.FlightStep step) {
        var model = worldSocket(step.to(), step.toRotation(), step.toSpin());
        for (int list : new int[] {0, 1}) {
            var expected = vector(new Matrix4f(model)
                    .mul(WrenchActionGeometry.anchor(asset, step.sequence(), list, step.blend()))
                    .getTranslation(new Vector3f()));
            assertVector(expected, WrenchThrowGeometry.anchor(asset, step, 1, list, false));
        }
        var physicalHead = WrenchThrowGeometry.anchor(asset, step, 1, 1, false);
        assertTrue(WrenchContact.sphereIntersects(physicalHead, 0.4, at(visualFlightHead(asset, step, false))));
    }

    private static ActionVector visualFlightHead(Rac1AnimatedAsset asset, WrenchActionState.FlightStep step,
            boolean previous) {
        return previous ? visibleAnchor(asset, step.from(), step.fromRotation(), step.fromSpin(),
                step.fromSequence(), step.fromBlend()) : visibleAnchor(asset, step.to(), step.toRotation(),
                step.toSpin(), step.sequence(), step.blend());
    }

    private static ActionVector visibleAnchor(Rac1AnimatedAsset asset, ActionVector grip, ActionRotation q,
            double spin, int sequence, double blend) {
        return vector(worldSocket(grip, q, spin).scale(WrenchActionGeometry.STEVE_MESH_SCALE)
                .mul(WrenchActionGeometry.anchor(asset, sequence, 1, blend)).getTranslation(new Vector3f()));
    }

    private static Matrix4f worldSocket(ActionVector grip, ActionRotation q, double spin) {
        return new Matrix4f().translation((float)grip.x(), (float)grip.y(), (float)grip.z())
                .rotate(new Quaternionf(q.x(), q.y(), q.z(), q.w())).scale(0.9375F).rotateY((float) spin);
    }

    private static WrenchContact.Box at(ActionVector point) { return new WrenchContact.Box(point, point); }
    private static ActionVector vector(Vector3f p) { return new ActionVector(p.x, p.y, p.z); }
    private static void assertVector(ActionVector a, ActionVector b) {
        assertEquals(0, a.subtract(b).length(), 2.0e-6);
    }

    private static Rac1AnimatedAsset attachments() {
        var skeleton = new Rac1Skeleton(new float[32], new int[]{-1, 0}, new float[6]);
        return new Rac1AnimatedAsset(71, "synthetic", null, skeleton,
                new Rac1Animation[]{clip(1, .46F), clip(6, .56F), clip(10, 1.92F)},
                new Rac1Texture[0], new int[][]{{0}, {0, 1}});
    }

    private static Rac1Animation clip(int sequence, float z) {
        return new Rac1Animation("sequence_" + sequence, new float[]{0}, false,
                new float[]{0, 0, 0, 1, 0, 0, 0, 1}, new int[]{0}, new int[]{1},
                new float[]{0, 0, z}, new int[0], new int[0], new float[0], new boolean[0]);
    }
}
