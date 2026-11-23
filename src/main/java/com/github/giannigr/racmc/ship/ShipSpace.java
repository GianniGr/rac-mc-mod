package com.github.giannigr.racmc.ship;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** All queries are bounded and preceded by loaded-chunk checks, including the collision halo. */
final class ShipSpace {
    static boolean loaded(ServerLevel level, AABB box) {
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        AABB halo = box.inflate(1);
        BlockPos first = BlockPos.containing(halo.minX, halo.minY, halo.minZ);
        BlockPos last = BlockPos.containing(halo.maxX, halo.maxY, halo.maxZ);
        for (int x = SectionPos.blockToSectionCoord(first.getX()); x <= SectionPos.blockToSectionCoord(last.getX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(first.getZ()); z <= SectionPos.blockToSectionCoord(last.getZ()); z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    static boolean solidUnder(ServerLevel level, Entity entity, AABB box) {
        AABB probe = new AABB(box.minX, box.minY - 0.035, box.minZ, box.maxX, box.minY, box.maxZ);
        if (!loaded(level, probe)) return false;
        return level.getBlockCollisions(entity, probe).iterator().hasNext();
    }

    static boolean dryAndSafe(ServerLevel level, AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX + 1e-6, box.minY + 1e-6, box.minZ + 1e-6),
                BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6))) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || EntityType.PLAYER.isBlockDangerous(state)
                    || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.NETHER_PORTAL)
                    || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) return false;
        }
        return true;
    }

    static boolean clearExit(ServerLevel level, Player pilot, Vec3 seat, Vec3 end) {
        var dimensions = pilot.getDimensions(Pose.STANDING);
        AABB stand = dimensions.makeBoundingBox(end);
        // Straight outward at seat height, then down to the landing surface.
        Vec3 side = new Vec3(end.x, seat.y, end.z);
        AABB route = dimensions.makeBoundingBox(seat).minmax(dimensions.makeBoundingBox(side));
        AABB arrival = stand.minmax(dimensions.makeBoundingBox(side));
        AABB foot = new AABB(end.x - .08, end.y, end.z - .08, end.x + .08, end.y + .01, end.z + .08);
        AABB contact = stand.expandTowards(0, -.04, 0);
        return loaded(level, route.minmax(arrival).minmax(contact))
                && level.noCollision(pilot, route) && level.noCollision(pilot, arrival)
                && dryAndSafe(level, route.minmax(arrival).minmax(contact))
                && solidUnder(level, pilot, foot)
                && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, stand,
                        other -> other != pilot && other.isAlive() && !other.isSpectator()).isEmpty();
    }

    /** Every leg of a proposed access path must be loaded, free of blocks and hazards. */
    static boolean clearAccessLegs(ServerLevel level, Player pilot, List<Vec3> worldPoints) {
        var dimensions = pilot.getDimensions(Pose.STANDING);
        for (int i = 1; i < worldPoints.size(); i++) {
            var route = dimensions.makeBoundingBox(worldPoints.get(i - 1))
                    .minmax(dimensions.makeBoundingBox(worldPoints.get(i)));
            if (!loaded(level, route) || !level.noCollision(pilot, route) || !dryAndSafe(level, route)
                    || !noLivingOccupant(level, pilot, route)) {
                return false;
            }
        }
        return worldPoints.size() >= 2;
    }

    static boolean safeAccessStand(ServerLevel level, Player pilot, Vec3 feet) {
        AABB standing = pilot.getDimensions(Pose.STANDING).makeBoundingBox(feet);
        AABB check = standing.expandTowards(0, -.035, 0);
        AABB sole = new AABB(feet.x - .08, feet.y, feet.z - .08, feet.x + .08, feet.y + .01, feet.z + .08);
        if (!loaded(level, check) || !dryAndSafe(level, check) || !level.noCollision(pilot, standing)
                || !noLivingOccupant(level, pilot, standing)) return false;
        var blocks = level.getBlockCollisions(pilot, sole.expandTowards(0, -1e-5, 0));
        return Math.abs(Shapes.collide(Direction.Axis.Y, sole, blocks, -1e-5)) <= 1e-7;
    }

    private static boolean noLivingOccupant(ServerLevel level, Player pilot, AABB route) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, route,
                other -> other != pilot && other.isAlive() && !other.isSpectator()).isEmpty();
    }

    private ShipSpace() {}
}
