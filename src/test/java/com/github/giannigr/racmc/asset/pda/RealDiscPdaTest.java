package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in bounded PDA class/icon import; no screenshots, asset dumps, playback or live launch. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscPdaTest {
    @TempDir Path temp;
    @Test void verifiedLeaseSelectsActualHandGadget619AndInventoryIcon60032() throws Exception {
        var path=Path.of(System.getProperty("racmc.isoImage")).toAbsolutePath().normalize();
        var validator=new Rac1DiscValidator();
        try (var media=new Rac1MediaManager(validator,temp.resolve("pda-media.properties"))) {
            media.start(); media.select(path);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while ((media.isChecking() || media.completedAttempts()==0) && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(media.isReady(),() -> media.attemptResult().toString());
            var result=media.attemptResult().orElseThrow();
            var profile=validator.catalog().profiles().stream().filter(p -> p.revision()==result.revision()).findFirst().orElseThrow();
            String key=Rac1DiscIdentity.key(result.revision(),profile);
            try (var lease=media.lease(path,key)) {
                var assets=Rac1PdaImporter.read(lease);
                assertEquals(619,assets.handheld().classNumber());
                assertEquals(4,assets.handheld().skeleton().jointCount());
                assertEquals(1071,assets.handheld().mesh().positions().length/3);
                assertEquals(1815,assets.handheld().mesh().indices().length);
                assertEquals(2,assets.icons().size());
                var pose=com.github.giannigr.racmc.client.Rac1ImportedItemPose.sample(assets.handheld(),1,0);
                assertEquals(assets.handheld().mesh().positions().length,pose.positions().length);
                assertTrue(pose.positions().length>0);
                for (float value:pose.positions()) assertTrue(Float.isFinite(value));
                for (float value:pose.normals()) assertTrue(Float.isFinite(value));
                var bounds=com.github.giannigr.racmc.client.Rac1ImportedItemPose.bounds(pose.positions());
                assertNotNull(bounds);
                assertTrue(bounds[3]>bounds[0] && bounds[4]>bounds[1] && bounds[5]>bounds[2]);
                for (int material:assets.handheld().mesh().materials())
                    assertTrue(material>=0 && material<assets.handheld().textures().length);
                System.out.println("PDA ORIGINAL "+lease.revision()+" class619 joints="+assets.handheld().skeleton().jointCount()
                        +" vertices="+assets.handheld().mesh().positions().length/3
                        +" indices="+assets.handheld().mesh().indices().length
                        +" textures="+assets.handheld().textures().length+" clips="+assets.handheld().animations().length);
                for (int i=0;i<assets.icons().size();i++) {
                    var icon=assets.icons().get(i);
                    assertTrue(icon.width()>0 && icon.height()>0);
                    System.out.println("PDA ICON60032 frame="+i+" size="+icon.width()+"x"+icon.height()+" argbSha256="+hash(icon.pixels()));
                }
                System.out.println("PDA mesh source selected by gadget table619; full startup validations="+media.completedAttempts());
                assertEquals(1,media.completedAttempts());
                media.invalidateLeases();
                assertThrows(Exception.class,() -> Rac1PdaImporter.read(lease));
            }
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
