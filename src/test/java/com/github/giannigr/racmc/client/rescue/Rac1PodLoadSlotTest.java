package com.github.giannigr.racmc.client.rescue;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.rescue.Rac1PodAssets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Rac1PodLoadSlotTest {
    @Test
    void menuTickPreparesAndWarmLookupHasNoJobsOrGpuCalls() {
        var slot = new Rac1PodLoadSlot<Integer, String, String>();
        var future = new CompletableFuture<String>();
        var starts = new AtomicInteger();
        var uploads = new AtomicInteger();
        var releases = new ArrayList<String>();
        assertNull(slot.lookup(key -> true));
        slot.tick(null, key -> true, key -> { throw new AssertionError("not ready"); },
                art -> art, releases::add, failure -> fail(failure));
        slot.tick(1, key -> true, key -> { starts.incrementAndGet(); return future; },
                art -> { uploads.incrementAndGet(); return art; }, releases::add, failure -> fail(failure));
        assertNull(slot.lookup(key -> true));
        future.complete("pod");
        slot.tick(1, key -> true, key -> fail("duplicate job"),
                art -> { uploads.incrementAndGet(); return art; }, releases::add, failure -> fail(failure));
        for (int i = 0; i < 100; i++) assertEquals("pod", slot.lookup(key -> key == 1));
        assertEquals(1, starts.get());
        assertEquals(1, uploads.get());
        assertTrue(releases.isEmpty());
        assertNull(slot.lookup(key -> false), "stale immediately, even before the cleanup tick");
        slot.tick(2, key -> true, key -> CompletableFuture.completedFuture("fresh"),
                art -> art, releases::add, failure -> fail(failure));
        assertEquals(List.of("pod"), releases);
    }

    @Test
    void obsoleteJobOccupiesTheOnlySlotAndCannotPublish() {
        var slot = new Rac1PodLoadSlot<Integer, String, String>();
        var pendingA = new CompletableFuture<String>();
        var pendingB = new CompletableFuture<String>();
        slot.tick(1, key -> true, key -> pendingA, art -> fail(), art -> fail(), failure -> fail(failure));
        for (int n = 2; n < 100; n++) {
            slot.tick(n, key -> true, key -> fail("older work still running"), art -> fail(), art -> fail(),
                    failure -> fail(failure));
        }
        pendingA.complete("stale");
        slot.tick(100, key -> true, key -> pendingB, art -> fail("stale upload"), art -> fail(),
                failure -> fail(failure));
        pendingB.complete("latest");
        slot.tick(100, key -> true, key -> fail("duplicate"), art -> art, art -> fail(), failure -> fail(failure));
        assertEquals("latest", slot.lookup(key -> key == 100));
    }

    @Test
    void failedGenerationDoesNotSpinAndInvalidationDuringUploadReleasesIt() {
        var slot = new Rac1PodLoadSlot<Integer, String, String>();
        var errors = new ArrayList<Throwable>();
        var released = new ArrayList<String>();
        var epoch = new AtomicInteger(1);
        var failure = new IllegalStateException("unavailable art");
        slot.tick(1, key -> key == epoch.get(), key -> CompletableFuture.failedFuture(failure),
                art -> fail(), released::add, errors::add);
        for (int i = 0; i < 10; i++) slot.tick(1, key -> key == epoch.get(), key -> fail("retry storm"),
                art -> fail(), released::add, errors::add);
        assertEquals(List.of(failure), errors);
        epoch.set(2);
        slot.tick(2, key -> key == epoch.get(), key -> CompletableFuture.completedFuture("obsolete"),
                art -> fail(), released::add, errors::add);
        slot.tick(2, key -> key == epoch.get(), key -> fail(), art -> { epoch.set(3); return art; },
                released::add, errors::add);
        assertNull(slot.lookup(key -> key == epoch.get()));
        assertEquals(List.of("obsolete"), released);
    }

    @Test
    void controlledPreparationAndWarmLookupProfile() {
        var assets = new Rac1PodAssets("synthetic", posed(1515, 2172, 2004, 4), posed(1365, 2432, 2500, 2));
        var slot = new Rac1PodLoadSlot<Integer, Rac1PodClientResources.Prepared, Rac1PodClientResources.Prepared>();
        long start = System.nanoTime();
        var result = CompletableFuture.supplyAsync(() -> {
            try {
                return Rac1PodClientResources.Prepared.of(assets);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
        slot.tick(1, key -> true, key -> result, art -> art, art -> fail(), failure -> fail(failure));
        var expected = result.join();
        long preparedNanos = System.nanoTime() - start;
        slot.tick(1, key -> true, key -> fail("duplicate"), art -> art, art -> fail(), failure -> fail(failure));
        var valid = (java.util.function.Predicate<Integer>) key -> key == 1;
        for (int i = 0; i < 10_000; i++) assertSame(expected, slot.lookup(valid));
        int iterations = 1_000_000;
        start = System.nanoTime();
        int received = 0;
        for (int i = 0; i < iterations; i++) if (slot.lookup(valid) == expected) received++;
        long lookupNanos = System.nanoTime() - start;
        assertEquals(iterations, received);
        assertEquals(46, expected.full().canopy().triangleCount());
        System.out.printf("pod fixture: first background dispatch + pose fitting %.3f ms; warm slot loop %.1f ns/lookup%n",
                preparedNanos / 1_000_000.0, lookupNanos / (double) iterations);
    }

    private static Rac1PodAssets.PosedModel posed(int id, int vertices, int triangles, int materials) {
        float[] pos = new float[vertices * 3], norm = new float[pos.length];
        for (int i = 0; i < vertices; i++) {
            pos[i * 3] = .5f + (i % 18) / 20f;
            pos[i * 3 + 1] = .4f + (i % 7) / 20f;
            pos[i * 3 + 2] = (i % 9 - 4) / 10f;
            norm[i * 3 + 1] = 1;
        }
        int[] indices = new int[triangles * 3];
        for (int i = 0; i < indices.length; i++) indices[i] = i % vertices;
        var mesh = new Rac1SkinnedMesh(pos, norm, new float[vertices * 2], indices, new int[triangles],
                new int[vertices * 3], new float[vertices * 3]);
        var asset = new Rac1AnimatedAsset(id, "fixture", mesh,
                new Rac1Skeleton(new float[0], new int[0], new float[0]),
                new Rac1Animation[0], new Rac1Texture[materials]);
        return new Rac1PodAssets.PosedModel(asset, pos, norm);
    }
}
