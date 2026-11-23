package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.shop.Rac1ShopMotion;
import org.junit.jupiter.api.Test;
import static com.github.giannigr.racmc.shop.Rac1ShopMotion.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopLocalMotionTest {
    @Test void reproducesOldObserverBoundaryWhenServerCorrectsWorldTimeBackwards() {
        var old=new Rac1ShopMotion();
        old.update(true,1000*3);
        old.update(true,1001*3);
        assertThrows(IllegalArgumentException.class,() -> old.update(true,999*3));
    }

    @Test void localObserverKeepsOriginalUnfoldAcrossWorldTimeCorrections() {
        var local=new Rac1ShopLocalMotion();
        // Successive world times around a server correction, large jump, and frozen world time.
        long[] worldTimes={1000,1001,999,1000,80000,80000,80000,5,6,7,8,9,10,11,12};
        for (int tick=0;tick<worldTimes.length;tick++) {
            var stamp=local.tick(true); // Production observer deliberately does not pass world time.
            assertEquals(tick<14 ? UNFOLD:OPEN,stamp.phase(),"client tick "+tick);
            var display=local.display(0);
            if (tick<14) assertEquals(tick*3/60f,display.seconds(),1e-6);
            assertTrue(Float.isFinite(local.display(.5f).seconds()));
        }
        assertEquals(OPEN,local.display(0).phase());
    }

    @Test void closingDuringUnfoldFinishesUnfoldPowerOffAndFoldWithoutResettingClock() {
        var local=new Rac1ShopLocalMotion();
        local.tick(true);
        for (int tick=1;tick<=29;tick++) {
            var stamp=local.tick(false);
            assertEquals(tick<14 ? UNFOLD:tick<16 ? POWER_OFF:tick<30 ? FOLD:CLOSED,stamp.phase());
        }
        assertEquals(CLOSED,local.tick(false).phase()); // 40 + 8 + 40 reference frames.
        assertEquals(UNFOLD,local.tick(true).phase());
        assertEquals(0,local.display(0).seconds());
    }

    @Test void reentryWaitsForFoldAndIndependentOrReplacementObserversStartFresh() {
        var a=new Rac1ShopLocalMotion();
        var b=new Rac1ShopLocalMotion();
        a.tick(true);
        for (int i=0;i<14;i++) a.tick(true);
        assertEquals(POWER_OFF,a.tick(false).phase());
        for (int i=0;i<3;i++) a.tick(false);
        assertEquals(FOLD,a.tick(true).phase());
        assertEquals(UNFOLD,b.tick(true).phase());
        assertEquals(0,b.display(0).seconds(),"World/assets replacement creates a fresh local clock");
        for (int i=0;i<12;i++) a.tick(true);
        assertEquals(UNFOLD,a.tick(true).phase(),"Finish fold before a fresh full unfold");
        assertEquals(UNFOLD,b.tick(true).phase(),"Other shopper's clock is independent");
    }
}
