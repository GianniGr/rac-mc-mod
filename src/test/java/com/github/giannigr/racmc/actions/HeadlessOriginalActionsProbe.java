package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.cache.Rac1CacheLocation;
import com.github.giannigr.racmc.iso.Rac1MediaConfig;
import com.github.giannigr.racmc.serverassets.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Real local provider -> original clock -> rigid Steve/socket -> pure action, without MC jars. */
public final class HeadlessOriginalActionsProbe {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.minecraft.server.MinecraftServer");
            throw new AssertionError("Minecraft must not be present in this headless process");
        } catch (ClassNotFoundException expected) {}
        Path config = Files.createDirectories(Path.of(args[2]).resolve("local-config"));
        new Rac1MediaConfig(Path.of(args[0]), "", 0, "", 0).write(Rac1MediaConfig.fileIn(config));
        Files.writeString(Rac1CacheLocation.fileIn(config), "directory=" + args[1] + "\n");
        try (var provider = Rac1WrenchDataProvider.open(config, false)) {
            long deadline = System.nanoTime() + 45_000_000_000L;
            while (provider.data().isEmpty() && System.nanoTime() < deadline
                    && provider.status().state() != Rac1WrenchDataStatus.State.FAILED) {
                Thread.sleep(10);
            }
            var data = provider.data().orElseThrow(() -> new AssertionError(provider.status()));
            require(provider.status().origin() == Rac1WrenchDataStatus.Origin.SHARED_CACHE,
                    "Must reuse the claimed validated original cache read-only");
            var rig = new WrenchSteveRig(data);
            for (boolean slim : new boolean[] {false, true}) {
                for (boolean left : new boolean[] {false, true}) {
                    for (double seconds = 0.266667; seconds <= 0.80001; seconds += 0.025) {
                        var pose = rig.sample(43, seconds, 10, seconds, left, slim, 1);
                        require(pose.socket().isFinite() && pose.head().isFinite(),
                                "Original calibrated projected poses must stay finite");
                        require(pose.socket().getTranslation(new Vector3f()).length() < 4,
                                "Actual rigid Steve socket must stay attached to short native arm");
                    }
                    throwAction(data, rig, left, slim);
                    slamAction(data, rig, left, slim);
                }
            }
            System.out.println("PASS real local data: dynamic validated shared provider; raw event "
                    + "clock release/catch; body43/child10 source sampling; native rigid arms/socket "
                    + "all left/right/default/slim combinations; slam movement/contact/recovery; "
                    + "cosmetic out/spin/moving-hand catch; no MC/client jars or assets on network");
        }
    }

    private static void throwAction(Rac1WrenchData data, WrenchSteveRig rig, boolean left, boolean slim) {
        var state = new WrenchActionState();
        var clock = new WrenchOriginalClock(data, false);
        require(state.start(1, WrenchActionState.Intent.THROW, 0,
                new WrenchActionState.Eligibility(true, true, true, true, 0, 0, false, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)), "Accepted cosmetic throw");
        int releases = 0, releaseTick = 0;
        boolean detached = false, caught = false;
        for (int tick = 1; tick < 100; tick++) {
            var view = state.presentation();
            var result = clock.advance(tick, view.phase(), true, 0, 0,
                    view.phase() == WrenchActionPresentation.Phase.CATCH
                            || (!view.heldVisible() && view.wrenchSequence() == 1));
            var frame = result.frame();
            if (frame.release()) { releases++; releaseTick = tick; }
            var pose = rig.sample(26, frame.seconds(), view.wrenchSequence(),
                    frame.wrenchSeconds(), left, slim, frame.bodyBlendWeight());
            var hand = pose.socket().getTranslation(new Vector3f());
            var q = pose.socket().getUnnormalizedRotation(new Quaternionf()).normalize();
            state.tick(tick, true, true, new ActionVector(hand.x + tick * 0.01, hand.y, hand.z),
                    new ActionRotation(q.x, q.y, q.z, q.w), frame, (a, b) -> null);
            var current = state.presentation();
            detached |= !current.heldVisible();
            caught |= current.phase() == WrenchActionPresentation.Phase.CATCH;
            require(!state.claimTarget(java.util.UUID.randomUUID()), "No cosmetic throw damage");
            if (current.phase() == WrenchActionPresentation.Phase.IDLE) {
                require(releases == 1 && releaseTick >= 4 && releaseTick <= 12,
                        "One source-header33 release, not key33/tick33");
                require(detached && caught, "Detached world proxy must return and catch");
                require(tick < 99, "Catch tail finishes before safety timeout");
                return;
            }
        }
        throw new AssertionError("Original cosmetic throw did not finish");
    }

    private static void slamAction(Rac1WrenchData data, WrenchSteveRig rig, boolean left, boolean slim) {
        var clock = new WrenchOriginalClock(data, true);
        var state = new WrenchActionState();
        require(state.start(1, WrenchActionState.Intent.SLAM, 0,
                new WrenchActionState.Eligibility(true, true, true, false, 4, 1.2, false, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)), "Accepted original-style slam");
        double height = 1.2, velocity = 0.1;
        int active = 0, descending = 0;
        for (int tick = 1; tick < 80; tick++) {
            boolean grounded = height <= 0;
            var result = clock.advance(tick, state.presentation().phase(), grounded, velocity,
                    Math.max(0, height));
            var frame = result.frame();
            if (result.active()) active++;
            if (result.bodyContact()) descending++;
            var pose = rig.sample(43, frame.seconds(), 10, frame.wrenchSeconds(),
                    left, slim, frame.bodyBlendWeight());
            require(pose.socket().isFinite() && pose.head().isFinite(), "Source-driven slam anchors");
            state.tick(tick, true, grounded, ActionVector.ZERO, frame, (a, b) -> null);
            if (state.presentation().phase() == WrenchActionPresentation.Phase.IDLE) {
                require(active > 0 && descending > 0, "Original contact windows actually exercised");
                require(grounded && tick < 79, "Body recovery, not arbitrary end-of-clip air cancel");
                return;
            }
            if (!grounded) {
                velocity = WrenchSlamMotion.velocity(velocity, tick, height);
                height = Math.max(0, height + velocity);
                velocity = height == 0 ? 0 : (velocity - 0.08) * 0.98;
            }
        }
        throw new AssertionError("Original slam did not finish");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
