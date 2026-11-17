package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.cache.Rac1HudCacheCodec;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, validated read-only disc test. Only digests and numeric facts in source, never art. */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class RealDiscHudTest {
    @Test void originalPanelsThirtyFramesRegularFontAndLayoutDecodeAndCache() throws Exception {
        try (var selected = new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(selected.isSupported(), selected.result().message());
            var level = Rac1LevelAssets.load(selected.image(), 1);
            var hud = Rac1HudImporter.read(selected.image(), level);
            assertTrue(Rac1BoltHudDefinition.complete(hud));
            assertEquals(30, hud.sprites().get("bolt").size());
            assertEquals(2, hud.sprites().get("bolt_panel").size());
            for (var frames : hud.sprites().values()) for (var frame : frames) {
                assertEquals(32, frame.width()); assertEquals(32, frame.height());
            }
            assertEquals("628a86baeaca8947b26d68beb9274a7abdd90d9f8fa69af26c3b1082958aefd8",
                    hash(hud.sprites().get("bolt").getFirst().pixels()));
            assertEquals("860320813144cd4856648370038832706d47186a43be177d2236ea9c6c60cafe",
                    hash(hud.sprites().get("bolt_panel").getFirst().pixels()));
            var font = hud.fonts().get("regular");
            assertEquals("d985b42c71498ef53db07fc27fab9018c04f5b748bd0ae2db70acd37592ddf8e",
                    hash(font.metrics()));
            assertEquals("4712749b61c81c995109285151ec88cc888a4775fe5f64a878d3b2c001b8fa72",
                    hash(font.atlas().pixels()));
            for (char digit = '0'; digit <= '9'; digit++) {
                assertEquals((digit - '0') * 16, font.u(digit));
                assertEquals(64, font.v(digit)); assertEquals(13, font.advance(digit));
            }
            assertEquals(-8, font.top('\''));
            assertEquals(4, font.advance('\'')); assertEquals(6, font.advance('.'));
            assertEquals(-32, hud.properties().get("textX")); assertEquals(8, hud.properties().get("textY"));
            assertEquals(-30, hud.properties().get("separatorX")); assertEquals(21, hud.properties().get("separatorY"));
            assertEquals(-29, hud.properties().get("dotX")); assertEquals(10, hud.properties().get("dotY"));
            assertEquals(13, hud.properties().get("digitWidth"));
            assertEquals(8, hud.properties().get("openSteps"));
            assertEquals(8, hud.properties().get("textSteps"));
            assertEquals(0x80E08060, hud.properties().get("textOpaque"));
            byte[] cached = Rac1HudCacheCodec.write(hud);
            var restored = Rac1HudCacheCodec.read(cached);
            assertTrue(Rac1BoltHudDefinition.complete(restored));
            assertArrayEquals(cached, Rac1HudCacheCodec.write(restored));
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
