package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.clank.ClankFixtures;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import org.junit.jupiter.api.Test;

class Rac1ClankActionPoseTest {
    @Test void localSnapshotEndpointMatchesExistingEvaluatorWithoutSecondScale() {
        var model=ClankFixtures.model(601);
        var clip=model.animations()[1];
        var local=Rac1ClankPoseBlend.sample(model,clip,0);
        assertNotNull(local);
        assertArrayEquals(Rac1AnimationEvaluator.evaluate(model.skeleton(),clip,0,1),
                Rac1ClankPoseBlend.skin(model,local),1e-6f);
        var blended=Rac1ClankPoseBlend.blend(local,local,.4f);
        assertArrayEquals(Rac1ClankPoseBlend.skin(model,local),
                Rac1ClankPoseBlend.skin(model,blended),1e-6f);
    }
    @Test void authoredHeroClockAndNonzeroFirstFrameAreNotClipDurationPlayback() {
        assertEquals(8,Rac1ClankPlayback.heroFrame(Action.HIGH_JUMP,0));
        assertEquals(29,Rac1ClankPlayback.heroFrame(Action.HIGH_JUMP,35));
        assertEquals(50,Rac1ClankPlayback.heroFrame(Action.HIGH_JUMP,86));
        var source=new Rac1SourceTiming(0,new int[]{160,192},
                new int[]{Float.floatToIntBits(.5f),0},new float[]{0,.1f},new int[]{0,1});
        var clip=new Rac1Animation("synthetic",new float[]{0,.1f},false,
                new float[]{0,0,0,1,0,0,0,1},new int[0],new int[0],new float[0],
                new int[0],new int[0],new float[0],new boolean[0],source);
        assertEquals(0,Rac1ClankPlayback.authoredSeconds(clip,10));
        assertEquals(.05f,Rac1ClankPlayback.authoredSeconds(clip,11),1e-6f);
    }
}
