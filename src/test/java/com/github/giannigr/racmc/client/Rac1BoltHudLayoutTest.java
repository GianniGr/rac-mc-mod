package com.github.giannigr.racmc.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltHudLayoutTest {
    @Test void thirtyIndicesAndOriginalLanguageGrouping() {
        for (int hz : new int[]{50, 60})
            for (long time = -1_000_000_000L; time <= 3_000_000_000L; time += 100_000)
                assertTrue(Rac1BoltHudLayout.frame(time, hz) >= 0 && Rac1BoltHudLayout.frame(time, hz) < 30);
        assertTrue(Rac1BoltHudLayout.dot("de_de"));
        assertTrue(Rac1BoltHudLayout.dot("it_it"));
        for (String code : new String[]{"en_us", "fr_fr", "es_es", "ja_jp"})
            assertFalse(Rac1BoltHudLayout.dot(code));
    }
}
