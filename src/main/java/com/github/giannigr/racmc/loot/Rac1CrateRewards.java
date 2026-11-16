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
        Rac1BoltLedger.get(level).reward(id, level, pos,
                Rac1BoltPayout.roll(base, new java.util.Random(level.random.nextLong())));
    }
}
