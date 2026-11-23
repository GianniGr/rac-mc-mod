package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.client.shop.Rac1ShopKioskTransition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopKioskPoseTest {
    @Test void originalCountBindingIsAlternatingLocalTranslationNotRotation() {
        int[] refs = new int[24]; Arrays.fill(refs,20000);
        var tables = new Rac1ShopSceneTables(new Rac1ShopLedFont(new int[64],new int[64]),
                refs,new float[24],new float[8],new float[20],1006,new int[]{3,2,5,4},.25f);
        for (int count : new int[]{0,1,6,7,8,64}) {
            var modifiers = Rac1ShopKioskPose.monitorArms(tables,count);
            float expected = (count < 8 ? (7-count)*1665 : 0)/1024f*.25f*Rac1AssetExtractor.UNIT_SCALE;
            for (int i = 0; i < 4; i++) {
                var m = modifiers.get(i);
                assertEquals(new int[]{3,2,5,4}[i],m.joint());
                assertEquals(0,m.x()); assertEquals(0,m.y()); assertEquals(0,m.z()); assertEquals(1,m.w());
                assertEquals(0,m.tx()); assertEquals(0,m.ty());
                assertEquals((i%2 == 0 ? -1 : 1)*expected,m.tz());
            }
        }
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopKioskPose.monitorArms(tables,-1));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopKioskPose.monitorArms(tables,65));
    }
    @Test void translationModifierMovesTheLocalJointWithoutMutatingSource() {
        var body = body(); var poses = new Rac1ShopKioskPose(body);
        float[] before = body.skeleton().restTranslations().clone();
        var original = poses.stage(Rac1ShopKioskPose.Stage.CLOSED,0,List.of());
        var moved = poses.stage(Rac1ShopKioskPose.Stage.CLOSED,0,List.of(
                new Rac1ShopKioskPose.Modifier(1,0,0,0,1,0,0,3)));
        for (int i = 0; i < original.positions().length; i+=3) {
            assertEquals(original.positions()[i],moved.positions()[i],1e-6);
            assertEquals(original.positions()[i+1],moved.positions()[i+1],1e-6);
            // Only vertex0 is weighted to joint1; vertices1/2 use sibling2/root0.
            assertEquals(original.positions()[i+2]+(i == 0 ? 3 : 0),moved.positions()[i+2],1e-6);
        }
        assertArrayEquals(before,body.skeleton().restTranslations());
    }
    private Rac1AnimatedAsset body() {
        var base = SyntheticVendor.asset(11);
        var original = base.animations()[0];
        Rac1Animation[] clips = new Rac1Animation[5];
        for (int i = 0; i < 5; i++) {
            float[] rotations = new float[24];
            System.arraycopy(original.rotations(),0,rotations,0,12);
            System.arraycopy(original.rotations(),0,rotations,12,12);
            clips[i] = new Rac1Animation("sequence_"+i,new float[]{0,1},false,rotations,
                    new int[]{0,1},new int[]{1,1},new float[]{0,0,0,10,0,0},
                    new int[0],new int[0],new float[0],new boolean[0]);
        }
        return new Rac1AnimatedAsset(11,"synthetic-kiosk",base.mesh(),base.skeleton(),clips,base.textures());
    }
    @Test void unfoldHalfSpeedAndReverseFoldHoldOriginalEndpointsWithoutRefitting() {
        var body = body(); var poses = new Rac1ShopKioskPose(body);
        var first = poses.stage(Rac1ShopKioskPose.Stage.UNFOLD,0,List.of());
        var middle = poses.stage(Rac1ShopKioskPose.Stage.UNFOLD,1,List.of());
        var last = poses.stage(Rac1ShopKioskPose.Stage.UNFOLD,2,List.of());
        assertEquals(5,middle.positions()[0]-first.positions()[0],1e-6);
        assertEquals(10,last.positions()[0]-first.positions()[0],1e-6);
        assertArrayEquals(last.positions(),poses.stage(Rac1ShopKioskPose.Stage.REVERSE_FOLD,0,List.of()).positions());
        assertArrayEquals(first.positions(),poses.stage(Rac1ShopKioskPose.Stage.REVERSE_FOLD,2,List.of()).positions());
        assertArrayEquals(first.positions(),poses.stage(Rac1ShopKioskPose.Stage.REVERSE_FOLD,100,List.of()).positions());
    }
    @Test void closeClockDrivesChangingReverseGeometryNotJustAVisibilityFlag() {
        var clock=new Rac1ShopKioskTransition();
        var poses=new Rac1ShopKioskPose(body());
        clock.sample(true,0); clock.sample(true,1); clock.sample(false,2);
        var first=clock.sample(false,2.2);
        var later=clock.sample(false,2.4);
        assertEquals(Rac1ShopKioskTransition.Phase.FOLD,first.phase());
        assertEquals(first.phase(),later.phase());
        var a=poses.stage(first.pose(),first.poseSeconds(),List.of());
        var b=poses.stage(later.pose(),later.poseSeconds(),List.of());
        assertTrue(b.positions()[0]<a.positions()[0],"Fold must drive actual posed vertices");
        assertEquals(1,a.positions()[0]-b.positions()[0],1e-5);
    }
    @Test void localModeZeroHamiltonModifierPrecedesHierarchyAndDoesNotMutateSource() {
        var body = body(); float[] before = body.animations()[0].rotations().clone();
        var poses = new Rac1ShopKioskPose(body);
        float half = .25f;
        var pose = poses.stage(Rac1ShopKioskPose.Stage.CLOSED,0,List.of(
                new Rac1ShopKioskPose.Modifier(1,0,(float)Math.sin(half),0,(float)Math.cos(half))));
        assertEquals(-2*Math.cos(.5)+2*Math.sin(.5),pose.positions()[0],1e-5);
        assertEquals(-2*Math.sin(.5)-2*Math.cos(.5),pose.positions()[2],1e-5);
        assertArrayEquals(before,body.animations()[0].rotations());
        assertThrows(IllegalArgumentException.class, () -> poses.stage(Rac1ShopKioskPose.Stage.OPEN,0,List.of(
                new Rac1ShopKioskPose.Modifier(99,0,0,0,1))));
    }
    @Test void crossSequenceQuaternionHemisphereBlendAndTimeBoundsAreExplicit() {
        var body = body();
        for (int i = 3; i < body.animations()[3].rotations().length; i+=4)
            body.animations()[3].rotations()[i] = -1;
        var poses = new Rac1ShopKioskPose(body);
        var result = poses.blend(0,0,3,0,.5f,List.of());
        assertArrayEquals(poses.stage(Rac1ShopKioskPose.Stage.CLOSED,0,List.of()).positions(),result.positions(),1e-6f);
        assertThrows(IllegalArgumentException.class, () -> poses.stage(Rac1ShopKioskPose.Stage.UNFOLD,-1,List.of()));
        assertThrows(IllegalArgumentException.class, () -> poses.blend(0,0,3,0,Float.NaN,List.of()));
    }
}
