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

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (rewardId != null) {
            tag.putUUID("BoltRewardId", rewardId);
            tag.putInt("BoltRewardBase", rewardBase);
            tag.putBoolean("BoltRewardConsumed", rewardConsumed);
        }
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
