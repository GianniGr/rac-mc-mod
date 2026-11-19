package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import org.junit.jupiter.api.Test;

class Rac1HydroLookPitchTest {
    private static Rac1HydroMotion run(double look,boolean rise,boolean dive) {
        var m=new Rac1HydroMotion();
        for(int i=0;i<120;i++) {
            var s=m.step(new Rac1HydroMotion.Input(true,rise,dive,0,0,0),Vector.ZERO,false,false,look);
            m.resolved(s.displacement());
            assertTrue(Math.abs(m.state().pitch())<=Math.toRadians(78)+1e-9);
            assertTrue(Math.abs(m.state().pitchRate())<=Math.toRadians(70)/60+1e-9);
            assertTrue(m.state().speed()<=7+1e-9);
        }
        return m;
    }
    @Test void neutralUpDownNativeLookSteersThroughExistingSpringNotImpulse() {
        assertEquals(0,run(0,false,false).velocity().y(),1e-9);
        assertTrue(run(-45,false,false).velocity().y()>4);
        assertTrue(run(45,false,false).velocity().y()< -4);
        var m=new Rac1HydroMotion();
        var first=m.step(new Rac1HydroMotion.Input(true,false,false,0,0,0),Vector.ZERO,false,false,-90);
        m.resolved(first.displacement());
        assertTrue(m.state().pitch()<0&&Math.abs(m.state().pitch())<=Math.toRadians(70)/60);
        assertEquals(13.0/60,m.velocity().length(),1e-9,"Look must not add vertical speed");
    }
    @Test void jumpSneakOverrideOpposingLookAndJumpWinsBoth() {
        assertTrue(run(78,true,false).velocity().y()>6);
        assertTrue(run(-78,false,true).velocity().y()< -6);
        assertTrue(run(78,true,true).velocity().y()>6);
    }
    @Test void finiteClampedPitchAndCheckpointReplayAreBounded() {
        assertEquals(78,Rac1HydroMotion.boundedLookPitch(1e100));
        assertEquals(-78,Rac1HydroMotion.boundedLookPitch(-1e100));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertEquals(0,Rac1HydroMotion.boundedLookPitch(bad));
        var a=run(-1e100,false,false);var b=a.copy();
        var input=new Rac1HydroMotion.Input(true,false,false,0,0,0);
        assertEquals(a.step(input,Vector.ZERO,false,false,40),b.step(input,Vector.ZERO,false,false,40));
    }
    @Test void lookAloneNeverStartsMotionOrSteersReleasedDrift() {
        var inactive=new Rac1HydroMotion();
        assertFalse(inactive.step(Rac1HydroMotion.Input.RELEASED,Vector.ZERO,false,false,-78).controlled());
        var a=run(0,false,false);var b=a.copy();
        var up=a.step(Rac1HydroMotion.Input.RELEASED,Vector.ZERO,false,false,-78);
        var down=b.step(Rac1HydroMotion.Input.RELEASED,Vector.ZERO,false,false,78);
        assertEquals(up,down);a.resolved(up.displacement());b.resolved(down.displacement());
        assertEquals(Rac1HydroMotion.Stage.DRIFT,a.state().stage());
        assertEquals(a.state(),b.state());
    }
}
