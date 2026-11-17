package com.github.giannigr.racmc.client.sound;

import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** PCM reads/copies only; never decodes or opens an ISO/cache on the audio thread. */
public final class Rac1PcmStream implements AudioStream {
    private byte[] pcm;
    private final AudioFormat format;
    private int cursor;
    public Rac1PcmStream(byte[] pcm, float rate) {
        this.pcm = pcm;
        format = new AudioFormat(rate, 16, 1, true, false);
    }
    @Override public AudioFormat getFormat() { return format; }
    @Override public synchronized ByteBuffer read(int requested) {
        int length = Math.min(Math.max(0, requested & ~1), pcm.length - cursor);
        ByteBuffer result = ByteBuffer.allocateDirect(length);
        result.put(pcm, cursor, length).flip();
        cursor += length;
        return result;
    }
    @Override public synchronized void close() { pcm = new byte[0]; cursor = 0; }
}
