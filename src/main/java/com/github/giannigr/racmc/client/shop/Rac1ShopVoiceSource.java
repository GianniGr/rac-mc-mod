package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.sound.PsAdpcm;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** In-memory dialogue data only. Not a playback/trigger/subtitle binding or a sidecar format. */
public final class Rac1ShopVoiceSource {
    private static final int[] INDICES={0,6,12,18,24,30};
    private static final int[] LENGTHS={18832,36176,29216,48608,41584,30464};
    public static final int MAX_BODY_BYTES=204880;
    public record Clip(int index,int rate,int frames,byte[] pcm) {
        public Clip { pcm=pcm.clone(); }
        @Override public byte[] pcm() { return pcm.clone(); }
    }
    @FunctionalInterface interface Reader { byte[] read(long offset,int length) throws IOException; }
    private Rac1ShopVoiceSource() {}

    /** Only the independently mapped, approved 208-byte routine is consumed here. */
    public static void verifyDispatcher(byte[] code,boolean pal) throws IOException {
        if (code.length!=208) throw new IOException("Exact dialogue dispatcher extent required");
        var b=ByteBuffer.wrap(code).order(ByteOrder.LITTLE_ENDIAN);
        int[] offsets={8,12,20,24,32,36};
        int[] words={0x3c030013,0x25a2d8f0,pal ? 0x24637c80:0x24637b80,
                0x000210c0,0x00621821,0x8c6401a0};
        for (int i=0;i<offsets.length;i++)
            if (b.getInt(offsets[i])!=words[i]) throw new IOException("Unverified dialogue ToC lookup");
    }
    public static int englishIndex(int streamId) throws IOException {
        int index=streamId-10000;
        if (streamId<10000 || Arrays.stream(INDICES).noneMatch(i -> i==index))
            throw new IOException("Outside approved English dialogue set");
        return index;
    }
    /** Caller supplies the existing validated selected image, global ToC and approved mapped code. */
    public static List<Clip> read(Rac1DiscImage image,byte[] toc,byte[] code) throws IOException {
        return read(image::readBytes,toc,code,image.revision()==Rac1DiscRevision.PAL_V2_00);
    }
    static List<Clip> read(Reader reader,byte[] toc,byte[] code,boolean pal) throws IOException {
        verifyDispatcher(code,pal);
        // Established Rac1DiscTables.read returns six sectors, including table padding.
        if (toc.length<0x2960 || toc.length>6*2048) throw new IOException("Global dialogue ToC extent");
        var table=ByteBuffer.wrap(toc).order(ByteOrder.LITTLE_ENDIAN);
        if (table.getInt(0)!=1 || table.getInt(4)!=0x2960) throw new IOException("Global dialogue ToC identity");
        long[] offsets=new long[6]; int[] lengths=new int[6],rates=new int[6];
        int total=0;
        // Validate all six headers and the aggregate budget before consuming any body.
        for (int n=0;n<INDICES.length;n++) {
            int at=0x1a0+englishIndex(10000+INDICES[n])*8;
            int sector=table.getInt(at),count=table.getInt(at+4);
            if (sector<=0 || count<=0 || count>16384) throw new IOException("Dialogue sector extent");
            offsets[n]=sector*2048L;
            byte[] header=reader.read(offsets[n],48);
            if (header.length!=48) throw new IOException("Truncated dialogue header");
            var h=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            lengths[n]=h.getInt(12); rates[n]=h.getInt(16);
            if (h.getInt(0)!=0x56414770 || h.getInt(4)!=32 || rates[n]!=44056
                    || lengths[n]!=LENGTHS[n] || lengths[n]%16!=0 || 48L+lengths[n]>count*2048L)
                throw new IOException("Unverified English dialogue header");
            total=Math.addExact(total,lengths[n]);
        }
        if (total!=MAX_BODY_BYTES) throw new IOException("Dialogue body budget");
        List<Clip> clips=new ArrayList<>();
        for (int n=0;n<INDICES.length;n++) {
            byte[] body=reader.read(offsets[n]+48,lengths[n]);
            if (body.length!=lengths[n]) throw new IOException("Truncated English dialogue");
            var decoded=PsAdpcm.decode(body,0,body.length);
            // The approved files end with flag1 followed by a flag7 padding frame.
            if (decoded.frames()!=lengths[n]/16-1 || decoded.repeat() || decoded.loopStart()!=-1)
                throw new IOException("Unverified English dialogue termination");
            clips.add(new Clip(INDICES[n],rates[n],decoded.frames(),decoded.pcm()));
        }
        return List.copyOf(clips);
    }
}
