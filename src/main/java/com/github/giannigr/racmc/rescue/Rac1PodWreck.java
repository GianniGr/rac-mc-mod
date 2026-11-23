package com.github.giannigr.racmc.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

/** One loaded hull, one native player removal callback, at most one recovered item. */
final class Rac1PodWreck {
    private Rac1PodWreck() {}

    static boolean breakable(BlockGetter view, BlockPos pos, int part) {
        var anchor = anchor(pos, part);
        var local = Rac1PodFootprint.loadedView(view, pos);
        if (anchor == null || local == null) return false;
        var marker = Rac1PodFootprint.markerAt(local, anchor);
        var parent = local.getBlockState(anchor);
        return marker != null && marker.canDismantle() && intact(local, anchor, parent, part);
    }

    static boolean remove(Level level, BlockPos pos, BlockState state, Player player, int part) {
        // Predict neither a nine-cell removal nor a denied break on the client.
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)
                || !server.getServer().isSameThread() || user.serverLevel() != server) return false;
        var local = Rac1PodFootprint.loadedView(level, pos);
        if (local == null || local.getBlockState(pos) != state || !allowed(server, user, pos)) return false;
        var anchor = anchor(pos, part);
        var marker = anchor == null ? null : Rac1PodFootprint.markerAt(local, anchor);
        var parent = anchor == null ? state : local.getBlockState(anchor);
        if (part != 0 && !Rac1PodFootprint.full(parent)) marker = null;
        if (marker != null && marker.isNatural() && !marker.isClaimed()) return false;
        if (marker == null || !marker.canDismantle() || !intact(local, anchor, parent, part)) {
            // Creative can clean up orphan/inert or damaged cells, never with a drop.
            return user.isCreative() && level.removeBlock(pos, false);
        }
        if (!marker.beginDismantle()) return false;
        try {
            int count = Rac1PodFootprint.full(parent) ? Rac1PodFootprint.CELLS.size() : 1;
            var air = Blocks.AIR.defaultBlockState();
            for (int i = 0; i < count; i++) {
                var cell = Rac1PodFootprint.CELLS.get(i).at(anchor);
                if (!allowed(server, user, cell)) return false;
                // Vanilla already fired this for the clicked cell. Protection mods may also veto any other cell.
                if (!cell.equals(pos) && CommonHooks.fireBlockBreak(server, user.gameMode.getGameModeForPlayer(),
                        user, cell, local.getBlockState(cell)).isCanceled()) return false;
            }
            if (Rac1PodFootprint.markerAt(local, anchor) != marker || local.getBlockState(pos) != state
                    || local.getBlockState(anchor) != parent || !intact(local, anchor, parent, part)) return false;
            // Remove the authority first; defer neighbor callbacks until the whole hull is gone.
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            if (!level.setBlock(anchor, air, flags)) return false;
            for (int i = 1; i < count; i++) level.setBlock(Rac1PodFootprint.CELLS.get(i).at(anchor), air, flags);
            for (int i = 0; i < count; i++) {
                var cell = Rac1PodFootprint.CELLS.get(i).at(anchor);
                // An edge slot may abut a chunk whose contents aren't available.
                for (Direction direction : Direction.values()) {
                    var neighbor = cell.relative(direction);
                    if (Rac1PodFootprint.loadedView(level, neighbor) == null) continue;
                    level.neighborChanged(neighbor, Blocks.AIR, cell);
                    level.neighborShapeChanged(direction.getOpposite(), air, neighbor, cell, Block.UPDATE_CLIENTS, 511);
                }
            }
            return true;
        } finally {
            marker.finishDismantle();
        }
    }

    private static boolean allowed(ServerLevel level, ServerPlayer user, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(user, pos)
                && !user.blockActionRestricted(level, pos, user.gameMode.getGameModeForPlayer());
    }

    private static BlockPos anchor(BlockPos pos, int part) {
        return part == 0 ? pos : Rac1PodFootprint.partAnchor(pos, part);
    }

    private static boolean intact(BlockGetter local, BlockPos anchor, BlockState parent, int part) {
        return parent.getBlock() instanceof Rac1PodBlock && (Rac1PodFootprint.full(parent)
                ? Rac1PodFootprint.complete(local, anchor) : part == 0);
    }

    static boolean recovers(BlockState state, ItemStack tool) {
        if (tool.is(Items.GOLDEN_PICKAXE) || tool.is(Items.IRON_PICKAXE)
                || tool.is(Items.DIAMOND_PICKAXE) || tool.is(Items.NETHERITE_PICKAXE)) return true;
        if (tool.is(Items.WOODEN_PICKAXE) || tool.is(Items.STONE_PICKAXE)) return false;
        return state.is(BlockTags.NEEDS_IRON_TOOL) && tool.is(ItemTags.PICKAXES) && tool.isCorrectToolForDrops(state);
    }

    static void drop(Level level, Player player, BlockPos pos, BlockState state, ItemStack tool) {
        // This native callback runs once after successful non-Creative removal, with the pre-wear copy.
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        player.causeFoodExhaustion(.005F);
        if (!player.isCreative() && recovers(state, tool)) {
            Block.popResource(level, pos, new ItemStack(Rac1PodRegistration.DECORATIVE.get()));
        }
    }
}
