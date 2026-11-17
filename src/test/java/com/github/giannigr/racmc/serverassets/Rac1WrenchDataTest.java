package com.github.giannigr.racmc.serverassets;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.asset.*;
import org.junit.jupiter.api.Test;

class Rac1WrenchDataTest {
    private static Rac1Animation clip(String name, int count, boolean metadata) {
        float[] times = new float[count];
        float[] rotations = new float[count * 4];
        int[] headers = new int[count];
        int[] rates = new int[count];
        int[] mapping = new int[count];
        for (int i = 0; i < count; i++) {
            times[i] = i / 60f;
            rotations[i * 4 + 3] = 1;
            headers[i] = i * 16;
            rates[i] = Float.floatToRawIntBits(1);
            mapping[i] = i;
        }
        return new Rac1Animation(name, times, false, rotations, new int[0], new int[0],
                new float[0], new int[0], new int[0], new float[0], new boolean[0],
                metadata ? new Rac1SourceTiming(0, headers, rates, times, mapping)
                        : Rac1SourceTiming.unavailable());
    }

    private static Rac1AnimatedAsset asset(int id, boolean metadata) {
        float[] identity = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
        var skeleton = new Rac1Skeleton(identity, new int[] { -1 }, new float[3]);
        var mesh = new Rac1SkinnedMesh(new float[0], new float[0], new float[0],
                new int[0], new int[0], new int[0], new float[0]);
        var clips = id == 0 ? new Rac1Animation[] {
                clip("ratchet_sequence_26", 27, metadata), clip("ratchet_sequence_43", 5, metadata) }
                : new Rac1Animation[] { clip("sequence_1", 1, metadata),
                        clip("sequence_6", 1, metadata), clip("sequence_10", 5, metadata) };
        return new Rac1AnimatedAsset(id, "synthetic", mesh, skeleton, clips, new Rac1Texture[0],
                new int[][] { { 0 }, { 0 } });
    }

    @Test
    void requiredOriginalChannelsAreAvailableWithoutActionStateOrMinecraftTypes() {
        var data = new Rac1WrenchData("synthetic", asset(0, true), asset(71, true));
        assertTrue(data.heroSequence(43).isPresent());
        assertTrue(data.heroSequence(26).isPresent());
        assertTrue(data.wrenchSequence(10).isPresent());
        assertTrue(data.wrenchSequence(99).isEmpty());
    }

    @Test
    void missingOriginalClockOrAttachmentDataDoesNotBecomeReadyByGuessing() {
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1WrenchData("synthetic", asset(0, false), asset(71, true)));
        var source = asset(71, true);
        var noLists = new Rac1AnimatedAsset(71, source.name(), source.mesh(), source.skeleton(),
                source.animations(), source.textures());
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1WrenchData("synthetic", asset(0, true), noLists));
    }

    @Test
    void nonfiniteRequiredPoseCannotBePublishedForAuthoritativeSampling() {
        var hero = asset(0, true);
        hero.animations()[0].rotations()[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1WrenchData("synthetic", hero, asset(71, true)));
    }
}
