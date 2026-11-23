package com.github.giannigr.racmc.client.shop;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Verified numeric metadata only. A threshold is NOT yet a duration, frame count or playback trigger. */
public final class Rac1ShopVoiceTiming {
    private static final int[] NTSC={
            0x2aeec8,0x2af228,0x2aef68,0x2aefd0,0x2af010,0x2af01c,0x2af028,
            0x2af228,0x2af228,0x2af228,0x2af0b4,0x2af1b0,0x2af138};
    private static final int[] PAL={
            0x2b0158,0x2b04b8,0x2b01f8,0x2b0260,0x2b02a0,0x2b02ac,0x2b02b8,
            0x2b04b8,0x2b04b8,0x2b04b8,0x2b0344,0x2b0440,0x2b03c8};
    private static final int[] THRESHOLDS={100,100,200,100,44,100};
    /** Source states4/5/6 and the corresponding two-variant sequence/stream selection. */
    public record Binding(int sourceState,int sequence,int streamId,int rawPredicateThreshold) {}
    private final boolean pal;
    private Rac1ShopVoiceTiming(boolean pal) { this.pal=pal; }

    /** Inputs are exactly the approved52/24-byte mapped ranges, not arbitrary adjacent overlay data. */
    public static Rac1ShopVoiceTiming read(byte[] stateTargets,byte[] variantThresholds,boolean pal) throws IOException {
        verify(stateTargets,pal ? PAL:NTSC);
        verify(variantThresholds,THRESHOLDS);
        return new Rac1ShopVoiceTiming(pal);
    }
    private static void verify(byte[] bytes,int[] expected) throws IOException {
        if (bytes==null || bytes.length!=expected.length*4) throw new IOException("Exact presenter table extent required");
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int value:expected) if (b.getInt()!=value) throw new IOException("Unverified presenter table");
    }
    public int stateTarget(int state) {
        if (state<0 || state>=13) throw new IllegalArgumentException("Presenter state outside source table");
        return (pal ? PAL:NTSC)[state];
    }
    public Binding binding(int sourceState,int variant) {
        if (sourceState<4 || sourceState>6 || variant<0 || variant>1)
            throw new IllegalArgumentException("Outside verified speaking state/variant");
        int category=sourceState-4;
        return new Binding(sourceState,sourceState+3*variant,
                10000+category*6+variant*18,THRESHOLDS[category*2+variant]);
    }
}
