package com.github.giannigr.racmc.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1ClankPcmTest {
    @Test void verifiedExtentsPlayIntroExactlyOnceAndRepeatOnlyOriginalLoopRegion() {
        // Synthetic ramp PCM, not extracted game audio. Both verified extents tested across chunks.
        for(int[] shape:new int[][]{{20440,28},{13468,168}}) {
            byte[] pcm=new byte[shape[0]*2];
            for(int i=0;i<shape[0];i++){pcm[i*2]=(byte)i;pcm[i*2+1]=(byte)(i>>>8);}
            var stream=new Rac1PcmStream(pcm,shape[1]==28?15984.375f:22019.53125f,shape[1],shape[0]);
            int cursor=0;
            for(int size:new int[]{100,pcm.length-30,pcm.length*2+24}) {
                var chunk=stream.read(size);
                while(chunk.hasRemaining()) {
                    if(cursor==shape[0])cursor=shape[1];
                    int value=(chunk.get()&255)|(chunk.get()&255)<<8;
                    assertEquals(cursor++,value);
                }
            }
            stream.close();assertEquals(0,stream.read(2048).remaining());
        }
    }
}
