package com.github.giannigr.racmc.client.shop;

import java.io.IOException;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopVoiceSourceTest {
    static final int[] INDICES={0,6,12,18,24,30},LENGTHS={18832,36176,29216,48608,41584,30464};
    static byte[] code(boolean pal) {
        var b=ByteBuffer.allocate(208).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(8,0x3c030013).putInt(12,0x25a2d8f0).putInt(20,pal ? 0x24637c80:0x24637b80)
                .putInt(24,0x000210c0).putInt(32,0x00621821).putInt(36,0x8c6401a0);
        return b.array();
    }
    static byte[] toc() {
        var b=ByteBuffer.allocate(0x2960).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,1).putInt(4,0x2960);
        for (int i=0;i<6;i++) b.putInt(0x1a0+INDICES[i]*8,100+i*32).putInt(0x1a4+INDICES[i]*8,32);
        return b.array();
    }
    static byte[] header(int length) {
        var b=ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0,0x56414770).putInt(4,32).putInt(12,length).putInt(16,44056);
        return b.array();
    }
    @Test void bothDiscGuardsRefuseChangedLookupAndOnlyApprovedEnglishIds() throws Exception {
        for (boolean pal:new boolean[]{false,true}) {
            Rac1ShopVoiceSource.verifyDispatcher(code(pal),pal);
            assertThrows(IOException.class,() -> Rac1ShopVoiceSource.verifyDispatcher(code(pal),!pal));
            for (int offset:new int[]{8,12,20,24,32,36}) {
                byte[] corrupt=code(pal); corrupt[offset]^=1;
                assertThrows(IOException.class,() -> Rac1ShopVoiceSource.verifyDispatcher(corrupt,pal));
            }
        }
        for (int index:INDICES) assertEquals(index,Rac1ShopVoiceSource.englishIndex(10000+index));
        for (int id:new int[]{Integer.MIN_VALUE,9999,10001,10036,Integer.MAX_VALUE})
            assertThrows(IOException.class,() -> Rac1ShopVoiceSource.englishIndex(id));
    }
    @Test void exactBudgetDecodedInMemoryAndHeadersAllCheckedBeforeBodies() throws Exception {
        List<Long> reads=new ArrayList<>(); int[] bodies={0};
        var clips=Rac1ShopVoiceSource.read((offset,length) -> {
            reads.add(offset);
            int n=(int)(offset/2048-100)/32;
            assertTrue(n>=0 && n<6);
            if (length==48) return header(LENGTHS[n]);
            assertEquals((100+n*32)*2048L+48,offset);
            assertEquals(LENGTHS[n],length); bodies[0]+=length;
            byte[] body=new byte[length]; body[length-31]=1; body[length-15]=7;
            return body;
        },toc(),code(false),false);
        assertEquals(204880,bodies[0]); assertEquals(12,reads.size()); assertEquals(6,clips.size());
        for (int i=0;i<6;i++) {
            assertEquals((100+i*32)*2048L,reads.get(i));
            assertEquals((100+i*32)*2048L+48,reads.get(i+6));
        }
        assertEquals(716744,clips.stream().mapToInt(c -> c.pcm().length).sum());
        assertThrows(UnsupportedOperationException.class,() -> clips.clear());
        byte[] copy=clips.getFirst().pcm(); copy[0]=123;
        assertEquals(0,clips.getFirst().pcm()[0]);
        int[] headers={0};
        assertThrows(IOException.class,() -> Rac1ShopVoiceSource.read((offset,length) -> {
            assertEquals(48,length,"Invalid metadata must not allow body reads");
            int n=headers[0]++;
            return header(n==5 ? 16:LENGTHS[n]);
        },toc(),code(false),false));
    }
}
