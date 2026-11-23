package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopHologramTest {
    @Test void asymmetricWeaponFitsPadAxisWithoutChangingDepthHeightOrInput() {
        float[] source={0,0,1.5f};
        var fitted=Rac1ShopHologram.centerHorizontal(source,-.588899f,.16711444f,0);
        assertEquals(.21089228f,fitted[1],1e-6);
        assertEquals(0,(-.588899f+fitted[1]+.16711444f+fitted[1])*.5f,1e-6);
        assertEquals(source[0],fitted[0]); assertEquals(source[2],fitted[2]);
        assertArrayEquals(new float[]{0,0,1.5f},source);
    }
    @Test void centeredOrInvalidExtentCannotSilentlyProduceAnOffset() {
        assertArrayEquals(new float[]{-1,.05f,1.1f},
                Rac1ShopHologram.centerHorizontal(new float[]{-1,.05f,1.1f},-.25f,.35f,.05f),1e-6f);
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopHologram.centerHorizontal(new float[3],Float.NaN,1,0));
        assertThrows(IllegalArgumentException.class,() -> Rac1ShopHologram.centerHorizontal(new float[3],2,1,0));
    }
}
