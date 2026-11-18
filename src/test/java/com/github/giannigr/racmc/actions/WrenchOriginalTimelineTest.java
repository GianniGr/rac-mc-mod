package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.Rac1SourceTiming;
import org.junit.jupiter.api.Test;

class WrenchOriginalTimelineTest {
    @Test void originalHeaderTimeIsNeitherKeyIndexNorSecondsTimesSixty() {
        var timeline = new WrenchOriginalClock.Timeline(new Rac1SourceTiming(0,
                new int[] {480, 512, 544, 576}, new int[4],
                new float[] {0, 0.06666667F, 0.13333334F, 0.2F}, new int[] {0, 1, 2, 3}));
        assertEquals(30, timeline.keyTime(0));
        assertEquals(33, timeline.keyTime(0.1), 1.0e-5);
        assertEquals(0.1, timeline.secondsAt(33), 1.0e-6);
        assertEquals(0.13333334, timeline.indexSeconds(2), 1.0e-6);
        assertNotEquals(6, timeline.keyTime(0.1));
    }

    @Test void rawIndicesSurviveDroppedZeroDurationPoseKeys() {
        var timeline = new WrenchOriginalClock.Timeline(new Rac1SourceTiming(0,
                new int[] {16, 32, 48, 64}, new int[4], new float[] {0, 0, 0.1F, 0.2F},
                new int[] {1, 2, 3, -1}));
        assertEquals(0, timeline.indexSeconds(1));
        assertEquals(2, timeline.keyTime(0), "Pose and event clock select the last zero-duration key");
        assertEquals(0.1, timeline.indexSeconds(2), 1.0e-6);
        assertEquals(3.5, timeline.keyTime(0.15), 1.0e-6);
    }

    @Test void unavailableOrNonMonotonicEventMetadataFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                new WrenchOriginalClock.Timeline(Rac1SourceTiming.unavailable()));
        assertThrows(IllegalArgumentException.class, () -> new WrenchOriginalClock.Timeline(
                new Rac1SourceTiming(0, new int[] {32, 16}, new int[2], new float[] {0, 1},
                        new int[] {0, 1})));
    }
}
