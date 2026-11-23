package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.sound.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Rac1ClankAudioLoadTest {
    @TempDir Path directory;
    @Test void glideImportUsesVerifiedCapabilityWithoutAnotherHashAndClosesOnlyItsLease() throws Exception {
        var disc=SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00,"SCES_509.16",SyntheticIso.elf(4096));
        var source=Files.write(directory.resolve("synthetic-disc"),disc.image());
        try(var media=new Rac1MediaManager(disc.validator(),directory.resolve("media.properties"))) {
            media.select(source);await(media);
            long validations=media.completedAttempts();
            String key=disc.profile().revision().label()+"|"+disc.productCode()+"|"+disc.profile().imageSha1();
            var request=new Rac1ClankAudioLoad.Request(source,key,media.generation());
            var catalog=new Rac1ClankAudioCatalog(Map.of(),Map.of());
            var result=Rac1ClankAudioLoad.load(request,r->media.lease(r.image(),r.discKey()),lease->{
                assertTrue(lease.isCurrent());return catalog;
            });
            assertSame(catalog,result);assertEquals(validations,media.completedAttempts());
            try(var stillOwned=media.lease(source,key)){assertTrue(stillOwned.isCurrent());}
            var stale=new Rac1ClankAudioLoad.Request(source,key,media.generation()+1);
            assertThrows(IOException.class,()->Rac1ClankAudioLoad.load(stale,
                    r->media.lease(r.image(),r.discKey()),lease->{fail("Stale job decoded audio");return catalog;}));
            assertThrows(IOException.class,()->Rac1ClankAudioLoad.load(request,
                    r->media.lease(r.image(),r.discKey()),lease->{media.invalidateLeases();return catalog;}));
            assertEquals(validations,media.completedAttempts());
        }
    }
    @Test void requestIdentityIncludesSamePathRevalidationAndDiscNotOnlyItsFilename() {
        var source=directory.resolve("source");var first=new Rac1ClankAudioLoad.Request(source,"disc-a",1);
        assertNotEquals(first,new Rac1ClankAudioLoad.Request(source,"disc-a",2));
        assertNotEquals(first,new Rac1ClankAudioLoad.Request(source,"disc-b",1));
        assertEquals(first,new Rac1ClankAudioLoad.Request(source.resolveSibling(".").resolve("source"),"disc-a",1));
        assertThrows(IllegalArgumentException.class,()->new Rac1ClankAudioLoad.Request(source,"disc-a",-1));
    }
    private static void await(Rac1MediaManager media) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while((media.isChecking()||media.completedAttempts()<1)&&System.nanoTime()<deadline)Thread.sleep(5);
        assertFalse(media.isChecking());assertEquals(1,media.completedAttempts());
        assertTrue(media.isReady(),()->media.attemptResult().toString());
    }
}
