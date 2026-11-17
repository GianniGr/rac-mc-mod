package com.github.giannigr.racmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a crate of the game.
 *
 * <p>Owns the persisted placement incarnation and selected original fresh-instance payout base,
 * as well as providing the original imported visual. Falling transfers this data unchanged.
 */
public class Rac1CrateBlockEntity extends BlockEntity {
    private java.util.UUID rewardId;
    private int rewardBase;
    private boolean rewardConsumed;

    @Override
    public void onLoad() {
        super.onLoad();
        initializeReward();
    }

    public void initializeReward() {
        if (rewardId == null && level != null && !level.isClientSide) {
            rewardId = java.util.UUID.randomUUID();
            int classNumber = ((Rac1CrateBlock) getBlockState().getBlock()).definition().classNumber();
            rewardBase = com.github.giannigr.racmc.loot.Rac1BoltPayout.selectBase(classNumber,
                    new java.util.Random(level.random.nextLong()));
            setChanged();
        }
    }

    public java.util.UUID rewardId() { return rewardId; }
    public int rewardBase() { return rewardBase; }
    public boolean consumeReward() {
        if (rewardConsumed) return false;
        rewardConsumed = true;
        setChanged();
        return true;
    }
    public void restoreReward() { rewardConsumed = false; setChanged(); }
    public final Rac1ExplosiveFuse fuse = new Rac1ExplosiveFuse();
    private final Rac1CrateFlashClock flashClock = new Rac1CrateFlashClock();

    public boolean visualRed(float partialTick) {
        return level != null && flashClock.red(
                fuse.remaining(), fuse.lastTick(), level.getGameTime(), partialTick);
    }

    public boolean arm(boolean chain) {
        if (level == null || level.isClientSide || !fuse.arm(chain, level.getGameTime())) {
            return false;
        }
        sync();
        return true;
    }

    public void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean wrenchHit() {
        if (level == null || level.isClientSide || isRemoved()
                || !(getBlockState().getBlock() instanceof Rac1CrateBlock block) || !block.explosive()
                || !fuse.wrenchHit(5 + level.random.nextInt(11), level.getGameTime())) return false;
        sync();
        return true;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
            BlockState state, Rac1CrateBlockEntity crate) {
        if (!(state.getBlock() instanceof Rac1CrateBlock block) || !block.explosive()) {
            return;
        }
        if (Rac1CrateBlast.touching(level, new net.minecraft.world.phys.AABB(pos))) {
            crate.arm(false);
        }
        int before = crate.fuse.remaining();
        if (crate.fuse.tick(level.getGameTime())) {
            Rac1CrateBlast.detonate((net.minecraft.server.level.ServerLevel) level,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos), crate, null);
        }
        if (!crate.isRemoved() && before != crate.fuse.remaining()) {
            if (com.github.giannigr.racmc.sound.Rac1AudioSignal.beep(before, crate.fuse.remaining())) {
                com.github.giannigr.racmc.sound.Rac1AudioSignal.emit(
                        (net.minecraft.server.level.ServerLevel) level,
                        net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        com.github.giannigr.racmc.sound.Rac1AudioEvent.TNT_BEEP);
            }
            crate.sync();
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (rewardId != null) {
            tag.putUUID("BoltRewardId", rewardId);
            tag.putInt("BoltRewardBase", rewardBase);
            tag.putBoolean("BoltRewardConsumed", rewardConsumed);
        }
        fuse.save(tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("BoltRewardId")) {
            rewardId = tag.getUUID("BoltRewardId");
            rewardBase = Math.max(0, tag.getInt("BoltRewardBase"));
            rewardConsumed = tag.getBoolean("BoltRewardConsumed");
        }
        fuse.load(tag);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
            net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Creates the block entity of a crate.
     *
     * @param pos the place of the block
     * @param state the state of the block
     */
    public Rac1CrateBlockEntity(BlockPos pos, BlockState state) {
        super(RacMcBlockEntities.CRATE.get(), pos, state);
    }
}
