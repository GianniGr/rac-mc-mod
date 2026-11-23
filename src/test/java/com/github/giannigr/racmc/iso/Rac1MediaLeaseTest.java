package com.github.giannigr.racmc.iso;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Rac1MediaLeaseTest {
    @TempDir Path root;
    private SyntheticDisc disc() {
        return SyntheticDisc.create(Rac1DiscRevision.PAL_V2_00,"SCES_509.16",SyntheticIso.elf(4096));
    }
    private String key(SyntheticDisc disc) {
        return disc.profile().revision().label()+"|"+disc.productCode()+"|"+disc.profile().imageSha1();
    }
    private Path write(String name,byte[] bytes) throws IOException { return Files.write(root.resolve(name),bytes); }
    private Rac1MediaManager manager(SyntheticDisc disc) { return new Rac1MediaManager(disc.validator(),root.resolve("media.properties")); }
    private void await(Rac1MediaManager manager) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while (manager.isChecking() && System.nanoTime()<until) Thread.sleep(2);
        assertFalse(manager.isChecking(),"Verification did not finish");
    }
    private void select(Rac1MediaManager manager,Path path) throws Exception {
        long before=manager.completedAttempts();
        manager.select(path); await(manager);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while (manager.completedAttempts()<=before && System.nanoTime()<until) Thread.sleep(2);
        assertTrue(manager.completedAttempts()>before);
        assertTrue(manager.isReady(),() -> manager.attemptResult().toString());
    }
    @Test void cacheMetadataOrPathCannotIssueCapabilityAndLeasesDoNotReverify() throws Exception {
        var d=disc(); var path=write("valid.iso",d.image());
        try(var m=manager(d)) {
            assertThrows(IOException.class,() -> m.lease(path,key(d)));
            m.start(); assertFalse(m.isReady());
            select(m,path); assertEquals(d.image().length,m.bytesRead());
            long validations=m.completedAttempts(),generation=m.generation();
            assertThrows(IOException.class,() -> m.lease(root.resolve("another.iso"),key(d)));
            assertThrows(IOException.class,() -> m.lease(path,key(d)+"forged"));
            for(int i=0;i<10;i++) try(var lease=m.lease(path,key(d))) {
                lease.checkValid(); assertEquals(generation,lease.generation()); assertEquals(key(d),lease.discKey());
                assertArrayEquals(Arrays.copyOfRange(d.image(),0,32),lease.readBytes(0,32));
                lease.checkValid();
            }
            assertEquals(validations,m.completedAttempts(),"Acquire/read must not call validator again");
        }
        // A new process/manager must hash again, regardless of remembered config/cache identity.
        try(var m=manager(d)) {
            m.start(); assertFalse(m.isReady()); await(m);
            assertTrue(m.isReady()); assertEquals(d.image().length,m.bytesRead());
        }
    }
    @Test void concurrentConsumersAndSnapshotStreamsNeverShareSeekCursorOrCloseEachOther() throws Exception {
        var d=disc(); var path=write("shared.iso",d.image());
        try(var m=manager(d)) {
            select(m,path);
            try(var a=m.lease(path,key(d)); var b=m.lease(path,key(d))) {
                var pool=Executors.newFixedThreadPool(2);
                try {
                    var gate=new CountDownLatch(1);
                    var first=pool.submit(() -> { gate.await(); for(int i=0;i<100;i++)
                        assertArrayEquals(Arrays.copyOfRange(d.image(),512,544),a.readBytes(512,32)); return true; });
                    var second=pool.submit(() -> { gate.await(); for(int i=0;i<100;i++)
                        assertArrayEquals(Arrays.copyOfRange(d.image(),32768,32800),b.readBytes(32768,32)); return true; });
                    gate.countDown(); assertTrue(first.get(20,TimeUnit.SECONDS)); assertTrue(second.get(20,TimeUnit.SECONDS));
                } finally { pool.shutdownNow(); }
                try(var x=a.openFile("SYSTEM.CNF"); var y=b.openFile(d.productCode())) {
                    assertEquals(0x7f,y.read()); assertNotEquals(-1,x.read()); assertEquals('E',y.read());
                }
                a.close(); assertThrows(IOException.class,() -> a.readBytes(0,1));
                b.checkValid(); assertEquals(d.image()[0],b.readBytes(0,1)[0]);
            }
        }
    }
    @Test void sourceMutationFailsClosedAndSamePathRevalidationCannotReviveOldLease() throws Exception {
        var d=disc(); var path=write("mutable.iso",d.image());
        try(var m=manager(d)) {
            select(m,path);
            try(var old=m.lease(path,key(d))) {
                var changed=d.image().clone(); changed[changed.length-1]^=1;
                Files.write(path,changed);
                Files.setLastModifiedTime(path,FileTime.fromMillis(System.currentTimeMillis()+2000));
                assertThrows(IOException.class,() -> old.readBytes(0,1));
                assertFalse(m.isReady()); assertThrows(IOException.class,() -> m.lease(path,key(d)));
                m.select(path); await(m); assertFalse(m.isReady(),"Changed full digest must fail");
                Files.write(path,d.image()); select(m,path);
                assertThrows(IOException.class,old::checkValid);
                try(var fresh=m.lease(path,key(d))) { fresh.checkValid(); }
            }
        }
    }
    @Test void samePathReplacementWithSameSizeAndMtimeIsRejectedByFileIdentity() throws Exception {
        var d=disc(); var path=write("replace.iso",d.image());
        try(var m=manager(d)) {
            select(m,path);
            try(var old=m.lease(path,key(d))) {
                var modified=Files.getLastModifiedTime(path);
                var replacement=write("replacement.iso",d.image());
                Files.setLastModifiedTime(replacement,modified);
                try {
                    Files.move(replacement,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    assertThrows(IOException.class,old::checkValid);
                    assertFalse(m.isReady());
                } catch (AccessDeniedException protectedHandle) {
                    // Windows denies replacement of the retained read handle. No changed bytes
                    // can be read; explicitly unset before retrying a same-name replacement.
                    old.checkValid(); assertArrayEquals(d.image(),Files.readAllBytes(path));
                    m.unset(); assertThrows(IOException.class,old::checkValid);
                    Files.move(replacement,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }
                select(m,path);
                try(var fresh=m.lease(path,key(d))) { fresh.checkValid(); }
                assertFalse(old.isCurrent());
            }
        }
    }
    @Test void failedDifferentSelectionKeepsVerifiedOriginalButNotOldGenerationLeases() throws Exception {
        var d=disc(); var path=write("original.iso",d.image()); var broken=write("bad.iso",new byte[65536]);
        try(var m=manager(d)) {
            select(m,path);
            try(var old=m.lease(path,key(d))) {
                m.select(broken); assertFalse(old.isCurrent()); await(m);
                assertTrue(m.isReady()); assertEquals(path,m.configuredPath().orElseThrow());
                try(var fresh=m.lease(path,key(d))) { fresh.checkValid(); assertNotEquals(old.generation(),fresh.generation()); }
            }
        }
    }
    @Test void reloadUnsetReselectionAndCloseInvalidateReadsStreamsAndOldCompletions() throws Exception {
        var d=disc(); var a=write("a.iso",d.image()); var b=write("b.iso",d.image());
        var m=manager(d);
        try {
            select(m,a);
            var old=m.lease(a,key(d)); var stream=old.openFile("SYSTEM.CNF");
            long count=m.completedAttempts(); m.invalidateLeases();
            assertThrows(IOException.class,() -> stream.read()); assertThrows(IOException.class,old::checkValid);
            try(var fresh=m.lease(a,key(d))) { fresh.checkValid(); }
            assertEquals(count,m.completedAttempts(),"Resource invalidation does not rehash unchanged media");
            for(int i=0;i<20;i++) { m.select(i%2==0 ? b:a); m.unset(); }
            assertFalse(m.isReady()); assertTrue(m.configuredPath().isEmpty());
            assertThrows(IOException.class,() -> m.lease(a,key(d)));
            select(m,b); assertEquals(b,m.configuredPath().orElseThrow());
            var last=m.lease(b,key(d)); m.close();
            assertFalse(last.isCurrent()); assertThrows(IOException.class,last::checkValid);
            assertThrows(IOException.class,() -> m.lease(b,key(d)));
            assertThrows(IllegalStateException.class,() -> m.select(a));
            last.close(); stream.close(); old.close();
        } finally { m.close(); }
    }
    @Test void boundsAndPathTraversalFailBeforeAllocatingOrEscapingImage() throws Exception {
        var d=disc(); var path=write("bounds.iso",d.image());
        try(var m=manager(d)) {
            select(m,path);
            try(var lease=m.lease(path,key(d))) {
                assertThrows(IllegalArgumentException.class,() -> lease.readBytes(0,Rac1MediaLease.MAX_READ_BYTES+1));
                assertThrows(IllegalArgumentException.class,() -> lease.readBytes(-1,1));
                assertThrows(IllegalArgumentException.class,() -> lease.readSectors(Long.MAX_VALUE,Integer.MAX_VALUE));
                assertThrows(IllegalArgumentException.class,() -> lease.readFile("SYSTEM.CNF",Long.MAX_VALUE));
                assertThrows(IllegalArgumentException.class,() -> lease.readFile("../outside",1024));
            }
        }
    }
}
