package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.loot.Rac1BoltEntity;
import com.github.giannigr.racmc.serverassets.Rac1WrenchServerData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Original 60Hz sampled enemy spheres; swept ordinary-crate contact uses the same posed anchors. */
final class WrenchThrowContacts {
    private WrenchThrowContacts() {}

    static void deliver(ServerPlayer owner, WrenchActionState action, WrenchActions.Sample sample,
            WrenchHitAudit audit) {
        var data = Rac1WrenchServerData.data(owner.server).orElse(null);
        var steps = action.beginFlightContacts(owner.level().getGameTime());
        if (steps.isEmpty()) return;
        try {
            if (data != null) deliverSteps(owner, action, sample, audit, data, steps);
        } finally {
            action.endFlightContacts();
        }
    }

    private static void deliverSteps(ServerPlayer owner, WrenchActionState action,
            WrenchActions.Sample sample, WrenchHitAudit audit,
            com.github.giannigr.racmc.serverassets.Rac1WrenchData data,
            List<WrenchActionState.FlightStep> steps) {
        var expectedHeld = owner.getMainHandItem();
        ActionVector player = WrenchActions.vector(owner.position());
        double handOffset = sample.hand().subtract(player).length();
        for (var step : steps) {
            if (!owner.isAlive() || owner.getMainHandItem() != expectedHeld
                    || !WrenchActions.isWrench(expectedHeld)) return;
            ActionVector head = WrenchThrowGeometry.anchor(data.wrench(), step, owner.getScale(), 1, false);
            ActionVector previousHead = WrenchThrowGeometry.anchor(data.wrench(), step, owner.getScale(), 1, true);
            double reach = WrenchThrowGeometry.outwardTravelBudget() + handOffset
                    + Math.max(head.subtract(step.to()).length(),
                            previousHead.subtract(step.from()).length()) + 0.4;
            if (!authorized(player, step, reach) || !loaded(owner, head) || !loaded(owner, previousHead)) {
                audit.eligibility++;
                continue;
            }
            if (step.returnOrigin() != null && WrenchActions.valid(owner)) {
                Rac1BoltEntity.attractFromReturningWrench(owner, WrenchActions.vec(step.returnOrigin()));
            }
            audit.samples++;
            var blocks = WrenchSlamBlocks.snapshot(owner,
                    WrenchSlamBlocks.swept(previousHead, head, 0.4));
            var selection = WrenchThrowGeometry.query(head,
                    () -> WrenchThrowGeometry.anchor(data.wrench(), step, owner.getScale(), 0, false),
                    center -> loaded(owner, center) ? sphere(owner, center) : List.of());
            ActionVector query = selection.center();
            List<LivingEntity> targets = selection.candidates();
            audit.candidates += targets.size();
            long sourceTime = owner.server.getTickCount() * 3L - 2 + step.substep();
            WrenchDamageDelivery.RECEIVERS.prune(sourceTime);
            for (LivingEntity target : targets) {
                if (!target.isAlive() || target.level() != owner.level() || !owner.canAttack(target)
                        || target instanceof Player other && !owner.canHarmPlayer(other)) {
                    audit.eligibility++;
                    continue;
                }
                if (!WrenchDamageDelivery.RECEIVERS.message(owner.getUUID(), target.getUUID(),
                        action.cometIdentity(), sourceTime)) { audit.dedup++; continue; }
                if (!WrenchActions.attackAllowed(owner, target)) { audit.protection++; continue; }
                if (owner.getMainHandItem() != expectedHeld || !target.isAlive()
                        || target.level() != owner.level() || !owner.canAttack(target)
                        || !WrenchActions.valid(owner) || !authorized(
                                WrenchActions.vector(owner.position()), step, reach)
                        || !WrenchContact.sphereIntersects(query, 0.4, box(target.getBoundingBox()))
                        || target instanceof Player other && !owner.canHarmPlayer(other)) continue;
                // No melee-range3 or blanket world-LOS gate: this is a bounded ranged source query.
                // Native armor, hurt immunity, hurt/protection events and PvP still own acceptance.
                if (WrenchDamageDelivery.hurt(owner, target, WrenchDamageDelivery.THROW_DAMAGE,
                        action.cometIdentity(), sourceTime)) audit.hits++;
                else audit.rejected++;
            }
            WrenchSlamBlocks.deliver(owner, action, blocks, expectedHeld, false,
                    new WrenchActions.ThrowReach(new WrenchContact.Segment(previousHead, head), reach));
        }
    }

    static boolean authorized(ActionVector owner, WrenchActionState.FlightStep step, double maximum) {
        return step.from().subtract(owner).length() <= maximum
                && step.to().subtract(owner).length() <= maximum;
    }

    static ActionVector surface(ServerPlayer owner, ActionVector from, ActionVector to) {
        double nearest = Double.POSITIVE_INFINITY;
        ActionVector cursor = from;
        var segment = new WrenchContact.Segment(from, to);
        for (int ignoredCrates = 0; ignoredCrates < 8; ignoredCrates++) {
            var hit = owner.level().clip(new ClipContext(WrenchActions.vec(cursor), WrenchActions.vec(to),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.MISS) break;
            var policy = com.github.giannigr.racmc.item.Rac1WrenchEvents.action(
                    owner.level().getBlockState(hit.getBlockPos()));
            if (policy == com.github.giannigr.racmc.item.Rac1WrenchBlockPolicy.Action.INSTANT_CRATE
                    || policy == com.github.giannigr.racmc.item.Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED) {
                var p = hit.getBlockPos();
                var cube = new WrenchContact.Box(new ActionVector(p.getX(), p.getY(), p.getZ()),
                        new ActionVector(p.getX() + 1, p.getY() + 1, p.getZ() + 1));
                double exit = 1 - WrenchContact.intersection(new WrenchContact.Segment(to, from), cube);
                if (!Double.isFinite(exit) || exit >= 1) break;
                cursor = from.lerp(to, Math.min(1, exit + 1.0e-5));
            } else {
                double length = to.subtract(from).length();
                nearest = length < 1.0e-9 ? 0
                        : WrenchActions.vector(hit.getLocation()).subtract(from).length() / length;
                break;
            }
        }
        AABB broad = new AABB(WrenchActions.vec(from), WrenchActions.vec(to)).inflate(0.001);
        for (var entity : owner.level().getEntitiesOfClass(LivingEntity.class, broad,
                e -> e != owner && e.isAlive() && !e.isSpectator() && e.isPickable())) {
            nearest = Math.min(nearest, WrenchContact.intersection(segment, box(entity.getBoundingBox())));
        }
        return Double.isFinite(nearest) ? from.lerp(to, Math.clamp(nearest, 0, 1)) : null;
    }

    private static List<LivingEntity> sphere(ServerPlayer owner, ActionVector center) {
        AABB bounds = new AABB(center.x() - 0.4, center.y() - 0.4, center.z() - 0.4,
                center.x() + 0.4, center.y() + 0.4, center.z() + 0.4);
        return owner.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator()
                        && WrenchContact.sphereIntersects(center, 0.4, box(entity.getBoundingBox())));
    }

    private static boolean loaded(ServerPlayer owner, ActionVector point) {
        return owner.level().hasChunkAt(BlockPos.containing(point.x(), point.y(), point.z()));
    }

    private static WrenchContact.Box box(AABB box) {
        return new WrenchContact.Box(new ActionVector(box.minX, box.minY, box.minZ),
                new ActionVector(box.maxX, box.maxY, box.maxZ));
    }
}
