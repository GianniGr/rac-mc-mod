package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
        award(level, pos, net.minecraft.world.phys.Vec3.atBottomCenterOf(pos), id, base,
                ((Rac1CrateBlock) expectedState.getBlock()).definition().classNumber(), cause);
        sound(level, net.minecraft.world.phys.Vec3.atCenterOf(pos),
                ((Rac1CrateBlock) expectedState.getBlock()).definition().classNumber());
        com.github.giannigr.racmc.effect.Rac1EffectTriggers.crateBreak(level, id,
                new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5),
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
        award(level, falling.blockPosition(), falling.position(), id, base, block.definition().classNumber(), cause);
        sound(level, falling.position(), block.definition().classNumber());
        com.github.giannigr.racmc.effect.Rac1EffectTriggers.crateBreak(level, id, falling.position(),
                block.definition().classNumber());
    }

    private static void award(ServerLevel level, BlockPos pos, net.minecraft.world.phys.Vec3 position,
                              java.util.UUID id, int base, int classId, @Nullable Entity cause) {
        if (classId == 501) {
            Rac1NanotechLedger.get(level).release(id, level, position);
            return; // one cluster, never a bolt reward (including zero-value currency tombstones)
        }
        boolean first = Rac1BoltLedger.get(level).rewardOnce(id, level, pos,
                Rac1BoltPayout.roll(base, new java.util.Random(level.random.nextLong())));
        if (first && classId == Rac1Crates.AMMO.classNumber()) {
            int pickups = BlasterAmmoCratePolicy.pickups(level.random.nextInt(BlasterAmmoCratePolicy.ROLL_BOUND));
            var stock = Rac1AmmoCrateRouting.forCause(level, cause);
            var ammo = stock.ammo().orElseThrow(); // Blaster is always registered; candidates were checked.
            for (int i = 0; i < pickups; i++) {
                // Vanilla pickup; no shop cap, direct inventory grant, or retry after a spawn veto.
                var drop = new ItemEntity(level, position.x, position.y, position.z,
                        new ItemStack(ammo, stock.pickupUnits()));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
    }

    private static void sound(ServerLevel level, net.minecraft.world.phys.Vec3 position, int classId) {
        // Only proven associations. Other crate classes are not silently bound to class500.
        var event = com.github.giannigr.racmc.sound.Rac1AudioEvent.crateBreak(classId);
        if (event != null) com.github.giannigr.racmc.sound.Rac1AudioSignal.emit(level, position, event);
    }
}
