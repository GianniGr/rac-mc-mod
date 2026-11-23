package com.github.giannigr.racmc.client.sound;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WrenchMeleePlaybackTest {
    @Test void fallbackOnlyAfterOriginalResolutionWithoutChannelAdmission() {
        assertTrue(Rac1WrenchAudioClient.handledMeleeAttempt(true, true));
        assertTrue(Rac1WrenchAudioClient.handledMeleeAttempt(false, false),
                "vetoed/replaced before resolution: do not start a second sound");
        assertFalse(Rac1WrenchAudioClient.handledMeleeAttempt(false, true),
                "no streaming channel: permit the one vanilla fallback");
    }
}
