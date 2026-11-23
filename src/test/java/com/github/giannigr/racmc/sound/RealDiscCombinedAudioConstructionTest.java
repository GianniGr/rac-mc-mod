package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Production shared extraction/cache plus separately optional dry import. No audio device or exports. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscCombinedAudioConstructionTest {
    @TempDir Path temp;
    @Test void allPhysicalEventsIncludingEquipUseTheCorrectOriginalClassStorage() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try (var media=new Rac1MediaManager(validator,temp.resolve("media.properties"))) {
            media.start(); media.select(path);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while ((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            try (var lease=media.lease(path,Rac1DiscIdentity.key(result.revision(),profile))) {
                var shared=Rac1AudioCatalog.extract(lease);
                assertEquals(14,shared.clips().size());
                for (var event:Rac1AudioEvent.IMPORTED) assertNotNull(shared.clip(event),event.id());
                for (var event:new Rac1AudioEvent[]{Rac1AudioEvent.BLASTER_FIRE_LOOP,
                        Rac1AudioEvent.BLASTER_EQUIP_START,Rac1AudioEvent.BLASTER_EQUIP_FINISH}) {
                    assertEquals(168,event.classId());
                    assertTrue(shared.clip(event).pcm().length>0);
                    System.out.println("SHARED AUDIO "+lease.revision()+" "+event+" slot="+event.slot()
                            +" cue="+shared.clip(event).cueId()+" hash="+shared.clip(event).adpcmHash());
                }
                assertEquals(101,shared.clip(Rac1AudioEvent.BLASTER_FIRE_LOOP).cueId());
                assertTrue(shared.clip(Rac1AudioEvent.BLASTER_FIRE_LOOP).repeat());
                assertSame(shared.clip(Rac1AudioEvent.BLASTER_AMMO_PICKUP),shared.clip(Rac1AudioEvent.BOMB_GLOVE_AMMO_PICKUP));
                var store=new Rac1SoundCache(temp.resolve("sound"));
                store.write(profile.imageSha1(),shared);
                var warm=store.read(profile.imageSha1()).orElseThrow();
                assertEquals(shared.clips().keySet(),warm.clips().keySet());
                for (var event:Rac1AudioEvent.IMPORTED)
                    assertArrayEquals(shared.clip(event).pcm(),warm.clip(event).pcm());
                var optional=warm.withDryBomb(lease);
                assertEquals(89,optional.clip(Rac1AudioEvent.BOMB_GLOVE_DRY_BURST).cueId());
                assertArrayEquals(Rac1SoundCache.encode(profile.imageSha1(),warm),
                        Rac1SoundCache.encode(profile.imageSha1(),optional));
                assertEquals(1,media.completedAttempts());
                System.out.println("SHARED AUDIO PRODUCTION "+lease.revision()+" cold/warm14 entries; optional dry89 isolated; no device playback");
            }
        }
    }
}
