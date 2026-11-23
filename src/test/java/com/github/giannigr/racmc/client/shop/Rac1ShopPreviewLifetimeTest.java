package com.github.giannigr.racmc.client.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopPreviewLifetimeTest {
    @Test void deathSpectatorWorldVendorReachAndResourceLossAllCloseIndependently() {
        assertTrue(new Rac1ShopPreviewLifetime(true,false,true,true,true,true,true,true).open());
        for (int changed=0;changed<8;changed++) {
            boolean[] v={true,false,true,true,true,true,true,true}; v[changed]=!v[changed];
            assertFalse(new Rac1ShopPreviewLifetime(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]).open(),"gate"+changed);
        }
    }
    @Test void closingOneViewerCannotCloseAnotherSnapshot() {
        var first=new Rac1ShopPreviewLifetime(true,false,true,true,true,true,true,true);
        var second=new Rac1ShopPreviewLifetime(false,false,true,true,true,true,true,true);
        assertFalse(second.open()); assertTrue(first.open());
    }
}
