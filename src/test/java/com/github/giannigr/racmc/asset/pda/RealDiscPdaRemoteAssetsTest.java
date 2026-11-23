package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Original remote program/cue bindings, never game playback or a rendered UI fidelity claim. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscPdaRemoteAssetsTest {
    @TempDir Path temp;
    @Test void exactRemoteProgramAndEightClassBindingsRejectStaleSource() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try (var media=new Rac1MediaManager(validator,temp.resolve("remote-assets-media.properties"))) {
            media.start(); media.select(path);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while ((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            try (var lease=media.lease(path,Rac1DiscIdentity.key(result.revision(),profile))) {
                var program=Rac1PdaRemoteProgram.read(lease);
                var audio=Rac1PdaRemoteAudio.read(lease,program);
                assertEquals(8,audio.clips().size());
                System.out.println("PDA REMOTE VERIFIED "+lease.revision()+" six primary program ranges");
                for (var role:Rac1PdaRemoteAudio.Role.values()) {
                    var clip=audio.clips().get(role);
                    assertNotNull(clip); assertEquals(68+role.slot(),clip.cueId());
                    assertFalse(clip.repeat()); assertTrue(clip.pcm().length>0);
                    System.out.println("PDA REMOTE CUE "+role+" local="+role.slot()+" cue="+clip.cueId()
                            +" rate="+clip.rate()+" gain="+clip.gain()+" adpcm="+clip.adpcmHash());
                }
                int vector=program.pal() ? 0x1610e0:0x161060;
                byte[] camera=Rac1HudImporter.overlayRange(program.overlay(),vector,16);
                System.out.printf("PDA REMOTE CAMERA source=0x%x xyz=%f,%f,%f%n",vector,
                        Float.intBitsToFloat(word(camera,0)),Float.intBitsToFloat(word(camera,4)),Float.intBitsToFloat(word(camera,8)));
                media.invalidateLeases();
                assertThrows(Exception.class,() -> Rac1PdaRemoteProgram.read(lease));
                assertThrows(Exception.class,() -> Rac1PdaRemoteAudio.read(lease,program));
                assertEquals(1,media.completedAttempts());
            }
        }
    }
}
