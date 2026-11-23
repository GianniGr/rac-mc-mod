package com.github.giannigr.racmc.client.shop;

import java.util.LinkedHashMap;
import java.util.Map;
import com.github.giannigr.racmc.iso.*;
import java.io.*;
import java.nio.file.Path;
import java.util.List;

/** Optional shop-local wall-stage probe, never logs paths, bytes, PCM or private UI state. */
public final class Rac1ShopImportProfile {
    @FunctionalInterface public interface Work<T> { T run() throws Exception; }
    private static final Rac1ShopImportProfile DISABLED=new Rac1ShopImportProfile(false);
    private final boolean enabled;
    private final Map<String,Long> nanos=new LinkedHashMap<>();
    private final Map<String,Long> cpuNanos=new LinkedHashMap<>();
    private long readBytes,readCalls;
    private static final class Cpu {
        static final java.lang.management.ThreadMXBean BEAN=java.lang.management.ManagementFactory.getThreadMXBean();
    }
    public Rac1ShopImportProfile() { this(true); }
    private Rac1ShopImportProfile(boolean enabled) { this.enabled=enabled; }
    public static Rac1ShopImportProfile disabled() { return DISABLED; }
    public <T> T measure(String stage,Work<T> work) throws Exception {
        if (!enabled) return work.run();
        long start=System.nanoTime();
        var bean=Cpu.BEAN;
        long cpu=bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled() ? bean.getCurrentThreadCpuTime():-1;
        try { return work.run(); }
        finally {
            nanos.merge(stage,System.nanoTime()-start,Long::sum);
            if (cpu>=0) cpuNanos.merge(stage,bean.getCurrentThreadCpuTime()-cpu,Long::sum);
        }
    }
    public Map<String,Long> nanos() { return Map.copyOf(nanos); }
    public Map<String,Long> cpuNanos() { return Map.copyOf(cpuNanos); }
    public long readBytes() { return readBytes; }
    public long readCalls() { return readCalls; }
    @FunctionalInterface private interface Read { byte[] run() throws IOException; }
    private byte[] read(Read work) throws IOException {
        long start=System.nanoTime();
        try {
            var bytes=work.run(); readBytes+=bytes.length; readCalls++; return bytes;
        } finally { nanos.merge("asset-disc-read-nested",System.nanoTime()-start,Long::sum); }
    }
    /** Only measures reads already requested by the importer. Does not open or retain another image. */
    Rac1DiscImage image(Rac1DiscImage original) {
        if (!enabled) return original;
        return new Rac1DiscImage() {
            public Rac1DiscRevision revision() { return original.revision(); }
            public Path sourcePath() { return original.sourcePath(); }
            public long imageSizeBytes() { return original.imageSizeBytes(); }
            public List<String> listFilePaths() { return original.listFilePaths(); }
            public byte[] readFile(String name,long max) throws IOException { return read(() -> original.readFile(name,max)); }
            public InputStream openFile(String name) throws IOException { return original.openFile(name); }
            public byte[] readBytes(long offset,int length) throws IOException { return read(() -> original.readBytes(offset,length)); }
            public byte[] readSectors(long sector,int count) throws IOException { return read(() -> original.readSectors(sector,count)); }
            public void close() { /* The validated outcome, not this measuring adapter, owns the handle. */ }
        };
    }
}
