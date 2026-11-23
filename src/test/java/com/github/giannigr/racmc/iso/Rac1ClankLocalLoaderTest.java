package com.github.giannigr.racmc.iso;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.clank.*;
import com.github.giannigr.racmc.client.clank.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic unit capability is constructed in its owning package; production must use media.lease(). */
class Rac1ClankLocalLoaderTest {
    @TempDir Path directory;
    private final AtomicBoolean current=new AtomicBoolean(true);
    private final AtomicInteger reads=new AtomicInteger(),closes=new AtomicInteger();
    private Path source;
    private Rac1ClankLoadSlot.Request request() throws Exception {
        source=Files.write(directory.resolve("synthetic-source"),new byte[512]);
        return new Rac1ClankLoadSlot.Request(source,directory.resolve("cache"),ClankFixtures.disc(0),7,4);
    }
    private Rac1MediaLease lease(String key,long generation) throws IOException {
        Rac1DiscImage image=new Rac1DiscImage() {
            public Path sourcePath(){return source;}
            public Rac1DiscRevision revision(){return Rac1DiscRevision.PAL_V2_00;}
            public long imageSizeBytes(){return 64L*1024*1024;} // Logical fake extent; no payload read exists.
            public List<String> listFilePaths(){return List.of();}
            public byte[] readBytes(long offset,int length)throws IOException{reads.incrementAndGet();throw new IOException("No synthetic retail data");}
            public byte[] readSectors(long offset,int count)throws IOException{return readBytes(offset*2048,count*2048);}
            public byte[] readFile(String path,long max)throws IOException{return readBytes(0,1);}
            public InputStream openFile(String path)throws IOException{throw new IOException("No files");}
            public void close(){closes.incrementAndGet();}
        };
        var retained=new Rac1MediaLease.Retained(image,key,Rac1MediaLease.Stamp.read(source),()->current.set(false));
        return new Rac1MediaLease(retained,generation,current::get);
    }
    @Test void validSidecarUsesTypedLeaseWithoutAnyDiscByteReadOrRevalidation() throws Exception {
        var request=request();new Rac1ClankSidecarStore(request.cache()).write(ClankFixtures.bundle());
        var stages=new EnumMap<Rac1ClankLocalLoader.Stage,Long>(Rac1ClankLocalLoader.Stage.class);
        var result=Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),7),stages::put);
        assertTrue(result.matches(request.discKey(),4));assertEquals(0,reads.get());assertEquals(0,closes.get());
        assertEquals(Set.of(Rac1ClankLocalLoader.Stage.SIDECAR,Rac1ClankLocalLoader.Stage.PREPARE),stages.keySet());
    }
    @Test void staleGenerationOrWrongIdentityCannotUseEvenValidSidecar() throws Exception {
        var request=request();new Rac1ClankSidecarStore(request.cache()).write(ClankFixtures.bundle());
        assertThrows(IOException.class,()->Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),8),(s,n)->{}));
        assertThrows(IOException.class,()->Rac1ClankLocalLoader.load(request,r->lease(ClankFixtures.disc(1),7),(s,n)->{}));
        current.set(false);
        assertThrows(IOException.class,()->Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),7),(s,n)->{}));
        assertEquals(0,reads.get());assertEquals(0,closes.get());
    }
    @Test void invalidationDuringCacheDecodeDiscardsResultBeforePreparation() throws Exception {
        var request=request();new Rac1ClankSidecarStore(request.cache()).write(ClankFixtures.bundle());
        var stages=new HashSet<Rac1ClankLocalLoader.Stage>();
        assertThrows(IOException.class,()->Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),7),(stage,n)->{
            stages.add(stage);if(stage==Rac1ClankLocalLoader.Stage.SIDECAR)current.set(false);
        }));
        assertEquals(Set.of(Rac1ClankLocalLoader.Stage.SIDECAR),stages);
    }
    @Test void invalidationDuringPreparationCannotReturnLateReadyModels() throws Exception {
        var request=request();new Rac1ClankSidecarStore(request.cache()).write(ClankFixtures.bundle());
        assertThrows(IOException.class,()->Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),7),(stage,n)->{
            if(stage==Rac1ClankLocalLoader.Stage.PREPARE)current.set(false);
        }));
        assertEquals(0,reads.get());
    }
    @Test void missAndCorruptionAttemptOnlyExistingImporterUnderTheLeaseAndFailClosed() throws Exception {
        var request=request();var store=new Rac1ClankSidecarStore(request.cache());
        for(boolean corrupt:new boolean[]{false,true}) {
            if(corrupt){store.write(ClankFixtures.bundle());var file=store.path(request.discKey());
                var bytes=Files.readAllBytes(file);bytes[0]^=1;Files.write(file,bytes);}
            int before=reads.get();
            assertThrows(Exception.class,()->Rac1ClankLocalLoader.load(request,r->lease(r.discKey(),7),(s,n)->{}));
            assertTrue(reads.get()>before,"Miss must use leased source, not inspect a path or guess assets");
        }
        assertEquals(0,closes.get(),"Closing a consumer lease cannot close the retained source");
    }
}
