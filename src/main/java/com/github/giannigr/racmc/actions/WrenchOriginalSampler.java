package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.serverassets.Rac1WrenchData;
import com.github.giannigr.racmc.serverassets.Rac1WrenchServerData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;

/** Production sampler: dynamically queries the lawful server-local provider, never client data. */
public final class WrenchOriginalSampler implements WrenchActions.Sampler {
    private final Map<UUID, Clock> clocks = new HashMap<>();
    private Rac1WrenchData data;
    private WrenchSteveRig rig;
    private Rac1WrenchData failed;

    @Override
    public boolean ready(ServerPlayer player) {
        var available = Rac1WrenchServerData.data(player.server).orElse(null);
        if (available == null || available == failed) return false;
        if (available != data) {
            try {
                rig = new WrenchSteveRig(available);
                new WrenchOriginalClock(available, true);
                new WrenchOriginalClock(available, false);
                WrenchActionGeometry.anchor(available.wrench(), 6, 0, 1);
                data = available;
                clocks.clear();
            } catch (IllegalArgumentException invalid) {
                failed = available;
                RacMcMod.LOGGER.error("Managed wrench actions disabled: original rig calibration failed",
                        invalid);
                return false;
            }
        }
        return true;
    }

    @Override
    public WrenchActions.Sample sample(ServerPlayer player, WrenchActionPresentation action,
            int elapsedTicks, boolean slim) {
        if (!ready(player)) return null;
        try {
            return sampleReady(player, action, elapsedTicks, slim);
        } catch (RuntimeException invalid) {
            failed = data;
            clocks.clear();
            RacMcMod.LOGGER.error("Managed wrench actions disabled: invalid original pose sample",
                    invalid);
            return null;
        }
    }

    private WrenchActions.Sample sampleReady(ServerPlayer player, WrenchActionPresentation action,
            int elapsedTicks, boolean slim) {
        boolean slam = action.bodySequence() == 43
                || action.phase() == WrenchActionPresentation.Phase.IDLE;
        Clock clock = clocks.get(player.getUUID());
        if (clock == null || clock.serial != action.serial()) {
            clock = new Clock(action.serial(), new WrenchOriginalClock(data, slam), actor(player, slim));
            clocks.put(player.getUUID(), clock);
        }
        double clearance = WrenchActions.groundClearance(player);
        var result = elapsedTicks == 0 ? clock.clock.initial()
                : clock.clock.advance(elapsedTicks, action.phase(), player.onGround(),
                        player.getDeltaMovement().y, clearance,
                        action.phase() == WrenchActionPresentation.Phase.CATCH
                                || (!action.heldVisible() && action.wrenchSequence() == 1));
        int child = slam ? 10 : action.heldVisible() ? 1 : 6;
        var current = actor(player, slim);
        var poses = WrenchSlamSampling.sample(rig, slam ? 43 : 26, child,
                elapsedTicks == 0 ? java.util.List.of(result) : clock.clock.samples(),
                clock.actor, current);
        clock.actor = current;
        var pose = poses.getLast();
        return new WrenchActions.Sample(result.frame(), pose.hand(), pose.head(), pose.body(),
                result.active(), result.bodyContact(), pose.rotation(), poses);
    }

    private static WrenchSlamSampling.Actor actor(ServerPlayer player, boolean slim) {
        return new WrenchSlamSampling.Actor(WrenchActions.vector(player.position()), player.getYRot(),
                player.getScale(), player.isCrouching(), player.getMainArm() == HumanoidArm.LEFT, slim);
    }

    @Override
    public void forget(UUID player) { clocks.remove(player); }

    private static final class Clock {
        final long serial;
        final WrenchOriginalClock clock;
        WrenchSlamSampling.Actor actor;
        Clock(long serial, WrenchOriginalClock clock, WrenchSlamSampling.Actor actor) {
            this.serial = serial;
            this.clock = clock;
            this.actor = actor;
        }
    }
}
