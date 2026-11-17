package com.github.giannigr.racmc.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1SourceTiming;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class Rac1WrenchCacheTest {
    @Test
    void sourceTimingBitsAndAttachmentListsRoundTripWithoutInventingMetadata() throws Exception {
        var base = SyntheticAssets.content("timing");
        var original = base.wrench();
        var clip = original.animations()[0];
        var timing = new Rac1SourceTiming(0, new int[] { 112, 528 },
                new int[] { Float.floatToRawIntBits(0.5f), 0x7fa12345 },
                new float[] { 0, 0.5f }, new int[] { 0, 1 });
        var timed = new Rac1Animation(clip.name(), clip.times(), clip.loop(), clip.rotations(),
                clip.translationKeys(), clip.translationJoints(), clip.translationValues(),
                clip.scaleKeys(), clip.scaleJoints(), clip.scaleValues(), clip.scaleInherited(),
                timing);
        var wrench = new Rac1AnimatedAsset(71, original.name(), original.mesh(),
                original.skeleton(), new Rac1Animation[] { timed }, original.textures(),
                new int[][] { { 0 }, { 0, 1, 2 } });
        var input = new Rac1CacheContent(base.discKey(), base.staticAsset(), base.animatedAsset(),
                base.blaster(), base.handJoint(), base.crates(), wrench);
        var output = Rac1CacheCodec.read(Rac1CacheCodec.write(input,
                Rac1CacheStore.SCHEMA_VERSION), input.discKey(), Rac1CacheStore.SCHEMA_VERSION);
        assertEquals(timing, output.wrench().animations()[0].sourceTiming());
        assertArrayEquals(new int[] { 0, 1, 2 }, output.wrench().attachmentChain(1));
        assertFalse(output.blaster().animations()[0].sourceTiming().available());
    }

    @Test
    void standaloneWrenchRoundTripsWithoutChangingOtherAssets() throws Exception {
        var input = SyntheticAssets.content("test");
        var output = Rac1CacheCodec.read(Rac1CacheCodec.write(input,
                Rac1CacheStore.SCHEMA_VERSION), "test", Rac1CacheStore.SCHEMA_VERSION);
        assertEquals(71, output.wrench().classNumber());
        assertArrayEquals(input.wrench().mesh().positions(), output.wrench().mesh().positions());
        assertArrayEquals(input.wrench().animations()[0].rotations(),
                output.wrench().animations()[0].rotations());
        assertArrayEquals(input.wrench().textures()[0].pixels(),
                output.wrench().textures()[0].pixels());
        assertEquals(input.crates().keySet(), output.crates().keySet());
        assertEquals(input.blaster().classNumber(), output.blaster().classNumber());
    }

    @Test
    void wrongClassInWrenchSectionFailsClosed() throws Exception {
        var input = SyntheticAssets.content("test");
        var wrong = new Rac1CacheContent("test", input.staticAsset(), input.animatedAsset(),
                input.blaster(), input.handJoint(), input.crates(), input.blaster());
        byte[] bytes = Rac1CacheCodec.write(wrong, Rac1CacheStore.SCHEMA_VERSION);
        assertThrows(IOException.class, () -> Rac1CacheCodec.read(bytes, "test",
                Rac1CacheStore.SCHEMA_VERSION));
    }

    @Test
    void schema4CannotMasqueradeAsCurrentWrenchCache() throws Exception {
        byte[] bytes = Rac1CacheCodec.write(SyntheticAssets.content("test"), 4);
        assertThrows(IOException.class, () -> Rac1CacheCodec.read(bytes, "test",
                Rac1CacheStore.SCHEMA_VERSION));
    }

    @Test
    void incompleteSyntheticContentRemainsExplicitlyUnavailable() throws Exception {
        var input = SyntheticAssets.content("test");
        var incomplete = new Rac1CacheContent("test", input.staticAsset(),
                input.animatedAsset(), input.blaster(), input.handJoint(), input.crates());
        var output = Rac1CacheCodec.read(Rac1CacheCodec.write(incomplete,
                Rac1CacheStore.SCHEMA_VERSION), "test", Rac1CacheStore.SCHEMA_VERSION);
        assertNull(output.wrench());
    }

    @Test
    void truncatedFileCannotYieldPartialWrench() throws Exception {
        byte[] bytes = Rac1CacheCodec.write(SyntheticAssets.content("test"),
                Rac1CacheStore.SCHEMA_VERSION);
        byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - 1);
        assertThrows(IOException.class, () -> Rac1CacheCodec.read(truncated, "test",
                Rac1CacheStore.SCHEMA_VERSION));
    }
}
