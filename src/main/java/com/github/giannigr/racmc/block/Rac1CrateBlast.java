package com.github.giannigr.racmc.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** One event-aware, final-allowlisted blast. Never invokes vanilla terrain finalization. */
public final class Rac1CrateBlast {
    public static final double CRATE_RADIUS = 3.0;
    public static final double DAMAGE_RADIUS = 8.0; // Vanilla TNT strength 4, NOT strength 2.

    private Rac1CrateBlast() {
    }

    public static boolean touching(Level level, AABB bounds) {
        return !level.getEntities((Entity) null, bounds.inflate(1.0E-6),
                e -> e.isAlive() && !e.isSpectator() && (e instanceof Player || e instanceof Mob)
                        && bounds.inflate(1.0E-6).intersects(e.getBoundingBox())).isEmpty();
    }

    public static boolean inCrateRange(Vec3 center, AABB bounds) {
        double x = Math.max(bounds.minX, Math.min(center.x, bounds.maxX));
        double y = Math.max(bounds.minY, Math.min(center.y, bounds.maxY));
        double z = Math.max(bounds.minZ, Math.min(center.z, bounds.maxZ));
        return center.distanceToSqr(new Vec3(x, y, z)) <= CRATE_RADIUS * CRATE_RADIUS;
    }

    public static void detonate(ServerLevel level, Vec3 center,
            @Nullable Rac1CrateBlockEntity grounded, @Nullable Rac1FallingExplosive airborne) {
        Explosion context = new Explosion(level, airborne, center.x, center.y, center.z,
                4.0F, false, Explosion.BlockInteraction.DESTROY);
        if (EventHooks.onExplosionStart(level, context)) {
            return; // Terminal suppressed state: no retries, removal, damage, chain or payout.
        }
        Map<BlockPos, Rac1CrateBlockEntity> blocks = new HashMap<>();
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-4, -4, -4),
                origin.offset(4, 4, 4))) {
            BlockPos pos = mutable.immutable();
            if (level.hasChunkAt(pos) && inCrateRange(center, new AABB(pos))
                    && level.getBlockState(pos).getBlock() instanceof Rac1CrateBlock
                    && level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate) {
                blocks.put(pos, crate);
            }
        }
        List<Entity> entities = new ArrayList<>(level.getEntities((Entity) null,
                new AABB(center, center).inflate(DAMAGE_RADIUS + 1), e ->
                        !(e instanceof ItemEntity) && (e instanceof LivingEntity
                        || e instanceof FallingBlockEntity falling
                        && falling.getBlockState().getBlock() instanceof Rac1CrateBlock
                        && inCrateRange(center, e.getBoundingBox()))));
        Set<Entity> originals = new HashSet<>(entities);
        context.getToBlow().addAll(blocks.keySet());
        EventHooks.onExplosionDetonate(level, context, entities, DAMAGE_RADIUS);
        Set<BlockPos> authorized = new HashSet<>(context.getToBlow());
        Set<Entity> authorizedEntities = new HashSet<>(entities);
        // Self-consumption is protected by the same event list as other targets.
        if (grounded != null && (!authorized.contains(grounded.getBlockPos())
                || level.getBlockEntity(grounded.getBlockPos()) != grounded)
                || airborne != null && (!authorizedEntities.contains(airborne) || airborne.isRemoved())) {
            return;
        }
        if (grounded != null && !destroyCrate(level, grounded.getBlockPos(),
                grounded.getBlockState(), airborne)) {
            return;
        }
        if (airborne != null) {
            airborne.discard();
        }
        // Like primed TNT, the emitting cube is gone before exposure sampling. Other
        // crate blocks still shield living targets until after the damage pass.
        for (Entity entity : authorizedEntities) {
            // Event additions cannot inject terrain, items, or unrelated entities into the blast.
            if (!originals.contains(entity) || entity.isRemoved() || entity instanceof ItemEntity) {
                continue;
            }
            if (entity instanceof FallingBlockEntity falling) {
                if (!inCrateRange(center, entity.getBoundingBox())
                        || !(falling.getBlockState().getBlock() instanceof Rac1CrateBlock block)) {
                    continue;
                }
                if (falling instanceof Rac1FallingExplosive explosive) {
                    explosive.arm(true);
                } else if (block.explosive()) {
                    Rac1FallingExplosive migrated = Rac1FallingExplosive.migrate(level, falling);
                    if (migrated != null) {
                        migrated.arm(true);
                    }
                } else {
                    // Baseline falling crates have no payouts. Integration seam mirrors destroyCrate.
                    falling.discard();
                }
            } else if (entity instanceof LivingEntity living && !entity.ignoreExplosion(context)) {
                damage(level, context, center, living, airborne);
            }
        }
        for (Map.Entry<BlockPos, Rac1CrateBlockEntity> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            Rac1CrateBlockEntity crate = entry.getValue();
            if (crate == grounded || !authorized.contains(pos) || !level.hasChunkAt(pos)
                    || level.getBlockEntity(pos) != crate || crate.isRemoved()
                    || !(level.getBlockState(pos).getBlock() instanceof Rac1CrateBlock block)) {
                continue;
            }
            if (block.explosive()) {
                crate.arm(true);
            } else {
                destroyCrate(level, pos, level.getBlockState(pos), airborne);
            }
        }
    }

    /**
     * Consolidation seam: replace the removeBlock call with
     * Rac1CrateRewards.destroy(level, pos, state, source). No payout dependency on this branch.
     */
    protected static boolean destroyCrate(ServerLevel level, BlockPos pos, BlockState state,
            @Nullable Entity source) {
        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != state
                || !(state.getBlock() instanceof Rac1CrateBlock)) {
            return false;
        }
        return level.removeBlock(pos, false);
    }

    private static void damage(ServerLevel level, Explosion context, Vec3 center,
            LivingEntity entity, @Nullable Entity source) {
        double distance = Math.sqrt(entity.distanceToSqr(center)) / DAMAGE_RADIUS;
        if (distance > 1.0) {
            return;
        }
        Vec3 direction = new Vec3(entity.getX() - center.x,
                entity.getEyeY() - center.y, entity.getZ() - center.z);
        if (direction.lengthSqr() == 0) {
            return;
        }
        double exposure = Explosion.getSeenPercent(center, entity);
        entity.hurt(level.damageSources().explosion(context),
                Rac1ExplosiveFuse.halfTntDamage(distance, exposure));
        double impulse = (1.0 - distance) * exposure
                * (1.0 - entity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
        Vec3 push = EventHooks.getExplosionKnockback(level, context, entity,
                direction.normalize().scale(impulse));
        entity.setDeltaMovement(entity.getDeltaMovement().add(push));
        entity.hurtMarked = true; // Includes authoritative player velocity synchronization.
        entity.onExplosionHit(source);
    }
}
