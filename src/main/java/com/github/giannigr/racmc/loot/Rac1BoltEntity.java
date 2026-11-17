package com.github.giannigr.racmc.loot;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;

/** Non-inventory currency. Never awards XP and never becomes an ItemEntity. */
public final class Rac1BoltEntity extends Entity {
    private static final EntityDataAccessor<Integer> VALUE =
            SynchedEntityData.defineId(Rac1BoltEntity.class, EntityDataSerializers.INT);
    private UUID target;
    private double speed;

    public Rac1BoltEntity(EntityType<? extends Rac1BoltEntity> type, Level level) {
        super(type, level);
        noPhysics = true; // labelled Minecraft adaptation: homing may pass walls
    }
    public int value() { return entityData.get(VALUE); }
    public void setValue(int value) { entityData.set(VALUE, value); }
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
        if (!(level() instanceof ServerLevel level)) return;
        var ledger = Rac1BoltLedger.get(level);
        if (ledger.pickup(getUUID()) == null) { discard(); return; }
        ServerPlayer player = target == null ? null : level.getServer().getPlayerList().getPlayer(target);
        if (!eligible(player, level)) { player = null; target = null; speed = 0; }
        if (player == null) {
            player = level.players().stream().filter(p -> eligible(p, level))
                    .filter(p -> Math.abs(p.getY() - getY()) < 1.75
                            && horizontalDistance(p) < 9)
                    .min(Comparator.comparingDouble((ServerPlayer p) -> distanceToSqr(p))
                            .thenComparing(p -> p.getUUID().toString())).orElse(null);
            if (player != null) target = player.getUUID();
        }
        if (player != null) {
            Vec3 destination = player.position().add(0, player.getBbHeight() * .5, 0);
            Vec3 delta = destination.subtract(position());
            // R05 48*dt speed, 16*dt² acceleration; fixed 20TPS dt=.05.
            speed = Math.min(48 * .05, speed + 16 * .05 * .05);
            if (delta.length() <= speed) {
                if (ledger.collect(getUUID(), player)) discard();
            } else setPos(position().add(delta.normalize().scale(speed)));
        }
        if (getY() < level.getMinBuildHeight()) {
            // Retain/recover unclaimed currency above the void; no arbitrary beneficiary.
            setPos(getX(), level.getMinBuildHeight() + 1, getZ());
        }
        ledger.move(getUUID(), getX(), getY(), getZ());
    }

    private double horizontalDistance(ServerPlayer player) {
        double x = player.getX() - getX(), z = player.getZ() - getZ();
        return x*x + z*z;
    }
    private static boolean eligible(ServerPlayer player, ServerLevel level) {
        return player != null && player.isAlive() && !player.isSpectator()
                && !player.hasDisconnected() && player.serverLevel() == level;
    }
}
