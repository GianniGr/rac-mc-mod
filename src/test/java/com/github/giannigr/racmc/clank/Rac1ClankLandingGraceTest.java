package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1ClankLandingGraceTest {
    @Test void exactHalfOpenFortyServerTickBoundary() {
        var grace=new Rac1ClankLandingGrace();grace.start(100,3,8);
        assertFalse(grace.covers(99));assertTrue(grace.covers(100));
        assertTrue(grace.covers(139));assertFalse(grace.covers(140));
        assertFalse(grace.covers(141));
    }
    @Test void touchdownConsumesEvenExpiredTokenAndCannotProtectAnotherFall() {
        var grace=new Rac1ClankLandingGrace();grace.start(100,3,8);
        assertTrue(grace.land(139));assertFalse(grace.active());
        assertFalse(grace.land(139));assertFalse(grace.land(140));
        grace.start(200,4,12);assertFalse(grace.land(240));assertFalse(grace.active());
    }
    @Test void lifecycleClearAndNewProvenReleaseHaveIndependentWindows() {
        var grace=new Rac1ClankLandingGrace();grace.start(100,3,8);grace.clear();
        assertFalse(grace.covers(101));assertEquals(-1,grace.releaseTick());
        grace.start(150,4,20);assertTrue(grace.covers(189));assertFalse(grace.covers(190));
        assertEquals(4,grace.actionSerial());assertEquals(20,grace.inputSerial());
    }
}
