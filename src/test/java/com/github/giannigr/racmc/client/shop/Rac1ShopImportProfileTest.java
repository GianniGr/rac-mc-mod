package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.iso.*;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopImportProfileTest {
    @Test void ordinaryRuntimeDoesNotCollectTimingsOrWrapTheValidatedImage() throws Exception {
        var profile=Rac1ShopImportProfile.disabled();
        Object expected=new Object();
        assertSame(expected,profile.measure("validation",() -> expected));
        assertTrue(profile.nanos().isEmpty()); assertTrue(profile.cpuNanos().isEmpty());
        assertNull(profile.image(null),"Disabled adapter returns the original argument untouched");
        assertEquals(0,profile.readCalls()); assertEquals(0,profile.readBytes());
    }
    @Test void stagesKeepResultsAndFailuresWithoutSuppressingExceptions() throws Exception {
        var profile=new Rac1ShopImportProfile();
        Object result=new Object();
        assertSame(result,profile.measure("convert",() -> result));
        var failure=new IOException("fixture");
        assertSame(failure,assertThrows(IOException.class,() -> profile.measure("convert",() -> { throw failure; })));
        assertTrue(profile.nanos().get("convert")>=0);
        assertThrows(UnsupportedOperationException.class,() -> profile.nanos().put("fake",1L));
    }
    @Test void measuringAdapterPreservesReadBoundsAndDoesNotCloseItsBorrowedOwner() throws Exception {
        var profile=new Rac1ShopImportProfile(); boolean[] closed={false};
        Rac1DiscImage original=new Rac1DiscImage() {
            public Rac1DiscRevision revision() { return Rac1DiscRevision.PAL_V2_00; }
            public Path sourcePath() { return Path.of("fixture.iso"); }
            public long imageSizeBytes() { return 8192; }
            public List<String> listFilePaths() { return List.of("fixture"); }
            public byte[] readFile(String name,long max) throws IOException { throw new IOException("fixture"); }
            public InputStream openFile(String name) { return new ByteArrayInputStream(new byte[]{3}); }
            public byte[] readBytes(long offset,int length) {
                assertEquals(5,offset); assertEquals(2,length); return new byte[]{1,2};
            }
            public byte[] readSectors(long sector,int count) {
                assertEquals(1,sector); assertEquals(2,count); return new byte[]{3,4,5,6};
            }
            public void close() { closed[0]=true; }
        };
        var measured=profile.image(original);
        assertArrayEquals(new byte[]{1,2},measured.readBytes(5,2));
        assertArrayEquals(new byte[]{3,4,5,6},measured.readSectors(1,2));
        assertEquals(2,profile.readCalls()); assertEquals(6,profile.readBytes());
        assertEquals(original.revision(),measured.revision());
        assertEquals(original.sourcePath(),measured.sourcePath());
        assertEquals(original.imageSizeBytes(),measured.imageSizeBytes());
        assertEquals(original.listFilePaths(),measured.listFilePaths());
        assertEquals(3,measured.openFile("fixture").read());
        assertThrows(IOException.class,() -> measured.readFile("fixture",1));
        assertEquals(2,profile.readCalls(),"Only completed asset reads counted");
        measured.close(); assertFalse(closed[0],"Only original validated outcome owns the handle");
        original.close(); assertTrue(closed[0]);
    }
}
