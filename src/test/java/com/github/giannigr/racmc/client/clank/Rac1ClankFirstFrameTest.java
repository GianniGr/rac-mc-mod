package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.clank.ClankFixtures;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** First rendered frame CPU pose/attachment probe, not a GPU blur screenshot. */
class Rac1ClankFirstFrameTest {
    private static final UUID ID=new UUID(17,36);
    private static Rac1ClankSession.Snapshot state(Action action,long serial) {
        return new Rac1ClankSession.Snapshot(ID,1,0,505,
                new Rac1ClankEquipment(true,true,true,true,Module.THRUSTER),action,serial,0,0);
    }
    private static Rac1AnimatedAsset animatedIdle(int id) {
        var base=ClankFixtures.model(id);int count=base.skeleton().jointCount();
        var parents=new int[count];parents[0]=-1;
        var rest=new float[count*3];rest[5]=1;
        var rotations=new float[count*8];
        for(int i=0;i<count*2;i++)rotations[i*4+3]=1;
        var clips=base.animations().clone();
        clips[1]=new Rac1Animation("sequence_1",new float[]{0,1},true,rotations,
                new int[]{0,1},new int[]{0,0},new float[]{0,0,0,1,0,0},
                new int[0],new int[0],new float[0],new boolean[0]);
        return new Rac1AnimatedAsset(id,"first-frame-synthetic",base.mesh(),
                new Rac1Skeleton(base.skeleton().inverseBind(),parents,rest),clips,base.textures(),
                id==608?new int[][]{{0},{1},{0},{1}}:new int[0][]);
    }
    @Test void longEntryKeepsSameIdleSequencePoseAndNozzlesContinuousWithoutBodyEchoes() {
        try {
            for(var role:new Role[]{Role.CLANK,Role.THRUSTER}) {
                var model=animatedIdle(role==Role.CLANK?601:608);
                float[] before=Rac1ClankPlayback.pose(ID,role,model,state(Action.IDLE,1),505.25);
                float[] oldChain=Rac1ClankPlayback.chain(ID,role,model);
                var history=new Rac1ClankTrail();
                assertTrue(history.sample(state(Action.LONG_JUMP,2),1515,0,new Matrix4f(),true).isEmpty());
                float[] first=Rac1ClankPlayback.pose(ID,role,model,state(Action.LONG_JUMP,2),505.25);
                float[] firstChain=Rac1ClankPlayback.chain(ID,role,model);
                assertArrayEquals(before,first,0.000001f,
                        "Unchanged sequence1 must not jump to its first key on long-jump entry");
                assertArrayEquals(oldChain,firstChain,0.000001f);
                assertArrayEquals(first,Rac1ClankPlayback.pose(ID,role,model,state(Action.LONG_JUMP,2),505.25),0.000001f);
                if(role==Role.THRUSTER) {
                    var zero=new Point(0,0,0);
                    assertEquals(Rac1ClankNozzles.thruster(model,oldChain,new Matrix4f(),zero),
                            Rac1ClankNozzles.thruster(model,firstChain,new Matrix4f(),zero));
                }
            }
        } finally {Rac1ClankPlayback.clear();}
    }
    @Test void highJumpClockIsNotChangedByTheLongOnlyContinuityFix() {
        try {
            var model=animatedIdle(608);
            Rac1ClankPlayback.pose(ID,Role.THRUSTER,model,state(Action.IDLE,1),505.25);
            var high=Rac1ClankPlayback.pose(ID,Role.THRUSTER,model,state(Action.HIGH_JUMP,2),505.25);
            var atStart=Rac1AnimationEvaluator.evaluate(model.skeleton(),model.animations()[1],0,1);
            assertArrayEquals(atStart,high,0.000001f);
        } finally {Rac1ClankPlayback.clear();}
    }
}
