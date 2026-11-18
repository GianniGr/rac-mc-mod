package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import com.github.giannigr.racmc.serverassets.*;
import com.github.giannigr.racmc.client.actions.WrenchModelPoseSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class WrenchCorrectionRealDataTest {
    private static Rac1WrenchData data;
    private static WrenchSteveRig rig;

    @BeforeAll static void localData() throws Exception {
        Path root = Files.createDirectories(Path.of(System.getProperty("racmc.wrenchDataTestDirectory"))
                .resolve("correction-" + UUID.randomUUID()));
        new Rac1MediaConfig(Path.of(System.getProperty("racmc.isoImage")), "", 0, "", 0)
                .write(Rac1MediaConfig.fileIn(root));
        Files.writeString(Rac1CacheLocation.fileIn(root),
                "directory=" + System.getProperty("racmc.wrenchActionCacheDirectory") + "\n");
        try (var provider = Rac1WrenchDataProvider.open(root, false)) {
            long end = System.nanoTime() + 45_000_000_000L;
            while (provider.data().isEmpty() && System.nanoTime() < end
                    && provider.status().state() != Rac1WrenchDataStatus.State.FAILED) Thread.sleep(10);
            data = provider.data().orElseThrow(() -> new AssertionError(provider.status()));
            assertEquals(Rac1WrenchDataStatus.Origin.SHARED_CACHE, provider.status().origin());
        }
        rig = new WrenchSteveRig(data);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap(); // Built-in EntityType geometry only, no Minecraft server/world/client.
    }

    @Test void allOriginalThrowAndSlamKeysKeepNativeWaistNeckShouldersAndLayerRootConnected() {
        double maximumExtraGap = 0;
        for (boolean slim : new boolean[] {false, true}) for (boolean left : new boolean[] {false, true}) {
            for (int sequence : new int[] {26, 43}) for (boolean crouch : new boolean[] {false, true}) {
                PlayerModel<?> model = model(slim, crouch);
                var bodyCube = cube(model.body);
                var rightLegCube = cube(model.rightLeg);
                var headCube = cube(model.head);
                assertEquals(12, bodyCube.maxY);
                assertEquals(0, rightLegCube.minY);
                // Native model parts are siblings, not body->leg/head children.
                assertThrows(NoSuchElementException.class, () -> model.body.getChild("right_leg"));
                for (double seconds : times(sequence)) {
                    var pose = rig.sample(sequence, seconds, sequence == 43 ? 10 : 6,
                            sequence == 43 ? Math.min(0.8, seconds) : 0, left, slim, 1, crouch);
                    Matrix4f root = pose.root();
                    Vector3f hip = new Vector3f(0, (crouch ? 12.2F : 12) / 16,
                            crouch ? 4.0F / 16 : 0);
                    assertTrue(root.transformPosition(new Vector3f(hip)).equals(hip, 1.0e-5F));
                    Vector3f waist = part(model.body).transformPosition(
                            new Vector3f(model.rightLeg.x / 16, bodyCube.maxY / 16, 0));
                    Vector3f leg = part(model.rightLeg).transformPosition(new Vector3f(
                            0, rightLegCube.minY / 16, 0));
                    Vector3f neck = part(model.head).transformPosition(new Vector3f(
                            0, headCube.maxY / 16, 0));
                    Vector3f chest = part(model.body).transformPosition(new Vector3f(
                            0, bodyCube.minY / 16, 0));
                    for (Vector3f[] anchors : new Vector3f[][] {{waist, leg}, {neck, chest}}) {
                        double base = anchors[0].distance(anchors[1]);
                        double animated = root.transformPosition(new Vector3f(anchors[0]))
                                .distance(root.transformPosition(new Vector3f(anchors[1])));
                        maximumExtraGap = Math.max(maximumExtraGap, Math.abs(animated - base));
                        assertEquals(base, animated, 1.0e-5, "No new gap beyond native crouch baseline");
                    }
                    var arm = left ? pose.leftArm() : pose.rightArm();
                    Vector3f shoulder = new Vector3f(left ? 5 : -5, crouch ? 5.2F : 2, 0).div(16);
                    Vector3f renderedShoulder = new Matrix4f(root)
                            .mul(WrenchSteveRig.modelPart(arm, 0)).getTranslation(new Vector3f());
                    assertTrue(renderedShoulder.equals(root.transformPosition(shoulder), 1.0e-5F));
                    // Armor/cape/hat/pants consume the SAME native model-root stack.
                    PoseStack layers = new PoseStack();
                    Matrix4f before = new Matrix4f(layers.last().pose());
                    layers.pushPose();
                    layers.mulPose(root);
                    assertTrue(layers.last().pose().equals(root, 1.0e-6F));
                    layers.popPose();
                    assertTrue(layers.last().pose().equals(before, 0));
                }
            }
        }
        System.out.println("Native full-clip maximum added waist/neck gap=" + maximumExtraGap);
    }

    @Test void nativeHandItemStackAndServerHeadShareOneRootYawScaleAndGrip() {
        double maxError = 0;
        for (boolean slim : new boolean[] {false, true}) for (boolean left : new boolean[] {false, true}) {
            for (int sequence : new int[] {26, 43}) for (double seconds : times(sequence)) {
                boolean crouch = sequence == 26;
                var pose = rig.sample(sequence, seconds, sequence == 43 ? 10 : 6,
                        sequence == 43 ? Math.min(0.8, seconds) : 0, left, slim, 1, crouch);
                var model = model(slim, crouch);
                apply(model.rightArm, pose.rightArm()); apply(model.leftArm, pose.leftArm());
                for (float yaw : new float[] {0, 90, -135}) {
                    // Native body yaw is deliberately stale; authoritative action-facing is not.
                    PoseStack render = new PoseStack();
                    render.mulPose(WrenchSteveRig.world(0, 0, 0, 0, 1, crouch));
                    render.mulPose(new Matrix4f().rotateY((float) Math.toRadians(yaw)));
                    render.mulPose(pose.root());
                    float originalX = (left ? model.leftArm : model.rightArm).x;
                    model.translateToHand(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, render);
                    assertEquals(originalX, (left ? model.leftArm : model.rightArm).x);
                    render.mulPose(new Quaternionf().rotationX((float) -Math.PI / 2));
                    render.mulPose(new Quaternionf().rotationY((float) Math.PI));
                    render.translate((left ? -1 : 1) / 16F, 0.125, -0.625);
                    render.mulPose(WrenchSteveRig.itemDisplay());
                    render.translate(-0.5, -0.5, -0.5);
                    render.translate(0.5, 0.5, 0.5); // Existing BEWLR origin restoration, once.
                    render.mulPose(pose.itemAdjustment());
                    Matrix4f server = WrenchSteveRig.world(0, 0, 0, yaw, 1, crouch).mul(pose.socket());
                    Vector3f grip = render.last().pose().getTranslation(new Vector3f());
                    maxError = Math.max(maxError, grip.distance(server.getTranslation(new Vector3f())));
                    assertTrue(render.last().pose().equals(server, 2.0e-5F));
                    assertTrue(pose.itemAdjustment().getTranslation(new Vector3f())
                            .length() < 1.0e-5, "Wrist adjustment rotates about real grip, never floats");
                    Matrix4f localHead = WrenchActionGeometry.head(data.wrench(),
                            sequence == 43 ? 10 : 6, sequence == 43 ? Math.min(0.8, seconds) : 0);
                    Vector3f visible = new Matrix4f(render.last().pose()).mul(localHead)
                            .getTranslation(new Vector3f());
                    Vector3f authority = WrenchSteveRig.world(0, 0, 0, yaw, 1, crouch)
                            .mul(pose.head()).getTranslation(new Vector3f());
                    maxError = Math.max(maxError, visible.distance(authority));
                    assertTrue(visible.equals(authority, 2.0e-5F));
                }
            }
        }
        System.out.println("Native rendered grip/head vs authority maximum error=" + maxError);
    }

    @Test void originalMeshBoundsAndDeformationParameterRemainMeasuredNotDoubleScaled() {
        double rawHeadMax = 0, adaptedHeadMax = 0, meshSpanMax = 0;
        for (double seconds : times(43)) {
            seconds = Math.min(seconds, 0.8);
            var mesh = WrenchActionGeometry.mesh(data.wrench(), 10, (float) seconds);
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < mesh.positions().length; i += 3) {
                min = Math.min(min, mesh.positions()[i]); max = Math.max(max, mesh.positions()[i]);
            }
            meshSpanMax = Math.max(meshSpanMax, (max - min) * 0.9375);
            float[] raw = com.github.giannigr.racmc.asset.Rac1AttachmentSampler.sample(data.wrench(),
                    "sequence_10", (float) seconds, 1).orElseThrow();
            rawHeadMax = Math.max(rawHeadMax, new Vector3f(raw[12], raw[13], raw[14]).length() * 0.9375);
            adaptedHeadMax = Math.max(adaptedHeadMax, WrenchActionGeometry.head(data.wrench(), 10, seconds)
                    .getTranslation(new Vector3f()).length() * 0.9375);
        }
        assertTrue(rawHeadMax > 1.80 && rawHeadMax < 1.83);
        assertTrue(adaptedHeadMax <= rawHeadMax + 1.0e-5);
        System.out.println("Slam amplitude=" + WrenchActionGeometry.SLAM_DEFORMATION
                + " rawHead=" + rawHeadMax + " presented/authorityHead=" + adaptedHeadMax
                + " meshSpan=" + meshSpanMax + " head/playerHeight=" + adaptedHeadMax / 1.8);
    }

    @Test void normalJumpSamplesContactNativeZombieBoundsThroughLandingAtBothFacingYaws() {
        var dimensions = EntityType.ZOMBIE.getDimensions();
        for (float yaw : new float[] {0, 90}) {
            int contactFrames = 0, landingFrames = 0;
            var clock = new WrenchOriginalClock(data, true);
            var state = new WrenchActionState();
            assertTrue(state.start(1, WrenchActionState.Intent.SLAM, 0,
                    new WrenchActionState.Eligibility(true, true, true, false, 4, 1.166, false, true),
                    ActionVector.ZERO, new ActionVector(0, 0, 1)));
            double height = 0, velocity = 0.42;
            for (int i = 0; i < 4; i++) { height += velocity; velocity = (velocity - 0.08) * 0.98; }
            WrenchSlamSampling.Actor actor = new WrenchSlamSampling.Actor(new ActionVector(0, height, 0),
                    yaw, 1, false, false, false);
            var initial = clock.initial();
            var previous = WrenchSlamSampling.at(rig, 43, 10, initial, actor);
            AABB nativeBox = dimensions.makeBoundingBox(yaw == 0 ? new Vec3(0, 0, 0.8)
                    : new Vec3(-0.8, 0, 0));
            var target = box(nativeBox);
            for (int tick = 1; tick < 70; tick++) {
                boolean grounded = height <= 0;
                clock.advance(tick, state.presentation().phase(), grounded, velocity, Math.max(0, height));
                var now = new WrenchSlamSampling.Actor(new ActionVector(0, height, 0),
                        yaw, 1, false, false, false);
                var poses = WrenchSlamSampling.sample(rig, 43, 10, clock.samples(), actor, now);
                assertEquals(3, poses.size());
                state.tickSources(tick, true, poses.stream().map(p -> new WrenchActionState.SourceFrame(
                        p.frame(), p.hand(), p.rotation(), p.grounded())).toList(), (a, b) -> null);
                for (var pose : poses) {
                    if (WrenchSlamSampling.intersects(previous, pose, target)) {
                        contactFrames++;
                        if (grounded) landingFrames++;
                    }
                    var far = new WrenchContact.Box(new ActionVector(5, 0, 5), new ActionVector(5.6, 1.95, 5.6));
                    assertFalse(WrenchSlamSampling.intersects(previous, pose, far));
                    previous = pose;
                }
                actor = now;
                if (state.presentation().phase() == WrenchActionPresentation.Phase.IDLE) break;
                if (!grounded) {
                    velocity = WrenchSlamMotion.velocity(velocity, tick, height);
                    height = Math.max(0, height + velocity);
                    velocity = height == 0 ? 0 : (velocity - 0.08) * 0.98;
                }
            }
            System.out.println("Native zombie yaw=" + yaw + " sourceContacts=" + contactFrames
                    + " landingWindowContacts=" + landingFrames);
            assertTrue(contactFrames > 0, "Ordinary jump -> visible front contact must resolve");
        }
    }

    @Test void realSlamTipHasSmallSideHeightEdgeContactsNotAnInflatedCornerOrRadialBlast() {
        int activeSamples = 0;
        var clock = new WrenchOriginalClock(data, true);
        var actor = new WrenchSlamSampling.Actor(ActionVector.ZERO, 90, 1, false, false, false);
        for (int tick = 1; tick <= 25; tick++) {
            clock.advance(tick, WrenchActionPresentation.Phase.SLAM_DESCENT, true, -0.2, 0);
            for (var source : clock.samples()) {
                var pose = WrenchSlamSampling.at(rig, 43, 10, source, actor);
                if (!pose.active()) continue;
                activeSamples++;
                ActionVector tip = WrenchSlamSampling.tip(pose);
                for (ActionVector offset : List.of(new ActionVector(0.469, 0, 0),
                        new ActionVector(-0.469, 0, 0), new ActionVector(0, 0.469, 0),
                        new ActionVector(0, -0.469, 0), new ActionVector(0, 0, 0.469))) {
                    ActionVector edge = tip.add(offset);
                    assertTrue(WrenchSlamSampling.spheres(pose, new WrenchContact.Box(edge, edge)));
                }
                for (ActionVector offset : List.of(new ActionVector(0.471, 0, 0),
                        new ActionVector(0, 0.471, 0), new ActionVector(0.34, 0.34, 0),
                        new ActionVector(0, 0, 4))) {
                    ActionVector outside = tip.add(offset);
                    assertFalse(WrenchSlamSampling.spheres(pose,
                            new WrenchContact.Box(outside, outside)));
                }
            }
        }
        assertTrue(activeSamples > 0);
        System.out.println("Real slam tip side/height/edge/noncontact samples=" + activeSamples);
    }

    @Test void measuredBaselineTorsoOnlyTransformSeparatesFlatNativeLegsWhileSharedRootDoesNot() {
        // Reproduce cd07bdf's torso delta/translation, not a second production rig.
        var hero = data.hero();
        int[] rootChain = hero.attachmentChain(9), torsoChain = hero.attachmentChain(11);
        int root = rootChain[rootChain.length - 1], torso = torsoChain[torsoChain.length - 1];
        float[] inverse = hero.skeleton().inverseBind();
        Matrix4f bindBody = matrix(inverse, root).mul(matrix(inverse, torso).invert());
        var basis = new Quaternionf().rotationAxis((float) Math.PI,
                (float) Math.sqrt(0.5), 0, (float) -Math.sqrt(0.5));
        double oldMax = 0, correctedMax = 0;
        var nativeModel = model(false, false);
        Vector3f waist = part(nativeModel.body).transformPosition(new Vector3f(
                nativeModel.rightLeg.x / 16, cube(nativeModel.body).maxY / 16, 0));
        Vector3f leg = part(nativeModel.rightLeg).transformPosition(new Vector3f(
                0, cube(nativeModel.rightLeg).minY / 16, 0));
        for (int sequence : new int[] {26, 43}) for (double seconds : times(sequence)) {
            float[] palette = com.github.giannigr.racmc.asset.Rac1AnimationEvaluator.evaluateChain(
                    hero.skeleton(), data.heroSequence(sequence).orElseThrow(), (float) seconds);
            Matrix4f body = matrix(palette, root).invert().mul(matrix(palette, torso));
            Quaternionf delta = body.getUnnormalizedRotation(new Quaternionf()).normalize()
                    .mul(bindBody.getUnnormalizedRotation(new Quaternionf()).normalize().conjugate());
            Quaternionf rotation = new Quaternionf(basis).mul(delta)
                    .mul(new Quaternionf(basis).conjugate()).normalize();
            Vector3f translation = basis.transform(body.getTranslation(new Vector3f())
                    .sub(bindBody.getTranslation(new Vector3f())));
            // Baseline translated/rotated only torso and arms. Flat native legs stayed unchanged.
            Vector3f oldWaist = new Matrix4f().translation(translation).rotate(rotation)
                    .transformPosition(new Vector3f(waist));
            oldMax = Math.max(oldMax, oldWaist.distance(leg) * 0.9375);
            Matrix4f corrected = rig.sample(sequence, seconds, sequence == 43 ? 10 : 6,
                    0, false, false, 1).root();
            correctedMax = Math.max(correctedMax, corrected.transformPosition(new Vector3f(waist))
                    .distance(corrected.transformPosition(new Vector3f(leg))) * 0.9375);
        }
        assertTrue(oldMax > 0.1, "Baseline must reproduce a material disconnected waist");
        assertEquals(0, correctedMax);
        System.out.println("Baseline torso-only waist separation=" + oldMax
                + " blocks; corrected shared-root separation=" + correctedMax);
    }

    @Test void snapshotRestoresEveryNativeAndWearPartAndScaleWithoutLayerRootLeak() {
        var model = model(true, false);
        var parts = List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg, model.jacket, model.rightSleeve, model.leftSleeve,
                model.rightPants, model.leftPants);
        var before = parts.stream().map(WrenchCorrectionRealDataTest::part).toList();
        var saved = WrenchModelPoseSnapshot.capture(model);
        for (var part : parts) { part.x += 4; part.zRot += 1; part.xScale = 3; }
        saved.restore();
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(before.get(i).equals(part(parts.get(i)), 0));
            assertEquals(1, parts.get(i).xScale);
        }
    }

    private static PlayerModel<?> model(boolean slim, boolean crouch) {
        var model = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(
                CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
        if (crouch) {
            model.body.xRot = 0.5F; model.body.y = 3.2F; model.head.y = 4.2F;
            model.rightArm.y = model.leftArm.y = 5.2F;
            model.rightLeg.y = model.leftLeg.y = 12.2F;
            model.rightLeg.z = model.leftLeg.z = 4;
        }
        return model;
    }
    private static ModelPart.Cube cube(ModelPart part) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        part.visit(new PoseStack(), (pose, name, index, cube) -> cubes.add(cube));
        return cubes.getFirst();
    }
    private static Matrix4f part(ModelPart part) {
        PoseStack stack = new PoseStack(); part.translateAndRotate(stack);
        return new Matrix4f(stack.last().pose());
    }
    private static void apply(ModelPart part, WrenchSteveRig.Part pose) {
        part.setPos(pose.x(), pose.y(), pose.z()); part.setRotation(pose.xRot(), pose.yRot(), pose.zRot());
    }
    private static List<Double> times(int sequence) {
        List<Double> times = new ArrayList<>();
        float[] keys = data.heroSequence(sequence).orElseThrow().times();
        for (int i = 0; i < keys.length; i++) {
            times.add((double) keys[i]);
            if (i > 0) times.add((keys[i] + keys[i - 1]) / 2.0);
        }
        return times;
    }
    private static WrenchContact.Box box(AABB b) {
        return new WrenchContact.Box(new ActionVector(b.minX, b.minY, b.minZ),
                new ActionVector(b.maxX, b.maxY, b.maxZ));
    }
    private static Matrix4f matrix(float[] palette, int joint) {
        return new Matrix4f().set(Arrays.copyOfRange(palette, joint * 16, joint * 16 + 16));
    }
}
