package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.item.RacMcEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla falling physics, with explicit fuse transfer and tracked flash state. */
public final class Rac1FallingExplosive extends FallingBlockEntity {
    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(
            Rac1FallingExplosive.class, EntityDataSerializers.INT);
    public final Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
    private double originY;

    public Rac1FallingExplosive(EntityType<? extends Rac1FallingExplosive> type, Level level) {
        super(type, level);
        dropItem = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FUSE, Rac1ExplosiveFuse.IDLE);
    }

    public int visualFuse() {
        return entityData.get(FUSE);
    }

    public static void fall(ServerLevel level, BlockPos pos, BlockState state) {
        Rac1FallingExplosive entity = new Rac1FallingExplosive(
                RacMcEntities.FALLING_EXPLOSIVE.get(), level);
        // Vanilla's state field is private; its supported NBT codec initializes it.
        CompoundTag initial = new CompoundTag();
        initial.put("BlockState", NbtUtils.writeBlockState(state));
        entity.readAdditionalSaveData(initial);
        entity.originY = pos.getY();
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.blocksBuilding = true;
        entity.setStartPos(pos);
        if (level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate) {
            entity.blockData = crate.saveWithoutMetadata(level.registryAccess());
            entity.fuse.restore(crate.fuse.remaining(), crate.fuse.lastTick());
        }
        entity.storeFuse();
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(entity);
    }

    /** Upgrade a baseline/command-created vanilla carrier without losing its saved physics or BE data. */
    @javax.annotation.Nullable
    public static Rac1FallingExplosive migrate(ServerLevel level, FallingBlockEntity old) {
        Rac1FallingExplosive entity = new Rac1FallingExplosive(
                RacMcEntities.FALLING_EXPLOSIVE.get(), level);
        CompoundTag saved = old.saveWithoutId(new CompoundTag());
        // Both entities briefly coexist during accepted spawn; the replacement needs a fresh
        // transport UUID. Gameplay fuse/terminal identity is in TileEntityData, not the UUID.
        saved.remove("UUID");
        if (!saved.contains("RacOriginY")) {
            saved.putDouble("RacOriginY", Math.max(old.getStartPos().getY(), old.getY()));
        }
        entity.load(saved);
        entity.setStartPos(old.getStartPos());
        if (!level.addFreshEntity(entity)) {
            return null; // A spawn veto must not consume the old carrier.
        }
        old.discard();
        return entity;
    }

    public void arm(boolean chain) {
        if (fuse.arm(chain, level().getGameTime())) {
            storeFuse();
        }
    }

    @Override
    public void onExplosionHit(@javax.annotation.Nullable net.minecraft.world.entity.Entity source) {
        if (!level().isClientSide) {
            arm(true);
        }
    }

    private void storeFuse() {
        if (blockData == null) {
            blockData = new CompoundTag();
        }
        fuse.save(blockData);
        entityData.set(FUSE, fuse.remaining());
    }

    public void landed(BlockPos pos) {
        if (originY > pos.getY()) {
            arm(false);
        }
        // onLand precedes vanilla's BE NBT overlay: update the overlay, not the fresh BE.
        storeFuse();
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (Rac1CrateBlast.touching(level(), getBoundingBox())) {
                arm(false);
            }
            if (fuse.tick(level().getGameTime())) {
                storeFuse();
                Rac1CrateBlast.detonate((ServerLevel) level(),
                        position().add(0, 0.5, 0), null, this);
                if (isRemoved()) {
                    return; // No ghost landing after airborne consumption.
                }
            }
            storeFuse();
        }
        super.tick();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("RacOriginY", originY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        originY = tag.contains("RacOriginY") ? tag.getDouble("RacOriginY") : getY();
        fuse.load(blockData == null ? new CompoundTag() : blockData);
        dropItem = false;
        storeFuse();
    }
}
