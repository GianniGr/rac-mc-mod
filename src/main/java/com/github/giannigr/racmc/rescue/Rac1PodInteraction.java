package com.github.giannigr.racmc.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Every native hull click resolves to the same loaded natural marker. */
final class Rac1PodInteraction {
    private Rac1PodInteraction() {}

    static InteractionResult use(BlockState state, Level level, BlockPos clicked, Player player,
                                 BlockHitResult hit, int part) {
        if (level.dimension() != Level.OVERWORLD || player.isSpectator() || !player.isAlive()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)
                || user.serverLevel() != server || user.hasDisconnected()
                || server.getServer().getPlayerList().getPlayer(user.getUUID()) != user
                || !server.getServer().isSameThread()) return InteractionResult.FAIL;
        var anchor = part == 0 ? clicked : Rac1PodFootprint.partAnchor(clicked, part);
        if (anchor == null || !server.getWorldBorder().isWithinBounds(clicked)
                || !server.getWorldBorder().isWithinBounds(anchor)
                || !server.mayInteract(user, clicked) || !server.mayInteract(user, anchor)) {
            return feedback(user, "not_allowed", "You can't use this pod here.");
        }
        var chunk = server.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(clicked.getX()), SectionPos.blockToSectionCoord(clicked.getZ()));
        if (chunk == null || chunk.getBlockState(clicked) != state) return InteractionResult.FAIL;
        var parent = chunk.getBlockState(anchor);
        boolean full = Rac1PodFootprint.full(parent);
        if (!(parent.getBlock() instanceof Rac1PodBlock)
                || !(chunk.getBlockEntity(anchor) instanceof Rac1PodMarker marker)
                || marker.isRemoved() || !marker.isNatural()
                || (part != 0 && !full) || (full && !Rac1PodFootprint.complete(chunk, anchor))) {
            return feedback(user, "inert", "This isn't an intact natural crash pod.");
        }
        if (!user.canInteractWithBlock(clicked, 0) || !hasLoadedSight(server, user, clicked, hit)) {
            return feedback(user, "approach", "Move closer and look directly at the hull.");
        }
        if (marker.isClaimed()) return feedback(user, "empty", "This ship has already been rescued.");
        if (!marker.beginClaim()) return feedback(user, "rescue_unavailable", "Rescue is unavailable right now.");
        try {
            return switch (Rac1PodProgression.rescue(user, () -> {
                marker.commitClaim();
                // This sends only the shared wreck state, never which player received a grant.
                server.sendBlockUpdated(anchor, parent, parent, Block.UPDATE_CLIENTS);
            })) {
                case NEWLY_UNLOCKED -> {
                    user.displayClientMessage(Component.translatableWithFallback(
                            "message.racmc.clank_rescued", "Clank rescued!"), true);
                    yield InteractionResult.CONSUME;
                }
                case ALREADY_UNLOCKED -> feedback(user, "already_rescued", "You've already rescued Clank.");
                case DENIED -> feedback(user, "rescue_unavailable", "Rescue is unavailable right now.");
            };
        } finally {
            marker.finishClaim();
        }
    }

    static Vec3 rayEnd(Vec3 eye, BlockPos clicked, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(clicked)) return null;
        Vec3 at = hit.getLocation();
        if (!(Math.abs(at.x - clicked.getX() - .5) <= .501
                && Math.abs(at.y - clicked.getY() - .5) <= .501
                && Math.abs(at.z - clicked.getZ() - .5) <= .501)) return null;
        Vec3 ray = at.subtract(eye);
        double length = ray.length();
        // Cap work even against very large modded reach. Extend slightly past the reported face.
        return length > 1e-6 && length <= 6 ? at.add(ray.scale(.02 / length)) : null;
    }

    private static boolean hasLoadedSight(ServerLevel level, ServerPlayer player, BlockPos clicked,
                                          BlockHitResult hit) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = rayEnd(eye, clicked, hit);
        if (target == null) return false;
        int minX = SectionPos.blockToSectionCoord(Mth.floor(Math.min(eye.x, target.x)) - 1);
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(Math.max(eye.x, target.x)) + 1);
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(Math.min(eye.z, target.z)) - 1);
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(Math.max(eye.z, target.z)) + 1);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        var sight = level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return sight.getType() == HitResult.Type.BLOCK && sight.getBlockPos().equals(clicked);
    }

    private static InteractionResult feedback(ServerPlayer user, String key, String fallback) {
        user.displayClientMessage(Component.translatableWithFallback("message.racmc.crashed_pod." + key, fallback), true);
        // Consume this block use instead of accidentally using the held item on the wreck.
        return InteractionResult.CONSUME;
    }
}
