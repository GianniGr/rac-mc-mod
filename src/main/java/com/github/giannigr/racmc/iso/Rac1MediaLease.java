package com.github.giannigr.racmc.iso;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Read-only capability issued only by a fully verified media manager. Acquire/use on an asset
 * executor; never retain a lease as a cache identity or publish results without checkValid().
 */
public final class Rac1MediaLease implements Rac1DiscImage {
    public static final int MAX_READ_BYTES=64*1024*1024;
    private final Retained owner;
    private final long generation;
    private final BooleanSupplier current;
    private volatile boolean closed;

    Rac1MediaLease(Retained owner,long generation,BooleanSupplier current) {
        this.owner=owner; this.generation=generation; this.current=current;
    }
    public long generation() { return generation; }
    public String discKey() { return owner.key; }
    /** Memory-only hint. checkValid() performs filesystem/change checks on the loader thread. */
    public boolean isCurrent() { return !closed && owner.active && current.getAsBoolean(); }
    public void checkValid() throws IOException { read(() -> null); }
    private void requireCurrent() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Media lease job cancelled");
        if (!isCurrent()) throw new IOException("Media lease is closed or its selection generation expired");
    }
    private <T> T read(Read<T> work) throws IOException {
        requireCurrent();
        try { owner.io.lockInterruptibly(); }
        catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt(); throw new InterruptedIOException("Media lease read cancelled");
        }
        try {
            requireCurrent(); owner.checkStamp();
            T result=work.run();
            owner.checkStamp(); requireCurrent();
            return result;
        } finally { owner.closeRevoked(); owner.io.unlock(); }
    }
    @FunctionalInterface private interface Read<T> { T run() throws IOException; }
    public Rac1DiscRevision revision() { return owner.image.revision(); }
    public Path sourcePath() { return owner.image.sourcePath(); }
    public long imageSizeBytes() { return owner.image.imageSizeBytes(); }
    public List<String> listFilePaths() {
        if (!isCurrent()) throw new IllegalStateException("Expired media lease");
        return owner.image.listFilePaths();
    }
    public byte[] readBytes(long offset,int length) throws IOException {
        if (length<0 || length>MAX_READ_BYTES || offset<0 || offset>imageSizeBytes()-length)
            throw new IllegalArgumentException("Media lease read exceeds bounded image range");
        return read(() -> owner.image.readBytes(offset,length));
    }
    public byte[] readSectors(long sector,int count) throws IOException {
        if (sector<0 || sector>Long.MAX_VALUE/2048 || count<0 || count>MAX_READ_BYTES/2048)
            throw new IllegalArgumentException("Media lease sector range overflow");
        return readBytes(sector*2048,count*2048);
    }
    public byte[] readFile(String path,long maxBytes) throws IOException {
        if (maxBytes<=0 || maxBytes>MAX_READ_BYTES) throw new IllegalArgumentException("Media lease file limit");
        return read(() -> owner.image.readFile(path,maxBytes));
    }
    /** Bounded snapshot, not a stream sharing the image's mutable seek cursor. */
    public InputStream openFile(String path) throws IOException {
        return snapshot(readFile(path,MAX_READ_BYTES));
    }
    private InputStream snapshot(byte[] bytes) {
        return new InputStream() {
            private final ByteArrayInputStream data=new ByteArrayInputStream(bytes);
            private boolean streamClosed;
            private void valid() throws IOException {
                requireCurrent();
                if (streamClosed) throw new IOException("Media lease stream closed");
            }
            public int read() throws IOException { valid(); return data.read(); }
            public int read(byte[] b,int off,int len) throws IOException { valid(); return data.read(b,off,len); }
            public long skip(long n) throws IOException { valid(); return data.skip(n); }
            public int available() throws IOException { valid(); return data.available(); }
            public void close() { streamClosed=true; }
        };
    }
    public void close() { closed=true; } // The manager alone owns the verified image handle.

    /** Filesystem checks detect ordinary mutation/replacement, never substitute for the full hash. */
    record Stamp(Path real,Object fileKey,long size,FileTime modified,FileTime created) {
        static Stamp read(Path path) throws IOException {
            Path real=path.toRealPath();
            var a=Files.readAttributes(real,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile()) throw new IOException("Selected media is not a regular file");
            Object key=a.fileKey();
            if (key==null && com.sun.jna.Platform.isWindows()) key=WindowsIdentity.read(real);
            if (key==null) throw new IOException("Filesystem does not expose stable media file identity");
            return new Stamp(real,key,a.size(),a.lastModifiedTime(),a.creationTime());
        }
    }
    /** Minecraft already supplies JNA. Windows' JDK BasicFileAttributes reports a null fileKey. */
    private static final class WindowsIdentity {
        static String read(Path path) throws IOException {
            var api=com.sun.jna.platform.win32.Kernel32.INSTANCE;
            var handle=api.CreateFile(path.toString(),0x80, // FILE_READ_ATTRIBUTES, never write access
                    1|2|4,null,3,0,null); // share read/write/delete, OPEN_EXISTING
            if (com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE.equals(handle))
                throw new IOException("Cannot inspect media file identity: Windows error "+api.GetLastError());
            try {
                var info=new com.sun.jna.platform.win32.WinBase.FILE_ID_INFO();
                if (!api.GetFileInformationByHandleEx(handle,com.sun.jna.platform.win32.WinBase.FileIdInfo,
                        info.getPointer(),new com.sun.jna.platform.win32.WinDef.DWORD(info.size())))
                    throw new IOException("Cannot inspect stable media file ID: Windows error "+api.GetLastError());
                info.read();
                byte[] id=new byte[info.FileId.Identifier.length];
                for (int i=0;i<id.length;i++) id[i]=info.FileId.Identifier[i].byteValue();
                return Long.toUnsignedString(info.VolumeSerialNumber,16)+":"+HexFormat.of().formatHex(id);
            } finally { api.CloseHandle(handle); }
        }
    }
    static final class Retained {
        final Rac1DiscImage image;
        final String key;
        final Stamp stamp;
        final Runnable invalidated;
        final ReentrantLock io=new ReentrantLock();
        volatile boolean active=true;
        private boolean handleClosed;
        Retained(Rac1DiscImage image,String key,Stamp stamp,Runnable invalidated) {
            this.image=image; this.key=key; this.stamp=stamp; this.invalidated=invalidated;
        }
        void checkStamp() throws IOException {
            try {
                if (!stamp.equals(Stamp.read(image.sourcePath()))) throw new IOException("Selected media changed; verify it again");
            } catch (IOException changed) {
                active=false; invalidated.run(); throw changed;
            }
        }
        void revoke() {
            active=false;
            if (io.tryLock()) try { closeRevoked(); } finally { io.unlock(); }
        }
        void closeRevoked() {
            if (!active && !handleClosed) {
                handleClosed=true;
                try { image.close(); } catch (IOException ignored) { /* Already revoked, cannot be leased again. */ }
            }
        }
    }
}
