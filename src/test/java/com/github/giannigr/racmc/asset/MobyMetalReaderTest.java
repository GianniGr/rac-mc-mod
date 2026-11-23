package com.github.giannigr.racmc.asset;

import java.nio.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** All source bytes hand-built; no proprietary geometry fixture. */
class MobyMetalReaderTest {
    private static final int TABLE=0x48, LIST=0x100, HEADER=0x500;
    private byte[] blob() {
        byte[] bytes=new byte[0x1000];
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,TABLE); bytes[6]=2; bytes[8]=3; b.putFloat(0x24,1);
        for(int p=0;p<2;p++) {
            int entry=TABLE+p*16, list=LIST+p*0x100, header=HEADER+p*0x100;
            b.putInt(entry,list); b.putShort(entry+4,(short)(p==0 ? 6 : 2));
            b.putInt(entry+8,header); bytes[entry+12]=4; bytes[entry+15]=3;
            b.putInt(header,3); b.putInt(header+4,16); b.putInt(header+8,48); b.putInt(header+12,64);
            for(int v=0;v<3;v++) {
                int at=header+16+v*16;
                b.putShort(at,(short)(v+1)); b.putShort(at+2,(short)(-v-2)); b.putShort(at+4,(short)(v+3));
                bytes[at+6]=(byte)(v*64); bytes[at+7]=(byte)(v==2 ? 64 : 0);
                bytes[at+8]=(byte)v; bytes[at+11]=1;
            }
            b.putInt(list,0x6e030000); bytes[list+6]=(byte)(p==0 ? 0x81 : 0);
            // Packet0 binds on escaped first corner; packet1 inherits but starts new corner history.
            byte[] stream=p==0 ? new byte[]{0,(byte)0x82,3,1,1,1,0,0}
                    : new byte[]{(byte)0x81,(byte)0x82,3,1,1,1,0,0};
            System.arraycopy(stream,0,bytes,list+8,stream.length);
            if(p==0) { b.putInt(list+16,0x6c040000); b.putInt(list+52,-2); }
        }
        return bytes;
    }
    @Test void rawVerticesAnglesStateCarryAndPacketCornerHistoryArePreserved() throws Exception {
        var packets=MobyClassReader.of(blob()).readMetalPackets();
        assertEquals(2,packets.size());
        for(int p=0;p<2;p++) {
            var packet=packets.get(p); assertEquals(p,packet.packetIndex()); assertEquals(3,packet.vertices().size());
            var v=packet.vertices().get(1);
            assertEquals(2,v.x()); assertEquals(-3,v.y()); assertEquals(4,v.z());
            assertEquals(64,v.normalAzimuth()); assertEquals(0,v.normalElevation());
            assertArrayEquals(new byte[]{1,0,0},v.jointIndices());
            assertEquals(3,packet.corners().size()); // exactly3 tail corners removed
            for(int i=0;i<3;i++) {
                var c=packet.corners().get(i); assertEquals(i,c.vertex()); assertEquals(i<2,c.noKick());
                assertEquals(0,c.materialPacket()); assertEquals(0,c.materialBlock());
            }
        }
    }
    @Test void resultArraysAndListsAreOwnedAndEmptyMetalListIsImmutable() throws Exception {
        byte[] bytes=blob();
        var packets=MobyClassReader.of(bytes).readMetalPackets();
        var v=packets.getFirst().vertices().getFirst();
        bytes[HEADER+24]=2; v.jointIndices()[0]=2; v.weightBytes()[0]=55;
        assertEquals(0,v.jointIndices()[0]); assertEquals(0,v.weightBytes()[0]);
        assertThrows(UnsupportedOperationException.class,packets::clear);
        assertThrows(UnsupportedOperationException.class,()->packets.getFirst().vertices().clear());
        assertThrows(UnsupportedOperationException.class,()->packets.getFirst().corners().clear());
        bytes[6]=0;
        assertTrue(MobyClassReader.of(bytes).readMetalPackets().isEmpty());
    }
    @Test void countZeroOneAndTwoThreeDirectWeightsAreNotSlotCacheOrDiv255() throws Exception {
        byte[] bytes=blob(); var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        bytes[HEADER+16+11]=0; bytes[HEADER+16+12]=(byte)123;
        int two=HEADER+32; bytes[two+11]=2; bytes[two+8]=0; bytes[two+9]=2;
        bytes[two+12]=(byte)128; bytes[two+13]=(byte)128;
        int three=HEADER+48; bytes[three+11]=3; bytes[three+8]=0; bytes[three+9]=1; bytes[three+10]=2;
        bytes[three+12]=64; bytes[three+13]=64; bytes[three+14]=(byte)128;
        var vertices=MobyClassReader.of(bytes).readMetalPackets().getFirst().vertices();
        assertEquals(0,vertices.get(0).count()); assertEquals(123,vertices.get(0).weightBytes()[0]&255);
        assertEquals(2,vertices.get(1).count()); assertEquals(3,vertices.get(2).count());
        assertEquals(256,Arrays.stream(new int[]{vertices.get(2).weightBytes()[0]&255,
                vertices.get(2).weightBytes()[1]&255,vertices.get(2).weightBytes()[2]&255}).sum());
    }
    @Test void materialSecretsUseQuadwordStrideAndNoKickDoesNotRepairStripWinding() throws Exception {
        byte[] bytes=blob(); var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        bytes[6]=1; b.putShort(TABLE+4,(short)10); // index16 + material command/payload132 padded
        b.putInt(LIST+16,0x6c080000);
        b.putInt(LIST+52,-2); b.putInt(LIST+116,-3);
        bytes[LIST+32]=(byte)0x82; // secret1 at material payload+12 (not next block)
        bytes[LIST+48]=0;         // secret2 at payload+28 terminates
        byte[] stream={0,2,3,0,1,3,1,1,1,0};
        b.putInt(LIST,0x6e040000); //16-byte index payload, shift material command by4
        byte[] materials=Arrays.copyOfRange(bytes,LIST+16,LIST+148);
        Arrays.fill(bytes,LIST,LIST+160,(byte)0);
        b.putInt(LIST,0x6e040000); bytes[LIST+6]=(byte)0x81;
        System.arraycopy(stream,0,bytes,LIST+8,stream.length);
        System.arraycopy(materials,0,bytes,LIST+20,materials.length);
        var corners=MobyClassReader.of(bytes).readMetalPackets().getFirst().corners();
        assertEquals(6,corners.size());
        assertEquals(0,corners.get(2).materialBlock()); assertEquals(1,corners.get(3).materialBlock());
        assertTrue(corners.get(3).noKick()); assertFalse(corners.get(4).noKick());
        assertEquals(1,corners.get(4).materialBlock());
    }
    @Test void malformedSourceFieldsRangesWeightsStreamsAndPackingFailClosed() {
        List<Consumer<ByteBuffer>> edits=List.of(
                b->b.putInt(0,-1),b->b.putInt(0,Integer.MAX_VALUE),b->b.put(7,(byte)1),
                b->b.put(8,(byte)0),b->b.put(8,(byte)112),
                b->b.putInt(TABLE,-1),b->b.putShort(TABLE+4,(short)65535),b->b.putInt(TABLE+8,-1),
                b->b.put(TABLE+12,(byte)1),b->b.put(TABLE+15,(byte)4),
                b->b.putInt(HEADER,4),b->b.putInt(HEADER+4,0),b->b.putInt(HEADER+8,0),b->b.putInt(HEADER+12,0),
                b->b.put(HEADER+16+8,(byte)3),b->b.put(HEADER+16+11,(byte)4),b->b.put(HEADER+16+15,(byte)1),
                b->b.put(HEADER+16+11,(byte)2), // two zero weights
                b->b.putInt(LIST,0x6d030000),b->b.put(LIST+7,(byte)1),
                b->b.put(LIST+6,(byte)0),b->b.put(LIST+6,(byte)0x84),
                b->b.put(LIST+9,(byte)0x80),b->b.put(LIST+11,(byte)2),
                b->b.putInt(LIST+52,-1),b->b.put(LIST+32,(byte)0x81),
                b->b.put(LIST+8,(byte)1)); // absent first binding
        for(int i=0;i<edits.size();i++) {
            byte[] bad=blob(); edits.get(i).accept(ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN)); int mutation=i;
            assertThrows(AssetFormatException.class,()->MobyClassReader.of(bad).readMetalPackets(),"mutation"+mutation);
        }
    }
    @Test void everyRequiredByteTruncationFailsAndNoMetalReturnsOwnedEmptyList() {
        byte[] source=blob();
        for(int length=0;length<HEADER+0x100+64;length++) {
            byte[] truncated=Arrays.copyOf(source,length); int extent=length;
            assertThrows(AssetFormatException.class,()->MobyClassReader.of(truncated).readMetalPackets(),"extent"+extent);
        }
        source[6]=0;
        assertDoesNotThrow(()->MobyClassReader.of(source).readMetalPackets());
    }
}
