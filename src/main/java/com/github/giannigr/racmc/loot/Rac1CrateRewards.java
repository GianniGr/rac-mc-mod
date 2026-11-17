package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** One genuine destruction entry point. Falling/removal callbacks must not award separately. */
public final class Rac1CrateRewards {
    private Rac1CrateRewards() {}

    public static void destroy(ServerLevel level, BlockPos pos, BlockState expectedState,
                               @Nullable Entity cause) {
        if (level.getBlockState(pos) != expectedState
                || !(expectedState.getBlock() instanceof Rac1CrateBlock)
                || !(level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate)) return;
        crate.initializeReward();
        if (!crate.consumeReward()) return; // guard before any reentrant neighbor callbacks
        var id = crate.rewardId();
        int base = crate.rewardBase();
        if (!level.removeBlock(pos, false)) {
            crate.restoreReward();
            return;
        }
        // Spawn admission/retries and collection are owned by a single saved ledger.
        award(level, pos, id, base);
        sound(level, net.minecraft.world.phys.Vec3.atCenterOf(pos),
                ((Rac1CrateBlock) expectedState.getBlock()).definition().classNumber());
    }

    /** Same placement incarnation and ledger as grounded destruction; never pays on transfer. */
    public static void destroy(ServerLevel level,
            net.minecraft.world.entity.item.FallingBlockEntity falling, @Nullable Entity cause) {
        if (falling.level() != level || falling.isRemoved()
                || !(falling.getBlockState().getBlock() instanceof Rac1CrateBlock block)) return;
        var data = falling.blockData;
        if (data == null) data = falling.blockData = new net.minecraft.nbt.CompoundTag();
        if (data.getBoolean("BoltRewardConsumed")) return;
        if (!data.hasUUID("BoltRewardId")) {
            data.putUUID("BoltRewardId", java.util.UUID.randomUUID());
            data.putInt("BoltRewardBase", Rac1BoltPayout.selectBase(block.definition().classNumber(),
                    new java.util.Random(level.random.nextLong())));
        }
        data.putBoolean("BoltRewardConsumed", true);
        var id = data.getUUID("BoltRewardId");
        int base = Math.max(0, data.getInt("BoltRewardBase"));
        falling.discard();
        award(level, falling.blockPosition(), id, base);
        sound(level, falling.position(), block.definition().classNumber());
    }

    private static void award(ServerLevel level, BlockPos pos, java.util.UUID id, int base) {
        Rac1BoltLedger.get(level).reward(id, level, pos,
                Rac1BoltPayout.roll(base, new java.util.Random(level.random.nextLong())));
    }

    private static void sound(ServerLevel level, net.minecraft.world.phys.Vec3 position, int classId) {
        // Only proven associations. Other crate classes are not silently bound to class500.
        var event = com.github.giannigr.racmc.sound.Rac1AudioEvent.crateBreak(classId);
        if (event != null) com.github.giannigr.racmc.sound.Rac1AudioSignal.emit(level, position, event);
    }
}
