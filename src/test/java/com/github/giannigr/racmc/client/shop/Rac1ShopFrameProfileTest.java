package com.github.giannigr.racmc.client.shop;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopFrameProfileTest {
    @Test void boundedStorageAndRawNestedStageOffsetsAreDeterministic() {
        var time=new AtomicLong(100);
        var s=new Rac1ShopFrameProfile.Session(time::get);
        s.frameStart();
        time.set(120); s.add(Rac1ShopFrameProfile.Stage.READBACK_GL,110);
        time.set(150); s.frameEnd();
        time.set(180); s.frameStart(); s.frameEnd();
        var report=s.report();
        assertTrue(report.contains("READBACK_GL count=1 p50=10 p95=10 max=10"));
        assertTrue(report.contains("0,LOADING,READBACK_GL,10,10"));
        assertTrue(report.contains("FRAME_CADENCE count=1 p50=80"));
        for (int i=0;i<5000;i++) s.add(Rac1ShopFrameProfile.Stage.PRESENTER,180);
        assertTrue(s.report().contains("samples=4096 dropped=908"));
    }
    @Test void ordinaryDisabledPathDoesNotReadClockOrCreateSession() {
        assertEquals(0,Rac1ShopFrameProfile.begin());
        Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.COMPOSITION,0);
        Rac1ShopFrameProfile.frameStart(); Rac1ShopFrameProfile.frameEnd();
        assertEquals(0,Rac1ShopFrameProfile.begin());
    }
}
