package com.github.giannigr.racmc.client.shop;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopSceneTest {
    @Test void monitorUsesActualBoneEdgesAndOriginalInsetsNotScreenRectangles() {
        float[] pose = new float[45 * 16];
        // Screen one: corner (10,20,30), edge A along z, edge B along y.
        pose[4 * 16 + 12] = 10; pose[4 * 16 + 13] = 20; pose[4 * 16 + 14] = 30;
        pose[5 * 16 + 12] = 10; pose[5 * 16 + 13] = 20; pose[5 * 16 + 14] = 32;
        pose[6 * 16 + 12] = 10; pose[6 * 16 + 13] = 23; pose[6 * 16 + 14] = 30;
        float[] original = pose.clone();
        var quad = Rac1ShopLayout.monitor(pose, 1);
        assertEquals(10, quad.corner().x()); assertEquals(20.03, quad.corner().y(), 1e-9);
        assertEquals(30.01, quad.corner().z(), 1e-9);
        assertEquals(1.94, quad.edgeA().z(), 1e-9); assertEquals(2.94, quad.edgeB().y(), 1e-9);
        assertArrayEquals(original, pose);
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopLayout.monitor(pose, 6));
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopLayout.monitor(new float[16], 0));
        assertThrows(IllegalArgumentException.class, () -> Rac1ShopLayout.monitor(new float[45 * 16], 0));
    }
    @Test void crtPowerShrinksAboutCenter() {
        var r = new Rac1ShopLayout.Rectangle(10, 20, 100, 50);
        assertEquals(new Rac1ShopLayout.Rectangle(35, 32.5, 50, 25), r.power(.5));
        assertEquals(new Rac1ShopLayout.Rectangle(60, 45, 0, 0), r.power(0));
        assertEquals(r, r.power(1));
        assertThrows(IllegalArgumentException.class, () -> r.power(Double.NaN));
    }
    @Test void resizeReloadLogoutReplacementAndFailedCreationDisposeExactlyOnce() throws Exception {
        List<Integer> closed = new ArrayList<>();
        var lifecycle = new Rac1ShopResources.Lifecycle();
        lifecycle.ensure("disc-a", 640, 480, o -> { o.own(() -> closed.add(1)); o.own(() -> closed.add(2)); });
        lifecycle.ensure("disc-a", 640, 480, o -> fail("No per-frame allocation"));
        lifecycle.ensure("disc-a", 800, 600, o -> o.own(() -> closed.add(3)));
        assertEquals(List.of(2, 1), closed);
        lifecycle.ensure("disc-b", 800, 600, o -> o.own(() -> closed.add(4)));
        lifecycle.reload(); lifecycle.logout();
        assertEquals(List.of(2, 1, 3, 4), closed);
        assertThrows(Exception.class, () -> lifecycle.ensure("disc-c", 800, 600, o -> {
            o.own(() -> closed.add(5)); throw new Exception("Injected allocation failure");
        }));
        assertEquals(List.of(2, 1, 3, 4, 5), closed);
        lifecycle.ensure("disc-c", 800, 600, o -> o.own(() -> closed.add(6)));
        lifecycle.close(); lifecycle.close();
        assertEquals(List.of(2, 1, 3, 4, 5, 6), closed);
    }
    @Test void oneCloseFailureDoesNotLeakOtherResources() throws Exception {
        List<Integer> closed = new ArrayList<>();
        var owner = new Rac1ShopResources();
        owner.own(() -> closed.add(1));
        owner.own(() -> { throw new Exception("Injected close failure"); });
        owner.own(() -> closed.add(3));
        assertThrows(Exception.class, owner::close);
        owner.close(); assertEquals(List.of(3, 1), closed);
    }
}
