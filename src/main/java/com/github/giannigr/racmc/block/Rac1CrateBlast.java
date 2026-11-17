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
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

/** One event-aware, final-allowlisted blast. Never invokes vanilla terrain finalization. */
public final class Rac1CrateBlast {
    public static final double CRATE_RADIUS = 3.0;

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
                Rac1CrateBlastPolicy.TNT_KNOCKBACK_POWER, false, Explosion.BlockInteraction.DESTROY);
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
                new AABB(center, center).inflate(Rac1CrateBlastPolicy.KNOCKBACK_RADIUS + 1.0E-6), e ->
                        !(e instanceof ItemEntity) && (isLivingTarget(e)
                        && (Rac1CrateBlastPolicy.inRange(center, e.getBoundingBox(), e instanceof Player)
                        || e.distanceToSqr(center) <= 64.0)
                        || e instanceof FallingBlockEntity falling
                        && falling.getBlockState().getBlock() instanceof Rac1CrateBlock
                        && inCrateRange(center, e.getBoundingBox()))));
        Set<Entity> originals = new HashSet<>(entities);
        context.getToBlow().addAll(blocks.keySet());
        EventHooks.onExplosionDetonate(level, context, entities, Rac1CrateBlastPolicy.KNOCKBACK_RADIUS);
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
            com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy(level, airborne, airborne);
        }
        // Like primed TNT, the emitting cube is gone before exposure sampling. Other
        // crate blocks still shield living targets until after the damage pass.
        List<LivingEntity> livingTargets = new ArrayList<>();
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
                    com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy(level, falling, airborne);
                }
            } else if (entity instanceof LivingEntity living && isLivingTarget(entity)
                    && !entity.ignoreExplosion(context)) {
                livingTargets.add(living);
            }
        }
        // Independent loops: an 8-block impulse must never imply an 8-block damage hit.
        Set<LivingEntity> hurtTargets = new HashSet<>();
        for (LivingEntity living : livingTargets) {
            if (!living.isRemoved() && isLivingTarget(living)
                    && damage(level, context, center, living)) {
                hurtTargets.add(living);
            }
        }
        Map<ServerPlayer, Vec3> playerPushes = new HashMap<>();
        for (LivingEntity living : livingTargets) {
            if (!living.isRemoved() && !living.ignoreExplosion(context)) {
                knockback(level, context, center, living, playerPushes);
            }
        }
        for (LivingEntity living : hurtTargets) {
            if (!living.isRemoved()) {
                living.onExplosionHit(airborne);
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
        // Mirror vanilla's hitPlayers filter and additive float impulse, not an absolute
        // velocity replacement. A vanilla explosion packet would also replay sound/FX.
        playerPushes.forEach((player, push) ->
                PacketDistributor.sendToPlayer(player, new Rac1CrateKnockbackPayload(push)));
        com.github.giannigr.racmc.effect.Rac1EffectTriggers.blast(level, center);
    }

    /** Shared reward ownership, after the event/identity allowlist has authorized destruction. */
    protected static boolean destroyCrate(ServerLevel level, BlockPos pos, BlockState state,
            @Nullable Entity source) {
        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != state
                || !(state.getBlock() instanceof Rac1CrateBlock)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate)) return false;
        com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy(level, pos, state, source);
        return crate.isRemoved();
    }

    private static boolean damage(ServerLevel level, Explosion context, Vec3 center,
            LivingEntity entity) {
        boolean player = entity instanceof Player;
        double distance = Rac1CrateBlastPolicy.distance(center, entity.getBoundingBox());
        if (distance > Rac1CrateBlastPolicy.radius(player)) {
            return false;
        }
        double exposure = Explosion.getSeenPercent(center, entity);
        float amount = Rac1CrateBlastPolicy.damage(exposure);
        if (amount <= 0) {
            return false;
        }
        entity.hurt(level.damageSources().explosion(context), amount);
        return true;
    }

    private static void knockback(ServerLevel level, Explosion context, Vec3 center,
            LivingEntity entity, Map<ServerPlayer, Vec3> playerPushes) {
        double distance = Math.sqrt(entity.distanceToSqr(center));
        if (distance > Rac1CrateBlastPolicy.KNOCKBACK_RADIUS) {
            return;
        }
        Vec3 direction = new Vec3(entity.getX() - center.x,
                entity.getEyeY() - center.y, entity.getZ() - center.z);
        if (direction.lengthSqr() == 0) {
            return;
        }
        double exposure = Explosion.getSeenPercent(center, entity);
        double impulse = Rac1CrateBlastPolicy.knockback(distance, exposure)
                * (1.0 - entity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
        Vec3 push = EventHooks.getExplosionKnockback(level, context, entity,
                direction.normalize().scale(impulse));
        entity.setDeltaMovement(entity.getDeltaMovement().add(push));
        if (entity instanceof ServerPlayer player) {
            if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                context.getHitPlayers().put(player, push);
                playerPushes.put(player, push);
            }
        } else {
            entity.hurtMarked = true; // Immediate normal mob velocity synchronization.
        }
    }

    private static boolean isLivingTarget(Entity entity) {
        return entity.isAlive() && !entity.isSpectator()
                && (entity instanceof Player || entity instanceof Mob);
    }
}
