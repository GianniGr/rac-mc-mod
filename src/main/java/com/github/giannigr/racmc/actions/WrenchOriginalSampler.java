package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.serverassets.Rac1WrenchData;
import com.github.giannigr.racmc.serverassets.Rac1WrenchServerData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
            clock = new Clock(action.serial(), new WrenchOriginalClock(data, slam));
            clocks.put(player.getUUID(), clock);
        }
        double clearance = WrenchActions.groundClearance(player);
        var result = elapsedTicks == 0 ? clock.clock.initial()
                : clock.clock.advance(elapsedTicks, action.phase(), player.onGround(),
                        player.getDeltaMovement().y, clearance,
                        action.phase() == WrenchActionPresentation.Phase.CATCH
                                || (!action.heldVisible() && action.wrenchSequence() == 1));
        int child = slam ? 10 : action.heldVisible() ? 1 : 6;
        var pose = rig.sample(slam ? 43 : 26, result.frame().seconds(), child,
                slam ? result.frame().wrenchSeconds() : 0,
                player.getMainArm() == HumanoidArm.LEFT, slim, result.frame().bodyBlendWeight());
        Matrix4f world = WrenchSteveRig.world(0, 0, 0, player.yBodyRot, player.getScale(),
                player.isCrouching());
        ActionVector hand = worldPosition(player, new Matrix4f(world).mul(pose.socket()));
        ActionVector head = worldPosition(player, new Matrix4f(world).mul(pose.head()));
        var q = new Matrix4f(world).mul(pose.socket())
                .getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
        // Source root calibrated to native Steve foot origin, then original gravity-relative -0.5.
        ActionVector body = WrenchActions.vector(player.position()).add(
                new ActionVector(0, -0.5 * player.getScale(), 0));
        return new WrenchActions.Sample(result.frame(), hand, head, body,
                result.active(), result.bodyContact(), new ActionRotation(q.x, q.y, q.z, q.w));
    }

    private static ActionVector worldPosition(ServerPlayer player, Matrix4f transform) {
        Vector3f local = transform.getTranslation(new Vector3f());
        return new ActionVector(player.getX() + local.x, player.getY() + local.y,
                player.getZ() + local.z);
    }

    @Override
    public void forget(UUID player) { clocks.remove(player); }

    private record Clock(long serial, WrenchOriginalClock clock) {}
}
