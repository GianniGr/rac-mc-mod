package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact approved level-1 source only; memory decode, no PCM dump or playback. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopDeniedCueTest {
    @TempDir Path temp;
    @Test void exactVendorLocalTwoResolvesVerifiedNonloopingCueSeventy() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try(var media=new Rac1MediaManager(validator,temp.resolve("media.properties"))) {
            media.select(path); RealDiscShopLeaseTest.await(media);
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            String key=Rac1DiscIdentity.key(result.revision(),profile);
            try(var lease=media.lease(path,key)) {
                var cue=Rac1ShopDeniedCue.read(lease,Rac1LevelAssets.load(lease,1));
                assertEquals(70,cue.cueId()); assertFalse(cue.repeat()); assertEquals(-1,cue.loopStart());
                assertEquals(514*56,cue.pcm().length); assertEquals(22050,cue.rate(),.01);
                assertTrue(cue.gain()>0 && cue.gain()<=1);
                System.out.println("DENIED SOURCE "+lease.discKey()+" cue="+cue.cueId()+" samples="+cue.loopEnd()
                        +" rate="+cue.rate()+" gain="+cue.gain()+" repeat="+cue.repeat()+"; no playback/dump");
                media.invalidateLeases();
                assertThrows(java.io.IOException.class,() -> Rac1ShopDeniedCue.read(lease,Rac1LevelAssets.load(lease,1)));
            }
            assertEquals(1,media.completedAttempts());
        }
    }
}
