package com.github.giannigr.racmc.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1ClankActionEventTest {
    static Rac1ClankActionEvent event(long epoch,long serial,int phase) {
        return new Rac1ClankActionEvent(new UUID(0,36),epoch,serial,20,phase,43);
    }
    @Test void boundedPresentationWireRoundTripsAndRejectsInputOrPayloadExpansion()throws Exception {
        var e=event(1,2,7);assertEquals(48,e.encode().length);assertEquals(e,Rac1ClankActionEvent.decode(e.encode()));
        assertThrows(IOException.class,()->Rac1ClankActionEvent.decode(new byte[49]));
        assertThrows(IllegalArgumentException.class,()->new Rac1ClankActionEvent(e.actor(),1,2,20,7,49));
        assertThrows(IllegalArgumentException.class,()->event(1,2,36001));
    }
    @Test void consumesDuplicatesLateMediaAndOldOwnersWithoutCatchupAndExpiresDeferredEvents() {
        var c=new Rac1ClankActionInbox();var e=event(1,2,7);
        assertTrue(c.offer(e,20,1,false));assertFalse(c.offer(e,21,1,true));assertTrue(c.drain(21,1).isEmpty());
        assertTrue(c.offer(event(1,2,8),21,1,true));
        var p=c.drain(21,1);assertEquals(1,p.size());c.defer(p.getFirst());
        assertTrue(c.drain(24,1).isEmpty());
        assertTrue(c.offer(event(2,3,9),25,2,true));
        assertTrue(c.drain(25,1).isEmpty());assertFalse(c.offer(e,25,2,true));
        c.clear();assertTrue(c.offer(event(3,4,10),26,3,true));assertEquals(1,c.drain(26,3).size());
    }
    @Test void acceptedTouchdownIsACompletionFactNotALandingClipOrDuplicateTrigger() throws Exception {
        var c=new Rac1ClankActionInbox();var cue=event(1,2,7);
        var end=new Rac1ClankActionEvent(cue.actor(),1,2,20,7,0);
        assertEquals(end,Rac1ClankActionEvent.decode(end.encode()));
        assertTrue(c.offer(cue,20,1,true));assertTrue(c.offer(end,20,1,true));
        assertFalse(c.offer(end,20,1,true));assertFalse(c.offer(cue,20,1,true));
        assertEquals(List.of(cue,end),c.drain(20,1).stream().map(Rac1ClankActionInbox.Pending::event).toList());
    }
    @Test void freshEventWaitsForAnOlderIdleViewsFutureActionButNeverForMediaOrNewerOwner() {
        var p=new Rac1ClankActionInbox.Pending(event(1,2,7),20,3);
        assertTrue(Rac1ClankActionInbox.needsSnapshot(p,1,1,19,20));
        assertTrue(Rac1ClankActionInbox.needsSnapshot(p,1,2,19,21));
        assertFalse(Rac1ClankActionInbox.needsSnapshot(p,1,2,20,21));
        assertFalse(Rac1ClankActionInbox.needsSnapshot(p,1,1,19,22));
        assertFalse(Rac1ClankActionInbox.needsSnapshot(p,2,1,19,20));
        assertFalse(Rac1ClankActionInbox.needsSnapshot(p,1,3,19,20));
    }
}
