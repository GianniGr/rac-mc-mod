package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.block.Rac1CrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Fixture-only package access, no widening of the production wrench API. */
public final class NanotechWrenchProbe {
    public static void destroy(ServerPlayer p, BlockPos pos, Rac1CrateBlockEntity c, Vec3 at, boolean thrown) {
        WrenchActions.harvestContact(p, new WrenchSlamBlocks.Candidate(pos, c.getBlockState(), c),
                thrown ? new WrenchActions.ThrowReach(new WrenchContact.Segment(
                        WrenchActions.vector(at.add(-1, .5, 0)), WrenchActions.vector(at.add(1, .5, 0))), 5) : null);
    }
}
