package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Rac1HydroDiagnosticsTest {
    @Test void disabledIsSilentLazyAndDoesNotRetainAState() {
        var out=new ArrayList<String>();var log=new Rac1HydroDiagnostics(false,4,out::add);
        assertFalse(log.transition("input","held",()->{fail("Disabled diagnostic evaluated details");return "";}));
        assertEquals(0,log.entries());assertTrue(out.isEmpty());
    }
    @Test void transitionsAreDeduplicatedAndHardCappedWithoutResetOrPerTickFlood() {
        var out=new ArrayList<String>();var log=new Rac1HydroDiagnostics(true,4,out::add);
        assertTrue(log.transition("input","Sprint/W",()->"physicalSprint=true forward=1"));
        for(int i=0;i<100;i++)assertFalse(log.transition("input","Sprint/W",()->{fail("Repeated state");return "";}));
        assertTrue(log.transition("input","Sprint/noW",()->"physicalSprint=true forward=0"));
        assertTrue(log.transition("stage","THRUST",()->"controlled=true"));
        assertTrue(log.transition("stream","nonzero",()->"samples=2048 peak=2000"));
        assertFalse(log.transition("stage","DRIFT",()->{fail("Cap exceeded");return "";}));
        assertEquals(4,out.size());assertTrue(out.getLast().contains("cap reached"));
    }
}
