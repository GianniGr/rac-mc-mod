package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.loot.Rac1CrateRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Authorizes projectile-driven crate mutation, independently of direct impact admission
 * or a verified blast-wave query. Never melee harvest or generic tagged terrain destruction.
 * Owner must be online/alive in this dimension. Launch payment is not refunded on impact veto.
 */
public final class RangedCrateHits {
    private RangedCrateHits() {}

    public static boolean ordinary(BlockState state) {
        return eligible(state) && !((Rac1CrateBlock) state.getBlock()).explosive();
    }

    public static boolean eligible(BlockState state) {
        var block = state.getBlock();
        // Registry hard deny precedes all content checks, even Creative and tag spoofing.
        if (BuiltInRegistries.BLOCK.getKey(block).toString().equals("racmc:gadgetron_vendor")) return false;
        if (!(block instanceof Rac1CrateBlock)) return false;
        return RacMcBlocks.crates().values().stream().anyMatch(holder -> holder.get() == block);
    }

    private static ServerPlayer owner(Projectile projectile, BlockPos pos) {
        if (!(projectile.level() instanceof ServerLevel level) || projectile.isRemoved()
                || !(projectile.getOwner() instanceof ServerPlayer player)
                || !player.isAlive() || player.isRemoved() || player.level() != level
                || player.isSpectator() || !level.hasChunkAt(pos)
                || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return null;
        return player;
    }

    public static boolean grounded(Projectile projectile, BlockPos pos) {
        return grounded(projectile,pos,false);
    }

    public static boolean groundedBomb(com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb bomb,BlockPos pos) {
        return grounded(bomb,pos,true);
    }

    private static boolean grounded(Projectile projectile, BlockPos pos,boolean bomb) {
        var player = owner(projectile, pos);
        if (player == null) return false;
        var level = player.serverLevel();
        var state = level.getBlockState(pos);
        if (!eligible(state) || !(level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate)) return false;
        crate.initializeReward();
        var id = crate.rewardId();
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) return false;
        if (owner(projectile, pos) != player || level.getBlockState(pos) != state
                || level.getBlockEntity(pos) != crate || !id.equals(crate.rewardId()) || !eligible(state)) return false;
        if (((Rac1CrateBlock) state.getBlock()).explosive()) {
            if (bomb) {
                var accepted=(com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb) projectile;
                if (accepted.acceptedOwner()!=player || !crate.fuse.armFromBomb(player.getUUID(),level.getGameTime())) return false;
                crate.sync();
                return true;
            }
            crate.blasterHit();
            return true; // contact handled, NEVER contact removal or reward
        }
        Rac1CrateRewards.destroy(level, pos, state, projectile);
        return level.getBlockEntity(pos) != crate;
    }

    public static boolean airborne(Projectile projectile, FallingBlockEntity carrier) {
        return airborne(projectile,carrier,false);
    }

    public static boolean airborneBomb(com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb bomb,FallingBlockEntity carrier) {
        return airborne(bomb,carrier,true);
    }

    private static boolean airborne(Projectile projectile, FallingBlockEntity carrier,boolean bomb) {
        var pos = carrier.blockPosition();
        var player = owner(projectile, pos);
        if (player == null || carrier.level() != projectile.level() || carrier.isRemoved()) return false;
        var state = carrier.getBlockState();
        if (!eligible(state) || carrier.blockData == null || !carrier.blockData.hasUUID("BoltRewardId")
                || carrier.blockData.getBoolean("BoltRewardConsumed")) return false;
        var data = carrier.blockData;
        var id = data.getUUID("BoltRewardId");
        var position = carrier.position();
        // Real BreakEvent at the carrier cell with its captured state: protection plugins may
        // veto. It does not claim that the carrier has become a block or mutate terrain there.
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.serverLevel(), pos, state, player)).isCanceled())
            return false;
        if (owner(projectile, pos) != player || carrier.isRemoved() || carrier.level() != player.level()
                || carrier.getBlockState() != state || carrier.blockData != data
                || !id.equals(data.getUUID("BoltRewardId")) || data.getBoolean("BoltRewardConsumed")
                || !position.equals(carrier.position()) || !eligible(state)) return false;
        if (((Rac1CrateBlock) state.getBlock()).explosive()) {
            if (bomb) {
                var accepted=(com.github.giannigr.racmc.item.weapon.bomb.Rac1Bomb) projectile;
                if (accepted.acceptedOwner()!=player) return false;
                if (carrier instanceof com.github.giannigr.racmc.block.Rac1FallingExplosive explosive)
                    return explosive.armFromBomb(player.getUUID());
                var fuse=new com.github.giannigr.racmc.block.Rac1ExplosiveFuse();
                fuse.load(data);
                if (!fuse.armFromBomb(player.getUUID(),player.serverLevel().getGameTime())) return false;
                fuse.save(data);
                return true;
            }
            if (carrier instanceof com.github.giannigr.racmc.block.Rac1FallingExplosive explosive) {
                explosive.blasterHit();
            } else {
                // A baseline carrier may be hit before its next Pre tick migration. Persist
                // the fuse in its existing overlay; the migration owns transfer and loads it.
                var fuse = new com.github.giannigr.racmc.block.Rac1ExplosiveFuse();
                fuse.load(data);
                if (fuse.blasterHit(() -> 5 + player.serverLevel().random.nextInt(11),
                        player.serverLevel().getGameTime())) fuse.save(data);
            }
            return true;
        }
        Rac1CrateRewards.destroy(player.serverLevel(), carrier, projectile);
        return carrier.isRemoved();
    }
}
