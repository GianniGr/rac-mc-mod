package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.item.Rac1WrenchBlockPolicy;
import com.github.giannigr.racmc.item.Rac1WrenchEvents;
import com.github.giannigr.racmc.item.Rac1WrenchCrateHits;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local contact -> P27 policy -> native validated harvest -> P27's ONE crate adapter. */
final class WrenchSlamBlocks {
    private WrenchSlamBlocks() {}

    static void sphere(ActionVector center, double radius, Set<BlockPos> contacts) {
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(center.x() - radius, center.y() - radius, center.z() - radius),
                BlockPos.containing(center.x() + radius, center.y() + radius, center.z() + radius))) {
            var min = new ActionVector(pos.getX(), pos.getY(), pos.getZ());
            if (WrenchContact.sphereIntersects(center, radius,
                    new WrenchContact.Box(min, min.add(new ActionVector(1, 1, 1))))) {
                contacts.add(pos.immutable());
            }
        }
    }

    static Set<BlockPos> swept(ActionVector from, ActionVector to, double radius) {
        Set<BlockPos> contacts = new java.util.LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(
                Math.min(from.x(), to.x()) - radius, Math.min(from.y(), to.y()) - radius,
                Math.min(from.z(), to.z()) - radius), BlockPos.containing(
                Math.max(from.x(), to.x()) + radius, Math.max(from.y(), to.y()) + radius,
                Math.max(from.z(), to.z()) + radius))) {
            var min = new ActionVector(pos.getX(), pos.getY(), pos.getZ());
            if (Double.isFinite(WrenchContact.sweptSphere(new WrenchContact.Segment(from, to),
                    radius, new WrenchContact.Box(min, min.add(new ActionVector(1, 1, 1)))))) {
                contacts.add(pos.immutable());
            }
        }
        return contacts;
    }

    static List<Candidate> snapshot(ServerPlayer player, Set<BlockPos> contacts) {
        List<Candidate> result = new ArrayList<>();
        for (BlockPos pos : contacts) {
            if (!player.level().hasChunkAt(pos)) continue;
            var state = player.level().getBlockState(pos);
            var entity = player.level().getBlockEntity(pos);
            var policy = Rac1WrenchEvents.action(state);
            if (entity != null && (policy == Rac1WrenchBlockPolicy.Action.INSTANT_CRATE
                    || policy == Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED)) {
                result.add(new Candidate(pos, state, entity));
            }
        }
        return result;
    }

    static void deliver(ServerPlayer player, WrenchActionState action, List<Candidate> contacts,
            net.minecraft.world.item.ItemStack expectedHeld, boolean sourceWindow) {
        deliver(player, action, contacts, expectedHeld, sourceWindow, null);
    }

    static void deliver(ServerPlayer player, WrenchActionState action, List<Candidate> contacts,
            net.minecraft.world.item.ItemStack expectedHeld, boolean sourceWindow,
            WrenchActions.ThrowReach ranged) {
        var level = player.serverLevel();
        for (Candidate candidate : contacts) {
            BlockPos pos = candidate.pos;
            if (!player.isAlive() || player.getMainHandItem() != expectedHeld) return;
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !WrenchActions.withinBlockReach(player, pos, ranged) || !level.mayInteract(player, pos)
                    || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
                continue;
            }
            var state = level.getBlockState(pos);
            if (state != candidate.state || level.getBlockEntity(pos) != candidate.entity) continue;
            var policy = Rac1WrenchEvents.action(state);
            if (policy == Rac1WrenchBlockPolicy.Action.NORMAL
                    || policy == Rac1WrenchBlockPolicy.Action.PROTECTED
                    || policy == Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED
                            && !Rac1WrenchCrateHits.hasContactHandler()) continue;
            net.minecraft.core.Direction face;
            if (ranged == null) {
                var hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)
                        || !action.claimBlock(pos.asLong(), sourceWindow)) continue;
                face = hit.getDirection();
            } else {
                if (!action.claimThrownCrate(candidate.entity)) continue;
                var delta = ranged.path().from().subtract(WrenchActions.vector(Vec3.atCenterOf(pos)));
                face = net.minecraft.core.Direction.getNearest((float) delta.x(),
                        (float) delta.y(), (float) delta.z());
            }
            var entity = level.getBlockEntity(pos);
            var held = player.getMainHandItem();
            if (!WrenchActions.blockContactAllowed(player, pos, face)) continue;
            if (level.getBlockState(pos) != state || level.getBlockEntity(pos) != entity
                    || player.getMainHandItem() != held || !WrenchActions.isWrench(held)) continue;
            // This fires the real BreakEvent and onDestroyedByPlayer, which invokes P27's
            // Rac1WrenchCrateHits seam. No direct removal, reward roll, fuse or synthetic ACK.
            if (policy == Rac1WrenchBlockPolicy.Action.INSTANT_CRATE) {
                WrenchActions.harvestContact(player, candidate, ranged);
            } else {
                WrenchActions.explosiveContact(player, candidate, ranged);
            }
        }
    }

    record Candidate(BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity entity) {}
}
