package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopReadiness;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.client.shop.Rac1ShopMaterials.*;

class Rac1ShopProjectionMaterialTest {
    @Test void originalOppositeCornerProjectionAndPowerUseSuppliedCameraWithoutFovGuess() {
        var quad = new Rac1ShopLayout.Quad(new Rac1ShopLayout.Point(-.5,.5,0),
                new Rac1ShopLayout.Point(1,0,0), new Rac1ShopLayout.Point(0,-1,0));
        var rectangle = Rac1ShopProjection.project(quad,new Matrix4f(),512,416);
        assertEquals(new Rac1ShopLayout.Rectangle(128,104,256,208),rectangle);
        assertEquals(new Rac1ShopLayout.Rectangle(192,156,128,104),rectangle.power(.5));
        assertEquals(3.8f,Rac1ShopProjection.originalLocalEye().x);
        assertEquals(-1,Rac1ShopProjection.originalLocalForward().x);
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopProjection.project(quad,new Matrix4f().m33(-1),512,416));
    }
    @Test void gsBlendEquationsDoNotMistakeFixedNoiseForTextureAlphaOrClampSampling() {
        assertEquals(Blend.FIXED_ADD,blend(0x68));
        assertEquals(.4,rgb(blend(0x68),.8,.2,0,32),1e-9); // texture alpha0 must not remove noise RGB
        assertEquals(.35,rgb(blend(0x64),.8,.2,1,32),1e-9);
        assertEquals(.5,rgb(blend(0x44),.8,.2,.5,128),1e-9);
        assertEquals(Address.REPEAT,Pass.LED_NOISE.sampler().address());
        assertEquals(Filter.POINT,Pass.LED_NOISE.sampler().filter());
        assertEquals(Filter.LINEAR,Pass.PANEL_NOISE.sampler().filter());
        assertThrows(IllegalArgumentException.class, () -> blend(0));
    }
    @Test void fullReadinessNeverAcceptsMissingAssetsOrTheUnfinishedSceneBackend() {
        var report = Rac1ShopReadiness.inspect(null,null);
        assertFalse(report.ready()); assertTrue(report.missingResources().contains("shop_sidecar"));
        assertTrue(report.pendingSceneComponents().contains("verified_vendor_audio_UNBOUND"));
        assertThrows(IllegalStateException.class,report::requireReady);
    }
}
