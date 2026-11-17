package com.github.giannigr.racmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a crate of the game.
 *
 * <p>All crate kinds use the imported-mesh renderer. Explosive crates additionally persist
 * their server-authoritative loaded-tick fuse here.
 */
public class Rac1CrateBlockEntity extends BlockEntity {
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
            crate.sync();
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        fuse.save(tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
