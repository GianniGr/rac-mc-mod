package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose;
import com.github.giannigr.racmc.shop.Rac1ShopSessions;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopKioskTransition.Phase.*;

class Rac1ShopKioskTransitionTest {
    @Test void normalExitPowersOffThenRunsReverseFoldBeforeReturningIdle() {
        var clock=new Rac1ShopKioskTransition();
        assertEquals(CLOSED,clock.sample(false,0).phase());
        assertEquals(UNFOLD,clock.sample(true,1).phase());
        assertEquals(OPEN,clock.sample(true,2).phase());
        assertEquals(POWER_OFF,clock.sample(false,3).phase());
        var half=clock.sample(false,3+4/60.0);
        assertEquals(.5f,half.screenPower(),1e-5);
        var fold=clock.sample(false,3+9/60.0);
        assertEquals(FOLD,fold.phase());
        assertEquals(Rac1ShopKioskPose.Stage.REVERSE_FOLD,fold.pose());
        assertEquals(1/60f,fold.poseSeconds(),1e-5);
        assertEquals(FOLD,clock.sample(false,3+47/60.0).phase());
        assertEquals(CLOSED,clock.sample(false,3+49/60.0).phase());
        assertEquals(UNFOLD,clock.sample(true,4).phase());
        assertEquals(OPEN,clock.sample(true,5).phase());
    }

    @Test void oneViewerLeavingDoesNotPowerOffOtherViewerOrRemoteVendor() {
        var sessions=new Rac1ShopSessions();
        var vendor=new Rac1ShopSessions.Vendor("minecraft:overworld",1,UUID.randomUUID());
        var other=new Rac1ShopSessions.Vendor("minecraft:overworld",2,UUID.randomUUID());
        var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var sa=sessions.open(a,vendor,0); var sb=sessions.open(b,vendor,0);
        var clock=new Rac1ShopKioskTransition(); var remote=new Rac1ShopKioskTransition();
        clock.sample(sessions.viewers(vendor)>0,0);
        assertEquals(OPEN,clock.sample(true,1).phase());
        sessions.close(a,sa.token());
        assertEquals(OPEN,clock.sample(sessions.viewers(vendor)>0,2).phase());
        assertEquals(CLOSED,remote.sample(sessions.viewers(other)>0,2).phase());
        sessions.close(b,sb.token());
        assertEquals(POWER_OFF,clock.sample(sessions.viewers(vendor)>0,3).phase());
        assertEquals(CLOSED,clock.sample(false,4).phase());
    }

    @Test void reentryDuringFoldQueuesUnfoldRatherThanSnappingOpen() {
        var clock=new Rac1ShopKioskTransition();
        clock.sample(true,0); clock.sample(true,1); clock.sample(false,2);
        var before=clock.sample(false,2.25);
        var reentry=clock.sample(true,2.25);
        assertEquals(FOLD,reentry.phase());
        assertEquals(before.poseSeconds(),reentry.poseSeconds());
        assertEquals(FOLD,clock.sample(true,2.75).phase());
        assertEquals(UNFOLD,clock.sample(true,2.9).phase());
        assertEquals(OPEN,clock.sample(true,4).phase());
    }

    @Test void earlyDepartureFinishesUnfoldBeforeNormalFoldAndLongFramesCatchUp() {
        var clock=new Rac1ShopKioskTransition();
        clock.sample(true,0);
        assertEquals(UNFOLD,clock.sample(false,.1).phase());
        assertEquals(POWER_OFF,clock.sample(false,.7).phase());
        assertEquals(FOLD,clock.sample(false,1).phase());
        assertEquals(CLOSED,clock.sample(false,100).phase());
    }

    @Test void forcedResourceLossDiscardsSeparatelyAndRejectsInvalidTime() {
        var clock=new Rac1ShopKioskTransition();
        clock.sample(true,0); clock.sample(true,1);
        clock.discard(1);
        assertEquals(CLOSED,clock.sample(false,1).phase());
        assertThrows(IllegalArgumentException.class,() -> clock.sample(true,.9));
        assertThrows(IllegalArgumentException.class,() -> clock.sample(true,Double.NaN));
        assertThrows(IllegalArgumentException.class,() -> clock.discard(Double.POSITIVE_INFINITY));
    }
}
