package com.github.giannigr.racmc.loot;

import java.util.ArrayList;
import com.github.giannigr.racmc.loot.Rac1BoltCollision.Box;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Real loaded-world voxel shapes (stairs/slabs included); no synthetic floor or chunk loading. */
final class Rac1BoltWorldCollision implements Collision {
    private final Rac1BoltCollision collision;
    Rac1BoltWorldCollision(ServerLevel level, Rac1BoltEntity entity) {
        collision = new Rac1BoltCollision((from, to, radius) -> {
            var boxes = new ArrayList<Box>();
            AABB bounds = new AABB(from.x(), from.y(), from.z(), to.x(), to.y(), to.z()).inflate(radius);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                if (level.isOutsideBuildHeight(pos)) continue; // void is not an unloaded solid floor
                if (!level.hasChunkAt(pos)) {
                    // Unloaded frontier is a temporary barrier, never force-load or lose ledger value.
                    boxes.add(new Box(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
                    continue;
                }
                for (AABB box : level.getBlockState(pos).getCollisionShape(level, pos,
                        CollisionContext.of(entity)).toAabbs()) {
                    boxes.add(new Box(box.minX + pos.getX(), box.minY + pos.getY(), box.minZ + pos.getZ(),
                            box.maxX + pos.getX(), box.maxY + pos.getY(), box.maxZ + pos.getZ()));
                }
            }
            return boxes;
        });
    }
    @Override public Movement move(V from, V displacement, double radius) {
        return collision.move(from, displacement, radius);
    }
}
