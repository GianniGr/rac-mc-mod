package com.github.giannigr.racmc.loot;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;

/** Non-inventory currency. Never awards XP and never becomes an ItemEntity. */
public final class Rac1BoltEntity extends Entity {
    private static final EntityDataAccessor<Integer> VALUE =
            SynchedEntityData.defineId(Rac1BoltEntity.class, EntityDataSerializers.INT);
    private static final double RETURN_ATTRACTION_RADIUS = 2.7F;
    private State motion;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private int interpolationSteps;

    public Rac1BoltEntity(EntityType<? extends Rac1BoltEntity> type, Level level) {
        super(type, level);
        noPhysics = true; // falling uses our sphere adapter; attraction may pass walls
    }
    public int value() { return entityData.get(VALUE); }
    public void setValue(int value) { entityData.set(VALUE, value); }
    void restoreMotion(State state) {
        motion = state;
        V p = state.position(), v = state.velocity();
        setPos(p.x(), p.y(), p.z());
        setDeltaMovement(v.x() * 3, v.y() * 3, v.z() * 3);
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x, y, z);
        interpolationSteps = Math.max(1, steps);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(VALUE, 1); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { setValue(tag.getInt("Value")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Value", value()); }
    @Override public boolean shouldBeSaved() { return false; } // the ledger is the only durable owner
    @Override public boolean ignoreExplosion(Explosion explosion) { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public void tick() {
        // Do not inherit baseTick's void discard/fire policy. No expiry/distance deletion.
        xo = getX(); yo = getY(); zo = getZ();
        xOld = getX(); yOld = getY(); zOld = getZ();
        tickCount++;
        if (!(level() instanceof ServerLevel level)) {
            if (interpolationSteps > 0) {
                V p = Rac1BoltMotion.interpolate(new V(getX(), getY(), getZ()),
                        new V(interpolationTarget.x, interpolationTarget.y, interpolationTarget.z), interpolationSteps);
                setPos(p.x(), p.y(), p.z());
                interpolationSteps--;
            }
            return;
        }
        var ledger = Rac1BoltLedger.get(level);
        if (ledger.pickup(getUUID()) == null) { discard(); return; }
        if (motion == null) {
            var record = ledger.pickup(getUUID());
            restoreMotion(record.motion() == null
                    ? Rac1BoltMotion.legacy(new V(getX(), getY(), getZ())) : record.motion());
        }
        var collision = new Rac1BoltWorldCollision(level, this);
        for (int frame = 0; frame < Rac1BoltMotion.SUBSTEPS; frame++) {
            ServerPlayer player = motion.target() == null ? null
                    : level.getServer().getPlayerList().getPlayer(motion.target());
            if (!eligible(player, level)) player = null;
            if (player == null) {
                player = level.players().stream().filter(p -> eligible(p, level))
                        .min(Comparator.comparingInt((ServerPlayer p) ->
                                Rac1BoltMotion.inRange(motion.position(), new V(p.getX(), p.getY(), p.getZ())) ? 0 : 1)
                                .thenComparingDouble(p -> distanceToSqr(p))
                                .thenComparing(p -> p.getUUID().toString())).orElse(null);
            }
            Hero hero = player == null ? null : new Hero(player.getUUID(),
                    new V(player.getX(), player.getY(), player.getZ()),
                    new V(player.getX(), player.getY() + player.getBbHeight() * .5, player.getZ()));
            var step = Rac1BoltMotion.step(motion, value(), collision, hero, random::nextDouble);
            if (motion.target() == null && step.state().target() != null) {
                com.github.giannigr.racmc.sound.Rac1AudioSignal.emit(level, position(),
                        com.github.giannigr.racmc.sound.Rac1AudioEvent.attraction(value()));
            }
            restoreMotion(step.state());
            if (step.collect() && player != null && ledger.collect(getUUID(), player)) {
                discard();
                return;
            }
        }
        if (getY() < level.getMinBuildHeight()) {
            // Retain/recover unclaimed currency above the void; no arbitrary beneficiary.
            // Existing value recovery policy, explicitly not an original PS2 hazard response.
            motion = Rac1BoltMotion.legacy(new V(getX(), level.getMinBuildHeight() + 1, getZ()));
            restoreMotion(motion);
        }
        ledger.move(getUUID(), motion);
    }

    /** At most one existing bolt per return substep; credit is still only on body arrival. */
    public static void attractFromReturningWrench(ServerPlayer owner, Vec3 center) {
        ServerLevel level = owner.serverLevel();
        if (!eligible(owner, level) || !level.hasChunkAt(BlockPos.containing(center))) return;
        var bounds = new AABB(center, center).inflate(RETURN_ATTRACTION_RADIUS);
        var bolts = level.getEntitiesOfClass(Rac1BoltEntity.class, bounds,
                bolt -> bolt.position().distanceToSqr(center)
                        < RETURN_ATTRACTION_RADIUS * RETURN_ATTRACTION_RADIUS);
        bolts.sort(Comparator.comparingInt(Rac1BoltEntity::getId));
        for (var bolt : bolts) {
            if (bolt.beginRemoteAttraction(level, owner)) return;
        }
    }

    private boolean beginRemoteAttraction(ServerLevel level, ServerPlayer owner) {
        // Entity/ledger mutation is serialized with ordinary pickup on the server thread.
        if (isRemoved() || level.getEntity(getUUID()) != this) return false;
        var ledger = Rac1BoltLedger.get(level);
        var record = ledger.pickup(getUUID());
        if (record == null || record.dimension() != level.dimension()) return false;
        State current = motion != null ? motion : record.motion();
        if (current == null) current = Rac1BoltMotion.legacy(new V(getX(), getY(), getZ()));
        State next = Rac1BoltMotion.beginRemoteAttraction(current, owner.getUUID(), random::nextDouble);
        if (next == current) return false;
        restoreMotion(next);
        ledger.move(getUUID(), next);
        com.github.giannigr.racmc.sound.Rac1AudioSignal.emit(level, position(),
                com.github.giannigr.racmc.sound.Rac1AudioEvent.attraction(value()));
        return true;
    }

    private static boolean eligible(ServerPlayer player, ServerLevel level) {
        return player != null && player.isAlive() && !player.isSpectator()
                && !player.hasDisconnected() && player.serverLevel() == level;
    }
}
