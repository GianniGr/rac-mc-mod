package com.github.giannigr.racmc.actions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Server-only local-contact delivery; no terrain/crate blast or vanilla critical/sweep stacking. */
final class WrenchSlamContacts {
    private WrenchSlamContacts() {}

    static void deliver(ServerPlayer player, WrenchActionState state,
            WrenchActions.Sample previous, WrenchActions.Sample current) {
        if (previous == null) return;
        var expectedHeld = player.getMainHandItem();
        // Native reach is a hard ceiling, even when a malformed sampler supplies distant anchors.
        double reach = player.entityInteractionRange();
        AABB search = player.getBoundingBox().inflate(reach);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class,
                search, target -> target != player && target.isAlive() && !target.isSpectator());
        Set<LivingEntity> contacts = new LinkedHashSet<>();
        Set<net.minecraft.core.BlockPos> blocks = new LinkedHashSet<>();
        // Five sampled segments stop at their nearest entity or terrain obstruction.
        for (WrenchContact.Segment segment : current.active()
                ? WrenchContact.sweep(previous.hand(), previous.head(), current.hand(), current.head())
                : List.<WrenchContact.Segment>of()) {
            var wallHit = player.level().clip(new net.minecraft.world.level.ClipContext(
                    WrenchActions.vec(segment.from()), WrenchActions.vec(segment.to()),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            double wall = wallHit.getLocation().distanceTo(WrenchActions.vec(segment.from()));
            double length = segment.to().subtract(segment.from()).length();
            double nearest = wallHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    ? Double.POSITIVE_INFINITY : length == 0 ? 0 : wall / length;
            LivingEntity first = null;
            for (LivingEntity target : candidates) {
                double fraction = WrenchContact.intersection(segment, box(target.getBoundingBox()));
                if (fraction < nearest) {
                    nearest = fraction;
                    first = target;
                }
            }
            if (first != null) contacts.add(first);
            else if (wallHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                blocks.add(wallHit.getBlockPos().immutable());
            }
        }
        ActionVector tip = current.head().subtract(current.head().subtract(current.hand())
                .normalized().multiply(0.085));
        if (current.active()) WrenchSlamBlocks.sphere(tip, 0.47, blocks);
        if (current.descendingBody() && player.getDeltaMovement().y < 0 && !player.onGround()) {
            WrenchSlamBlocks.sphere(current.bodyContact(), 0.4, blocks);
        }
        for (LivingEntity target : candidates) {
            WrenchContact.Box box = box(target.getBoundingBox());
            if ((current.active() && WrenchContact.sphereIntersects(tip, 0.47, box))
                    || (current.descendingBody() && player.getDeltaMovement().y < 0
                    && !player.onGround()
                    && WrenchContact.sphereIntersects(current.bodyContact(), 0.4, box))) {
                contacts.add(target);
            }
        }
        var blockContacts = WrenchSlamBlocks.snapshot(player, blocks);
        for (LivingEntity target : contacts) {
            if (player.getMainHandItem() != expectedHeld || !player.isAlive()) return;
            if (!player.canInteractWithEntity(target, 0) || !player.hasLineOfSight(target)
                    || !player.canAttack(target)
                    || (target instanceof Player other && !player.canHarmPlayer(other))) continue;
            if (!state.claimTarget(target.getUUID())) continue;
            if (!WrenchActions.attackAllowed(player, target)) continue;
            if (!target.isAlive() || target.level() != player.level()
                    || player.getMainHandItem() != expectedHeld
                    || !player.canInteractWithEntity(target, 0) || !player.hasLineOfSight(target)
                    || !player.canAttack(target)
                    || (target instanceof Player other && !player.canHarmPlayer(other))) continue;
            // Normal native mitigation/invulnerability remains intact; never zero hurt timers.
            target.hurt(player.damageSources().playerAttack(player), WrenchActionState.SLAM_DAMAGE);
        }
        WrenchSlamBlocks.deliver(player, state, blockContacts, expectedHeld);
    }

    private static WrenchContact.Box box(AABB box) {
        return new WrenchContact.Box(new ActionVector(box.minX, box.minY, box.minZ),
                new ActionVector(box.maxX, box.maxY, box.maxZ));
    }
}
