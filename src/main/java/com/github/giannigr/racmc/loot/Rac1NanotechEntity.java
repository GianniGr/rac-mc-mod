package com.github.giannigr.racmc.loot;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;

/** One bounded semantic carrier, not eight rewarding entities. No asset bytes on the wire. */
public final class Rac1NanotechEntity extends Entity {
    private static final EntityDataAccessor<Long> SEED = SynchedEntityData.defineId(Rac1NanotechEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Optional<UUID>> RECEIVER =
            SynchedEntityData.defineId(Rac1NanotechEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(Rac1NanotechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMMITTED = SynchedEntityData.defineId(Rac1NanotechEntity.class, EntityDataSerializers.BOOLEAN);
    public Rac1NanotechEntity(EntityType<? extends Rac1NanotechEntity> type, Level level) {
        super(type, level); noPhysics = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SEED, 0L); b.define(RECEIVER, Optional.empty()); b.define(AGE, 0); b.define(COMMITTED, false);
    }
    void snapshot(Rac1NanotechLedger.Cluster c) {
        setPos(c.position); entityData.set(SEED, c.seed); entityData.set(RECEIVER, Optional.ofNullable(c.receiver));
        entityData.set(AGE, c.age); entityData.set(COMMITTED, c.committed);
    }
    public long seed() { return entityData.get(SEED); }
    public Optional<UUID> receiver() { return entityData.get(RECEIVER); }
    public int attractionAge() { return Math.clamp(entityData.get(AGE), 0, 100); }
    public boolean committed() { return entityData.get(COMMITTED); }
    @Override public void tick() {
        // No baseTick fire/void/item despawn policy.
        xo = getX(); yo = getY(); zo = getZ(); tickCount++;
        if (level() instanceof ServerLevel s && Rac1NanotechLedger.get(s).cluster(getUUID()) == null) discard();
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean ignoreExplosion(Explosion explosion) { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean fireImmune() { return true; }
}
