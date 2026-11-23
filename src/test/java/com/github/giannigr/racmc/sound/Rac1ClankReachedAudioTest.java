package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankReachedAudioTest {
    private static Rac1ClankReachedAudio.Advance a(long step,int seq,int from,int to,boolean blend,boolean wrap) {
        return new Rac1ClankReachedAudio.Advance(1,2,step,seq,from,to,blend,wrap);
    }
    @Test void reachedLongAndSkidKeysUseAuthoredOrderAndNotHeaderTimeOrInput() {
        var c=new Rac1ClankReachedAudio();c.enter(1,2,0,0x12);
        assertEquals(43,c.advance(a(1,0x12,4,8,false,false)).orElseThrow());
        assertEquals(31,c.advance(a(2,0x12,204,208,false,false)).orElseThrow());
        c.enter(1,2,0,0x26);
        assertEquals(1,c.advance(a(1,0x26,0,12,false,false)).orElseThrow());
        c.enter(1,2,0,5);
        assertTrue(c.advance(a(1,5,28,32,true,false)).isEmpty());
        assertTrue(c.advance(a(2,5,32,36,false,false)).isEmpty(),"No catch-up from suppressed blend");
    }
    @Test void retimedAndLandingIntervalsRemainReachedObservationsNotStateAge() {
        var c=new Rac1ClankReachedAudio();c.enter(1,2,0,0x15);
        var fired=new ArrayList<Integer>();
        for(var a:List.of(a(1,0x15,4,8,false,false),a(2,0x15,76,83,false,false),
                a(3,0x15,432,443,false,false),a(4,0x15,460,466,false,false))) {
            c.advance(a).ifPresent(fired::add);assertTrue(c.advance(a).isEmpty());
        }
        assertEquals(List.of(49,46,30,31),fired);
        c.enter(1,2,0,0x11);
        assertEquals(62,c.advance(a(1,0x11,92,99,false,false)).orElseThrow());
        assertEquals(31,c.advance(a(2,0x11,316,323,false,false)).orElseThrow());
        assertEquals(30,c.advance(a(3,0x11,348,357,false,false)).orElseThrow());
    }
    @Test void gapsInterruptionOwnerChangeAndWrapDoNotReplayMissedKeys() {
        var c=new Rac1ClankReachedAudio();
        assertTrue(c.advance(a(1,5,28,32,false,false)).isEmpty());
        c.enter(1,2,0,5);
        assertTrue(c.advance(a(2,5,28,32,false,false)).isEmpty());
        assertTrue(c.advance(a(3,5,28,32,false,false)).isEmpty());
        c.enter(1,2,0,5);
        assertTrue(c.advance(a(1,5,28,32,false,true)).isEmpty());
        c.interrupt();assertTrue(c.advance(a(2,5,28,32,false,false)).isEmpty());
        c.enter(2,2,0,5);
        assertTrue(c.advance(a(1,5,28,32,false,false)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->c.enter(1,2,0,6));
    }
}
