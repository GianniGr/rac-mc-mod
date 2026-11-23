package com.github.giannigr.racmc.client.belt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1BeltVisualTest {
    @Test void itemFitCentersOriginalBoundsWithinTheSlotWithoutNonuniformStretch() {
        var fit = Rac1BoltGrabberPickupFit.transform(new float[]{2, 0, -2, 8, 2, 1, 4, 1, 0});
        var minimum = fit.transformPosition(new Vector3f(2, 0, -2));
        var maximum = fit.transformPosition(new Vector3f(8, 2, 1));
        assertEquals(.82f, minimum.distance(maximum), .0001f);
        var center = fit.transformPosition(new Vector3f(5, 1, -.5f));
        assertEquals(.5f, center.x, .0001f);
        assertEquals(.5f, center.y, .0001f);
        assertEquals(.5f, center.z, .0001f);
        assertTrue(minimum.x >= 0 && maximum.x <= 1);
        assertThrows(IllegalArgumentException.class, () -> Rac1BoltGrabberPickupFit.transform(new float[9]));
    }

    @Test void waistMountKeepsBackFaceOnArmorAndPreservesHandednessAndBodyUp() {
        var fit = Rac1BeltWaist.fit(new float[]{-2, -1, -3, 2, -1, 3, -2, 1, 3});
        for (var host : Rac1BeltWaist.Clearance.values()) {
            var transform = fit.local(host);
            assertTrue(transform.determinant() > 0);
            var frontNormal = transform.transformDirection(new Vector3f(0, 0, 1)).normalize();
            assertEquals(-1, frontNormal.z, .0001f);
            assertEquals(-1, transform.transformDirection(new Vector3f(0, 1, 0)).normalize().y, .0001f);
            float expect = switch (host) {
                case SKIN -> 2f;
                case JACKET -> 2.25f;
                case LEGGINGS -> 2.5f;
                case CHESTPLATE -> 3f;
            };
            var back = transform.transformPosition(new Vector3f(0, 0, -3));
            assertEquals(0, back.x, .0001f);
            assertEquals(10f / 16, back.y, .0001f);
            assertEquals((-expect + .08f) / 16, back.z, .0001f);
            assertTrue(back.z - transform.transformPosition(new Vector3f(0, 0, 3)).z <= 1.8001f / 16);
        }
        assertThrows(IllegalArgumentException.class, () -> Rac1BeltWaist.fit(new float[9]));
    }

    @Test void selectionOrReloadInvalidatesWarmArtAndDiscardsStalePreparation() {
        var slot = new Rac1BeltArtSlot<Integer, String, String>();
        var first = new CompletableFuture<String>();
        var second = new CompletableFuture<String>();
        var started = new ArrayList<Integer>();
        var installed = new ArrayList<String>();
        var released = new ArrayList<String>();
        java.util.function.Consumer<Integer> tick = generation -> slot.tick(generation, key -> {
            started.add(key);
            return key == 1 ? first : second;
        }, (key, mesh) -> {
            installed.add(mesh);
            return mesh;
        }, released::add, failure -> fail(failure));
        tick.accept(1);
        tick.accept(2);
        assertEquals(List.of(1), started);
        first.complete("stale");
        tick.accept(2);
        assertEquals(List.of(1, 2), started);
        assertEquals(List.of(), installed);
        second.complete("verified");
        tick.accept(2);
        assertEquals("verified", slot.get(2));
        assertNull(slot.get(3)); // no stale warm frame while tick has not yet processed reload.
        tick.accept(null);
        assertEquals(List.of("verified"), released);
        assertNull(slot.get(2));
    }

    @Test void failedLocalReadDoesNotSpawnRetriesEveryTickOrShowReplacementArt() {
        var slot = new Rac1BeltArtSlot<Integer, String, String>();
        var errors = new ArrayList<RuntimeException>();
        var started = new ArrayList<Integer>();
        for (int tick = 0; tick < 4; tick++) slot.tick(1, key -> {
            started.add(key);
            return CompletableFuture.failedFuture(new IllegalArgumentException("missing class"));
        }, (key, art) -> fail("Unverified install"), art -> fail("Unexpected close"), errors::add);
        assertEquals(List.of(1), started);
        assertEquals(1, errors.size());
        assertNull(slot.get(1));
    }
}
