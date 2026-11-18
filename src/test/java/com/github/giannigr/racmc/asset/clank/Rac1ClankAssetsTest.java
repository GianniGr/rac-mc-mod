package com.github.giannigr.racmc.asset.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.Rac1CacheCodec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class Rac1ClankAssetsTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path directory;

    @Test void publicModelEncodingOneCompatibilityIsPinnedIndependentlyOfEnvelopes() throws Exception {
        byte[] payload = Rac1CacheCodec.encodeAnimatedModel(ClankFixtures.model(601));
        assertEquals(1, Rac1ClankSidecarCodec.REQUIRED_MODEL_ENCODING);
        assertEquals(13511, payload.length);
        assertEquals("98a4f06394b533045885ebc8edb6f51ae3aeb398d9653a0a0ef54063ff1c73eb",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(payload)));
        assertArrayEquals(payload, Rac1CacheCodec.encodeAnimatedModel(
                Rac1CacheCodec.decodeAnimatedModel(payload)));
    }
    @Test void exactRolesAndSourceTimingRoundTrip() throws Exception {
        var source = ClankFixtures.bundle();
        var decoded = Rac1ClankSidecarCodec.decode(Rac1ClankSidecarCodec.encode(source), source.discKey());
        for (var role : Rac1ClankAssets.Role.values()) {
            assertArrayEquals(source.encodedModel(role), decoded.encodedModel(role));
            assertEquals(role.classNumber(), decoded.copyModel(role).classNumber());
            assertTrue(decoded.copyModel(role).animations()[0].sourceTiming().available());
        }
    }
    @Test void bundleOwnsInputAndEveryOutputArray() throws Exception {
        var model = ClankFixtures.model(601);
        var assets = new Rac1ClankAssets(ClankFixtures.disc(0), model,
                ClankFixtures.model(607), ClankFixtures.model(608));
        model.mesh().positions()[0] = 50;
        assets.copyModel(Rac1ClankAssets.Role.CLANK).mesh().positions()[0] = 99;
        assets.encodedModel(Rac1ClankAssets.Role.CLANK)[0] = 0;
        assertEquals(0, assets.copyModel(Rac1ClankAssets.Role.CLANK).mesh().positions()[0]);
    }
    @Test void playable87WrongPackAndMissingClipsFail() {
        assertThrows(IOException.class, () -> new Rac1ClankAssets(ClankFixtures.disc(0),
                ClankFixtures.model(87), ClankFixtures.model(607), ClankFixtures.model(608)));
        assertThrows(IOException.class, () -> new Rac1ClankAssets(ClankFixtures.disc(0),
                ClankFixtures.model(601), ClankFixtures.model(608), ClankFixtures.model(607)));
        var broken = ClankFixtures.model(607);
        broken.animations()[6] = broken.animations()[1];
        assertThrows(IOException.class, () -> new Rac1ClankAssets(ClankFixtures.disc(0),
                ClankFixtures.model(601), broken, ClankFixtures.model(608)));
    }
    @Test void missingAntennaAndTimingFail() {
        var a = ClankFixtures.model(601);
        var noChains = new Rac1AnimatedAsset(601, a.name(), a.mesh(), a.skeleton(),
                a.animations(), a.textures());
        assertThrows(IOException.class, () -> new Rac1ClankAssets(ClankFixtures.disc(0),
                noChains, ClankFixtures.model(607), ClankFixtures.model(608)));
        var c = a.animations()[0];
        a.animations()[0] = new Rac1Animation(c.name(), c.times(), c.loop(), c.rotations(),
                c.translationKeys(), c.translationJoints(), c.translationValues(), c.scaleKeys(),
                c.scaleJoints(), c.scaleValues(), c.scaleInherited());
        assertThrows(IOException.class, () -> new Rac1ClankAssets(ClankFixtures.disc(0),
                a, ClankFixtures.model(607), ClankFixtures.model(608)));
    }
    @Test void wrongDiscCorruptionTruncationTrailingAndUnknownIdentityFail() throws Exception {
        var a = ClankFixtures.bundle();
        byte[] valid = Rac1ClankSidecarCodec.encode(a);
        assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(valid, ClankFixtures.disc(1)));
        assertThrows(IllegalArgumentException.class, () -> new Rac1ClankAssets("private/path",
                ClankFixtures.model(601), ClankFixtures.model(607), ClankFixtures.model(608)));
        for (int length : new int[] {0, 10, 31, 32, valid.length - 1})
            assertThrows(IOException.class,
                    () -> Rac1ClankSidecarCodec.decode(Arrays.copyOf(valid, length), a.discKey()));
        for (int offset : new int[] {0, 4, 8, 12, 16, 20, 24, 28, 35, valid.length - 1}) {
            byte[] bad = valid.clone(); bad[offset] ^= 1;
            assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(bad, a.discKey()));
        }
        assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(
                Arrays.copyOf(valid, valid.length + 1), a.discKey()));
    }
    @Test void repairedEnvelopeCannotHideBadRolesOrModelLengths() throws Exception {
        var a = ClankFixtures.bundle();
        byte[] bytes = Rac1ClankSidecarCodec.encode(a);
        int firstSection = 32 + a.discKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        for (int value : new int[] {-1, 0, Integer.MAX_VALUE, Rac1CacheCodec.MAX_MODEL_BYTES + 1}) {
            byte[] bad = bytes.clone();
            ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(firstSection + 4, value);
            repair(bad);
            assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(bad, a.discKey()));
        }
        byte[] bad = bytes.clone();
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(firstSection, 87);
        repair(bad);
        assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(bad, a.discKey()));
        byte[] badSection = bytes.clone();
        badSection[firstSection + 12] ^= 1;
        repair(badSection); // repaired envelope still cannot bypass per-model CRC
        assertThrows(IOException.class, () -> Rac1ClankSidecarCodec.decode(badSection, a.discKey()));
    }
    private static void repair(byte[] bytes) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(28, 0);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(28, Rac1CacheCodec.checksum(bytes));
    }
    @Test void aggregateBoundBeforeReadingAndStagingNeverUsed() throws Exception {
        var a = ClankFixtures.bundle();
        var store = new Rac1ClankSidecarStore(directory);
        Path target = store.path(a.discKey());
        try (var file = new java.io.RandomAccessFile(target.toFile(), "rw")) {
            file.setLength(Rac1ClankSidecarCodec.MAX_BYTES + 1L);
        }
        assertThrows(IOException.class, () -> store.read(a.discKey()));
        Files.write(directory.resolve("unrelated.staging"), new byte[] {1, 2, 3});
        store.write(a);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(directory.resolve("unrelated.staging")));
        assertEquals(a.discKey(), store.read(a.discKey()).discKey());
    }
    @Test void atomicReplacementAndFailedPublishPreservePriorData() throws Exception {
        var a = ClankFixtures.bundle();
        var store = new Rac1ClankSidecarStore(directory);
        store.write(a);
        byte[] prior = Files.readAllBytes(store.path(a.discKey()));
        store.write(a);
        assertArrayEquals(prior, Files.readAllBytes(store.path(a.discKey())));
        Path blocked = directory.resolve("blocked");
        Files.write(blocked, new byte[] {99});
        assertThrows(IOException.class, () -> new Rac1ClankSidecarStore(blocked).write(a));
        assertArrayEquals(prior, Files.readAllBytes(store.path(a.discKey())));
    }
    @Test void failedStagingReadbackKeepsGoodCacheAndRetainsEvidence() throws Exception {
        var a = ClankFixtures.bundle();
        var store = new Rac1ClankSidecarStore(directory);
        store.write(a);
        byte[] prior = Files.readAllBytes(store.path(a.discKey()));
        Path interrupted = Files.createTempFile(directory,
                store.path(a.discKey()).getFileName() + "-", ".staging");
        Files.write(interrupted, Arrays.copyOf(prior, prior.length - 1));
        assertThrows(IOException.class, () -> store.publish(interrupted, a.discKey()));
        assertArrayEquals(prior, Files.readAllBytes(store.path(a.discKey())));
        assertTrue(Files.exists(interrupted));
        Path foreign = directory.resolve("not-clank.staging");
        Files.write(foreign, prior);
        assertThrows(IOException.class, () -> store.publish(foreign, a.discKey()));
        assertTrue(Files.exists(foreign));
    }
    @Test void completeButUnpublishedStagingIsNeverACache() throws Exception {
        var a = ClankFixtures.bundle();
        var store = new Rac1ClankSidecarStore(directory);
        Path staging = Files.createTempFile(directory,
                store.path(a.discKey()).getFileName() + "-", ".staging");
        Files.write(staging, Rac1ClankSidecarCodec.encode(a));
        assertThrows(IOException.class, () -> store.read(a.discKey()));
        assertTrue(Files.exists(staging));
        store.publish(staging, a.discKey());
        assertEquals(a.discKey(), store.read(a.discKey()).discKey());
    }
}
