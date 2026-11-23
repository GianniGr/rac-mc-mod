package com.github.giannigr.racmc.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Position-bound provenance and the ship's public claim bit; no player or entitlement data. */
public final class Rac1PodMarker extends BlockEntity {
    private static final String ANCHOR = "NaturalAnchor";
    private static final String DECORATIVE = "DecorativeAnchor";
    private boolean natural;
    private boolean decorative;
    private boolean claimed;
    private boolean claiming;
    private boolean dismantling;

    public Rac1PodMarker(BlockPos pos, BlockState state) {
        super(Rac1PodRegistration.MARKER.get(), pos, state);
    }

    void markNatural() {
        natural = true;
        setChanged();
    }

    public boolean isNatural() {
        return natural;
    }

    void markDecorative() {
        if (natural) throw new IllegalStateException("Natural pod cannot become decorative");
        decorative = true;
        setChanged();
    }

    public boolean isDecorative() { return decorative; }

    public boolean isClaimed() { return natural && claimed; }

    boolean canDismantle() { return isClaimed() || decorative; }

    boolean beginDismantle() {
        if (!canDismantle() || dismantling) return false;
        dismantling = true;
        return true;
    }

    void finishDismantle() { dismantling = false; }

    boolean beginClaim() {
        if (!natural || claimed || claiming) return false;
        claiming = true;
        return true;
    }

    void commitClaim() {
        if (!claiming || claimed) throw new IllegalStateException("No pending natural pod claim");
        claimed = true;
        setChanged();
    }

    void finishClaim() { claiming = false; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        natural = tag.contains(ANCHOR, Tag.TAG_LONG) && tag.getLong(ANCHOR) == worldPosition.asLong();
        decorative = !natural && tag.contains(DECORATIVE, Tag.TAG_LONG)
                && tag.getLong(DECORATIVE) == worldPosition.asLong();
        claimed = natural && tag.getBoolean("Claimed");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (natural) tag.putLong(ANCHOR, worldPosition.asLong());
        if (decorative) tag.putLong(DECORATIVE, worldPosition.asLong());
        if (isClaimed()) tag.putBoolean("Claimed", true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
