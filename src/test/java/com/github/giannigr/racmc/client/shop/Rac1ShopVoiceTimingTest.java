package com.github.giannigr.racmc.client.shop;

import java.io.IOException;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopVoiceTimingTest {
    private static final int[] N={0x2aeec8,0x2af228,0x2aef68,0x2aefd0,0x2af010,0x2af01c,0x2af028,
            0x2af228,0x2af228,0x2af228,0x2af0b4,0x2af1b0,0x2af138};
    private static final int[] P={0x2b0158,0x2b04b8,0x2b01f8,0x2b0260,0x2b02a0,0x2b02ac,0x2b02b8,
            0x2b04b8,0x2b04b8,0x2b04b8,0x2b0344,0x2b0440,0x2b03c8};
    private static byte[] bytes(int... values) {
        var b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v:values) b.putInt(v);
        return b.array();
    }
    @Test void independentDiscStateTargetsAndSixRawThresholdBindingsMatchNumericEvidence() throws Exception {
        for (boolean pal:new boolean[]{false,true}) {
            int[] expected=pal ? P:N;
            byte[] targets=bytes(expected),thresholds=bytes(100,100,200,100,44,100);
            var timing=Rac1ShopVoiceTiming.read(targets,thresholds,pal);
            targets[0]^=1; thresholds[0]^=1;
            for (int i=0;i<13;i++) assertEquals(expected[i],timing.stateTarget(i));
            assertEquals(new Rac1ShopVoiceTiming.Binding(4,4,10000,100),timing.binding(4,0));
            assertEquals(new Rac1ShopVoiceTiming.Binding(4,7,10018,100),timing.binding(4,1));
            assertEquals(new Rac1ShopVoiceTiming.Binding(5,5,10006,200),timing.binding(5,0));
            assertEquals(new Rac1ShopVoiceTiming.Binding(5,8,10024,100),timing.binding(5,1));
            assertEquals(new Rac1ShopVoiceTiming.Binding(6,6,10012,44),timing.binding(6,0));
            assertEquals(new Rac1ShopVoiceTiming.Binding(6,9,10030,100),timing.binding(6,1));
            for (int state:new int[]{-1,13,Integer.MAX_VALUE})
                assertThrows(IllegalArgumentException.class,() -> timing.stateTarget(state));
            assertThrows(IllegalArgumentException.class,() -> timing.binding(3,0));
            assertThrows(IllegalArgumentException.class,() -> timing.binding(7,0));
            assertThrows(IllegalArgumentException.class,() -> timing.binding(4,2));
        }
    }
    @Test void everyChangedWordAndWrongRegionOrExtentFailsClosed() {
        for (boolean pal:new boolean[]{false,true}) {
            byte[] targets=bytes(pal ? P:N),thresholds=bytes(100,100,200,100,44,100);
            assertThrows(IOException.class,() -> Rac1ShopVoiceTiming.read(targets,thresholds,!pal));
            assertThrows(IOException.class,() -> Rac1ShopVoiceTiming.read(new byte[56],thresholds,pal));
            assertThrows(IOException.class,() -> Rac1ShopVoiceTiming.read(targets,new byte[20],pal));
            for (int i=0;i<13;i++) {
                byte[] corrupt=targets.clone(); corrupt[i*4]^=1;
                assertThrows(IOException.class,() -> Rac1ShopVoiceTiming.read(corrupt,thresholds,pal));
            }
            for (int i=0;i<6;i++) {
                byte[] corrupt=thresholds.clone(); corrupt[i*4]^=1;
                assertThrows(IOException.class,() -> Rac1ShopVoiceTiming.read(targets,corrupt,pal));
            }
        }
    }
}
