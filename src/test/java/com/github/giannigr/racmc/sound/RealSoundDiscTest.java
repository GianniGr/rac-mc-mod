package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Read-only opt-in test. No PCM files, device access, playback or proprietary bytes in test output. */
class RealSoundDiscTest {
    @Test void referenceSamplesAndRegionalPerLevelRemaps() throws Exception {
        String path = System.getProperty("racmc.isoImage", "");
        assumeTrue(!path.isBlank());
        try (var result = new Rac1DiscValidator().inspect(Path.of(path))) {
            assertTrue(result.isSupported());
            var image = result.image();
            var catalog = Rac1AudioCatalog.extract(image);
            var events = new Rac1AudioEvent[]{Rac1AudioEvent.CRATE_BREAK, Rac1AudioEvent.TNT_BLAST,
                    Rac1AudioEvent.TNT_BEEP, Rac1AudioEvent.BOLT_ATTRACTION};
            int[] lengths = {10892, 23828, 11200, 8960};
            String[] hashes = {
                    "172bc7275c39fcb52b0a97a702d5abbfc4849dbb8b15eed5d1a5e9f6357026ed",
                    "6df1b60b7a581ec9b56ae2cc34f8076d3b10d4343781ec5c0d252e40b2fdd6f7",
                    "8a2aad8f377e2f6ea781fa270456b620b65bb04615c4918874bda98e11a51da0",
                    "589820d5bbe6a8f8c01790e97be729d8261a670e8c3e15ccf57611717895cdab"};
            for (int n = 0; n < events.length; n++) {
                var clip = catalog.clips().get(events[n]);
                assertEquals(lengths[n] * 2, clip.pcm().length);
                assertEquals(hashes[n], clip.adpcmHash());
                System.out.println(result.result().revision() + " " + events[n].id()
                        + " samples=" + lengths[n] + " PCM-SHA256=" + SoundBytes.hash(clip.pcm()));
            }
            var tables = Rac1DiscTables.read(image);
            for (int level : new int[]{0, 1, 2}) {
                var container = Rac1LevelData.read(image, Rac1LevelHeader.read(image, tables.levelSector(level)));
                var bank = new Rac1SoundBank(container.soundBank(image));
                byte[] index = image.readBytes(container.base() + container.coreIndexOffset(), container.coreIndexSize());
                var assets = Rac1LevelAssets.load(image, level);
                var crate = assets.coreIndex().mobyClass(500);
                int cue = Rac1SoundMap.resolve(index, assets.classBlob(crate.offset()), 500, 0).cue();
                assertEquals(new int[]{179, 173, 163}[level], cue);
                assertEquals(hashes[0], bank.tone(cue).adpcmHash());
                if (level > 0) {
                    var tnt = assets.coreIndex().mobyClass(505);
                    cue = Rac1SoundMap.resolve(index, assets.classBlob(tnt.offset()), 505, 1).cue();
                    assertEquals(level == 1 ? 179 : 167, cue);
                    assertEquals(hashes[2], bank.tone(cue).adpcmHash());
                }
            }
        }
    }
}
