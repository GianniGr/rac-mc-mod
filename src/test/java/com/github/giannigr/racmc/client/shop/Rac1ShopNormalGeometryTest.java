package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopNormalGeometryTest {
    @Test void reversibleUnitsRecoverEverySigned16ValueWithoutTruncatingScaledCoordinates() {
        for (float scale:new float[]{.03704023361206055f,.013582159765064716f,.16666667f,1f})
            for (int v=Short.MIN_VALUE;v<=Short.MAX_VALUE;v++)
                assertEquals(v,Rac1ShopNormalGeometry.recover(v*scale/1024f,scale));
    }
    @Test void malformedOrNonSourceUnitsFailInsteadOfRoundingAnArbitraryPose() {
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopNormalGeometry.recover(.12345f,1));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopNormalGeometry.recover(Float.NaN,1));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopNormalGeometry.recover(1,0));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopNormalGeometry.recover(100,1));
    }
}
