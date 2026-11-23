package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopMetalProgramTest {
    private float[] identity() { return new float[]{1,0,0,0,1,0,0,0,1}; }
    @Test void sourceMetalLightAndSphereMapDoNotBecomeFlatPopupColor() {
        var sample=Rac1ShopMetalProgram.evaluate(0,0,1,new float[]{0,0,1,0,0,0,0,0,0},
                new float[]{1,.5f,.25f,0,0,0,0,0,0},identity(),64);
        assertEquals(new Rac1ShopMetalProgram.Sample((short)2048,(short)2048,144,128,120,64),sample);
        assertEquals(.5f,sample.textureS()); assertEquals(.5f,sample.textureT());
    }
    @Test void metalDoesNotNormalizeSkinNormalOrClampOverflowRgbAndUv() {
        var sample=Rac1ShopMetalProgram.evaluate(2,-1,0,new float[]{1,0,0,0,0,0,0,0,0},
                new float[]{1,2,4,0,0,0,0,0,0},identity(),128);
        assertEquals(new Rac1ShopMetalProgram.Sample((short)6144,(short)0,176,240,112,128),sample);
        assertEquals(1.5f,sample.textureS());
    }
    @Test void individualLightTruncationAndNegativeHalfwordShiftAreDistinct() {
        var sample=Rac1ShopMetalProgram.evaluate(0,0,1,new float[]{0,0,1,0,0,1,0,0,1},
                new float[]{.02f,.02f,.02f,.02f,.02f,.02f,.02f,.02f,.02f},identity(),7);
        assertEquals(112,sample.red()); assertEquals(112,sample.green()); assertEquals(112,sample.blue());
        sample=Rac1ShopMetalProgram.evaluate(-2,-1.0003f,0,new float[9],new float[9],identity(),0);
        assertEquals(-2048,sample.s()); assertEquals(-1,sample.t());
    }
    @Test void originalBasisOrderAndSignedPackingAreExplicit() {
        var sample=Rac1ShopMetalProgram.evaluate(1,2,3,new float[9],new float[9],
                new float[]{1,2,0,3,4,0,5,6,0},5);
        assertEquals(-18432,sample.s()); assertEquals(-6144,sample.t());
        assertEquals(112,sample.red());
    }
    @Test void shineGateConsumesOriginalPackedDepthAndSuppliedDistance() {
        assertEquals(128,Rac1ShopMetalProgram.shineAlpha(24,8*1024));
        assertEquals(64,Rac1ShopMetalProgram.shineAlpha(24,16*1024));
        assertEquals(0,Rac1ShopMetalProgram.shineAlpha(24,24*1024));
        assertEquals(0,Rac1ShopMetalProgram.shineAlpha(24,24*1024+1));
        assertEquals(0,Rac1ShopMetalProgram.shineAlpha(24,24*1024-127));
        assertEquals(1,Rac1ShopMetalProgram.shineAlpha(24,24*1024-128));
        assertEquals(128,Rac1ShopMetalProgram.shineAlpha(24,-1));
        assertEquals(0,Rac1ShopMetalProgram.shineAlpha(0,-10000));
    }
    @Test void unboundedOrMissingSourceInputsFailRatherThanInventLights() {
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(0,0,1,null,new float[9],identity(),1));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(Float.NaN,0,1,new float[9],new float[9],identity(),1));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(0,0,1,new float[8],new float[9],identity(),1));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(0,0,1,new float[9],new float[9],identity(),129));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(0,0,1,new float[9],
                new float[]{-1,0,0,0,0,0,0,0,0},identity(),1));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.evaluate(Float.MAX_VALUE,0,1,
                new float[9],new float[9],identity(),1));
        assertThrows(IllegalArgumentException.class,()->Rac1ShopMetalProgram.shineAlpha(256,0));
    }
}
