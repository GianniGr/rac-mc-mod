package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.belt.Rac1BoltGrabberAsset;
import com.github.giannigr.racmc.asset.belt.Rac1BoltGrabberPickupAsset;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.belt.Rac1BeltWaist;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in claimed media verification: one exact core/asset (L01/618 or L15/1388) per attempt. */
class Rac1BoltGrabberDiscTest {
    @TempDir Path scratch;

    @Test
    @EnabledIfEnvironmentVariable(named = "RACMC_BOLT_WORN_NTSC", matches = ".+")
    void verifiedNtscNovalisClass618() throws Exception {
        probe("RACMC_BOLT_WORN_NTSC", Rac1DiscRevision.NTSC_U_V1_00, false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RACMC_BOLT_WORN_PAL", matches = ".+")
    void verifiedPalNovalisClass618() throws Exception {
        probe("RACMC_BOLT_WORN_PAL", Rac1DiscRevision.PAL_V2_00, false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RACMC_BOLT_PICKUP_NTSC", matches = ".+")
    void verifiedNtscQuartuClass1388() throws Exception {
        probe("RACMC_BOLT_PICKUP_NTSC", Rac1DiscRevision.NTSC_U_V1_00, true);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RACMC_BOLT_PICKUP_PAL", matches = ".+")
    void verifiedPalQuartuClass1388() throws Exception {
        probe("RACMC_BOLT_PICKUP_PAL", Rac1DiscRevision.PAL_V2_00, true);
    }

    private void probe(String variable, Rac1DiscRevision revision, boolean pickup) throws Exception {
        Path path = Path.of(System.getenv(variable));
        var profile = Rac1DiscCatalog.supported().profiles().stream()
                .filter(candidate -> candidate.revision() == revision).findFirst().orElseThrow();
        try (var media = new Rac1MediaManager(new Rac1DiscValidator(), scratch.resolve("media.properties"))) {
            media.select(path);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            while (media.completedAttempts() == 0 && System.nanoTime() < until) Thread.sleep(20);
            assertTrue(media.isReady(), "Complete owned-disc validation did not reach READY");
            try (var lease = media.lease(path, Rac1DiscIdentity.key(revision, profile))) {
                assertEquals(revision, lease.revision());
                if (pickup) {
                    var asset = Rac1BoltGrabberPickupAsset.read(lease);
                    assertEquals(1388, asset.classNumber());
                    assertTrue(Arrays.stream(asset.mesh().materials()).allMatch(slot ->
                            slot >= 0 && slot < asset.textures().length));
                    System.out.printf("%s L15 pickup 1388: jointless vertices=%d triangles=%d materials=%d "
                                    + "textures=%s bounds=%s%n",
                            revision.name(), asset.mesh().vertexCount(),
                            asset.mesh().triangleCount(), asset.textures().length,
                            Arrays.stream(asset.textures()).map(t -> t.width() + "x" + t.height()).toList(),
                            Arrays.toString(Rac1ImportedItemPose.bounds(asset.mesh().positions())));
                    assertEquals(1, media.completedAttempts());
                    return;
                }
                var asset = Rac1BoltGrabberAsset.read(lease);
                assertEquals(618, asset.classNumber());
                var neutral = Rac1ImportedItemPose.sample(asset, 0, 0);
                assertEquals(asset.mesh().vertexCount() * 3, neutral.positions().length);
                var mount = Rac1BeltWaist.fit(neutral.positions());
                float[] bounds = Rac1ImportedItemPose.bounds(neutral.positions());
                assertTrue((bounds[3] - bounds[0]) * mount.scale() > .20f,
                        "Put the broad face across Steve's waist, not edge-on into the torso");
                System.out.printf("%s L01 worn 618: joints=%d vertices=%d triangles=%d materials=%d clips=%s "
                                + "neutralBounds=%s waistScale=%g%n",
                        revision.name(), asset.skeleton().jointCount(), asset.mesh().vertexCount(),
                        asset.mesh().triangleCount(), asset.textures().length,
                        Arrays.stream(asset.animations()).map(a -> a.name() + ':' + a.keyCount()).toList(),
                        Arrays.toString(bounds), mount.scale());
            }
            assertEquals(1, media.completedAttempts());
        }
    }
}
