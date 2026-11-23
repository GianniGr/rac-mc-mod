package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopSceneLightSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopLightingProgramTest {
    private static final float[] I={1,0,0,0,1,0,0,0,1};
    private static final int[] NEUTRAL={128,128,128,128};

    @Test void rebasingRetainsSourceNonOrthonormalOffAxisBasis() {
        assertArrayEquals(I,Rac1ShopLightingProgram.environmentBasis(I,I,new float[]{0,0,5}),0);
        // Literal A/B expansion with d=(.6,0,.8); a "better" normalized rotation is WRONG.
        assertArrayEquals(new float[]{.8f,0,.6f,0,.8f,0,-.48f,0,.64f},
                Rac1ShopLightingProgram.environmentBasis(I,I,new float[]{3,0,4}),1e-6f);
        assertArrayEquals(new float[]{.8f,0,0,0,.8f,.48f,0,-.6f,.64f},
                Rac1ShopLightingProgram.environmentBasis(I,I,new float[]{0,3,4}),1e-6f);
    }
    @Test void usesViewAndModelRotationsWithoutFitScaleOrTranslation() {
        float[] z90={0,1,0,-1,0,0,0,0,1};
        assertArrayEquals(z90,Rac1ShopLightingProgram.environmentBasis(z90,I,new float[]{0,0,7}),0);
        assertArrayEquals(new float[]{-2,1,-3},
                Rac1ShopLightingProgram.toLight(z90,new float[]{1,2,3}),0);
        float[] scaled=I.clone(); scaled[0]=2.5f;
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ShopLightingProgram.environmentBasis(I,scaled,new float[]{0,0,7}));
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ShopLightingProgram.environmentBasis(I,I,new float[3]));
    }
    @Test void normalProgramHasLengthCorrectionBacklightAmbientAndMultipliersUnlikeMetal() {
        float[] directions={0,0,1,0,0,0,0,0,0};
        float[] colors={.5f,1,.25f,-.25f,0,0,0,0,0,0,0,0};
        int[] ambient={16,16,16};
        assertArrayEquals(new int[]{80,144,48,128},
                Rac1ShopLightingProgram.normal(0,0,2,directions,colors,ambient,128,NEUTRAL));
        assertArrayEquals(new int[]{32,48,24,128},
                Rac1ShopLightingProgram.normal(0,0,-2,directions,colors,ambient,128,NEUTRAL));
        assertArrayEquals(new int[]{40,144,0,64},
                Rac1ShopLightingProgram.normal(0,0,2,directions,colors,ambient,128,new int[]{64,128,0,64}));
        assertArrayEquals(new int[]{16,16,16,128},
                Rac1ShopLightingProgram.normal(1,0,0,directions,colors,ambient,128,NEUTRAL));
    }
    @Test void originalPopupKeyProducesLitFrontAndBackNotFlatNeutral128() {
        float[] directions={.42f,.7f,.577f,0,0,0,0,0,0};
        float[] colors={.9f,.9f,.6f,-.25f,0,0,0,0,0,0,0,0};
        assertArrayEquals(new int[]{82,82,60,128},
                Rac1ShopLightingProgram.normal(0,0,2,directions,colors,new int[]{16,16,16},128,NEUTRAL));
        assertArrayEquals(new int[]{32,32,27,128},
                Rac1ShopLightingProgram.normal(0,0,-2,directions,colors,new int[]{16,16,16},128,NEUTRAL));
    }
    @Test void normalPackingSaturatesAndRejectsUnprovenPrepackDomains() {
        float[] directions={0,0,1,0,0,0,0,0,0};
        float[] colors={4,4,4,0,0,0,0,0,0,0,0,0};
        assertArrayEquals(new int[]{255,255,255,255},
                Rac1ShopLightingProgram.normal(0,0,1,directions,colors,new int[3],128,new int[]{255,255,255,255}));
        colors[0]=Float.MAX_VALUE;
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ShopLightingProgram.normal(0,0,1,directions,colors,new int[3],128,NEUTRAL));
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ShopLightingProgram.normal(0,0,0,directions,new float[12],new int[3],128,NEUTRAL));
        assertThrows(IllegalArgumentException.class,
                () -> Rac1ShopLightingProgram.normal(1,0,0,new float[8],new float[12],new int[3],128,NEUTRAL));
    }
    @Test void fogIsPackedViewDepthNotRadialDistanceAndRttHasItsOwnInputs() {
        var world=new Rac1ShopSceneLightSource.Fog(105,127,180,0,245760,255,102);
        assertEquals(255,Rac1ShopLightingProgram.fogByte(-1,world));
        assertEquals(178,Rac1ShopLightingProgram.fogByte(122880,world));
        assertEquals(102,Rac1ShopLightingProgram.fogByte(245760,world));
        assertEquals(102,Rac1ShopLightingProgram.fogByte(999999,world));
        var rtt=new Rac1ShopSceneLightSource.Fog(105,127,180,0,524288,255,0);
        assertEquals(127,Rac1ShopLightingProgram.fogByte(262144,rtt));
        var lowNear=new Rac1ShopSceneLightSource.Fog(0,0,0,0,100,128,0);
        assertEquals(255,Rac1ShopLightingProgram.fogByte(-1000,lowNear));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopLightingProgram.fogByte(Float.NaN,world));
    }
}
