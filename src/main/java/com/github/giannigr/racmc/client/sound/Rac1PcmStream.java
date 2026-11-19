package com.github.giannigr.racmc.client.sound;

import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** PCM reads/copies only; never decodes or opens an ISO/cache on the audio thread. */
public final class Rac1PcmStream implements AudioStream {
    private byte[] pcm;
    private final AudioFormat format;
    private int cursor;
    private final int loopStart;
    private final int loopEnd;
    public Rac1PcmStream(byte[] pcm, float rate) {
        this(pcm, rate, -1, pcm.length / 2);
    }
    public Rac1PcmStream(byte[] pcm, float rate, int loopStartSamples, int loopEndSamples) {
        if ((pcm.length & 1) != 0 || loopStartSamples < -1 || loopEndSamples != pcm.length / 2
                || loopStartSamples >= loopEndSamples) throw new IllegalArgumentException("Invalid PCM loop");
        this.pcm = pcm;
        loopStart = loopStartSamples * 2;
        loopEnd = loopEndSamples * 2;
        format = new AudioFormat(rate, 16, 1, true, false);
    }
    @Override public AudioFormat getFormat() { return format; }
    @Override public synchronized ByteBuffer read(int requested) {
        int wanted = Math.min(Math.max(0, requested & ~1), 1024 * 1024);
        int length = loopStart >= 0 && pcm.length != 0 ? wanted : Math.min(wanted, pcm.length - cursor);
        ByteBuffer result = ByteBuffer.allocateDirect(length);
        int left = length;
        while (left > 0) {
            if (cursor == loopEnd && loopStart >= 0) cursor = loopStart;
            int count = Math.min(left, pcm.length - cursor);
            result.put(pcm, cursor, count);
            cursor += count; left -= count;
        }
        result.flip();
        return result;
    }
    @Override public synchronized void close() { pcm = new byte[0]; cursor = 0; }
}
