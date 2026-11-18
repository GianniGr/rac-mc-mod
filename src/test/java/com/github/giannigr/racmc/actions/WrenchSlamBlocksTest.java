package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WrenchSlamBlocksTest {
    @Test void onlySmallActualSphereContactsBecomeBlockCandidates() {
        var blocks = new LinkedHashSet<BlockPos>();
        WrenchSlamBlocks.sphere(new ActionVector(0.5, 0.5, 0.5), 0.47, blocks);
        assertEquals(java.util.Set.of(BlockPos.ZERO), blocks);
        blocks.clear();
        WrenchSlamBlocks.sphere(new ActionVector(1.08, 1.08, 1.08), 0.1, blocks);
        assertEquals(4, blocks.size());
        assertFalse(blocks.contains(BlockPos.ZERO), "Broadphase corner is outside contact sphere");
    }

    @Test void sameBlockCannotBeHarvestedTwiceBySweepTipAndBodyOrReplacement() {
        var action = new WrenchActionState();
        assertTrue(action.start(1, WrenchActionState.Intent.SLAM, 0,
                new WrenchActionState.Eligibility(true, true, true, false, 4, 1, true, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)));
        assertTrue(action.claimBlock(123));
        assertFalse(action.claimBlock(123));
        assertTrue(action.claimBlock(124));
        action.cancel();
        assertFalse(action.claimBlock(125));
    }
}
