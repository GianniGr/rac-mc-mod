package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.MobyClassReader.*;
import com.github.giannigr.racmc.asset.shop.*;
import java.io.IOException;
import java.nio.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopMaterialCodecTest {
    @Test void exactBigEndianLayoutRoundTripsRawFieldsWithoutColorConversion() throws Exception {
        var source=Rac1ShopCacheTest.metadataFixture();
        byte[] bytes=Rac1ShopMaterialCodec.write(source);
        assertEquals(368,bytes.length);
        var b=ByteBuffer.wrap(bytes);
        assertEquals(1,b.getInt(0)); assertEquals(1,b.getInt(4)); assertEquals(5,b.getInt(8));
        assertEquals(11,b.getInt(12)); assertEquals(0x89abcdef,b.getInt(16));
        assertEquals(0xabcd,b.getShort(20)&65535); assertEquals(255,b.get(22)&255); assertEquals(0,b.get(23));
        assertEquals(1,b.getInt(24)); assertEquals(0,b.getInt(28));
        assertEquals(0,b.get(32)); assertEquals(3,b.getShort(34)); assertEquals(12,b.getInt(36));
        assertEquals(1,b.getInt(52)); assertEquals(0,b.getInt(56));
        assertEquals(0xfedcba9876543210L,b.getLong(60)); assertEquals(0x8877665544332211L,b.getLong(68));
        var read=Rac1ShopMaterialCodec.read(bytes);
        assertArrayEquals(bytes,Rac1ShopMaterialCodec.write(read));
        for(int id:Rac1ShopAssets.MATERIAL_CLASSES) {
            assertEquals(source.get(id).glowRgba(),read.get(id).glowRgba());
            assertArrayEquals(source.get(id).packets().getFirst().normalMultipliers(),
                    read.get(id).packets().getFirst().normalMultipliers());
        }
        assertTrue(read.get(13).packets().getLast().metal());
        assertEquals(0,read.get(13).packets().getLast().normalMultipliers().length);
        assertEquals(-3,read.get(13).packets().getLast().materials().getFirst().textureSelector());
        assertEquals(-1,read.get(1137).packets().getFirst().materials().getFirst().textureSelector());
        assertThrows(UnsupportedOperationException.class,read::clear);
        byte[] defensive=read.get(11).packets().getFirst().normalMultipliers(); defensive[0]=42;
        assertEquals(0,read.get(11).packets().getFirst().normalMultipliers()[0]);
    }

    @Test void malformedIdentityExtentReservedCountsSelectorsAndAllTruncationsReject() throws Exception {
        byte[] valid=Rac1ShopMaterialCodec.write(Rac1ShopCacheTest.metadataFixture());
        List<Consumer<ByteBuffer>> edits=List.of(
                b->b.putInt(0,2),b->b.putInt(4,2),b->b.putInt(8,4),b->b.putInt(8,Integer.MAX_VALUE),
                b->b.putInt(12,12),b->b.putInt(76,11),b->b.putInt(304,168),
                b->b.put(23,(byte)1),b->b.putInt(24,-1),b->b.putInt(24,511),b->b.putInt(24,Integer.MAX_VALUE),
                b->b.putInt(28,-1),b->b.putInt(28,765),b->b.put(32,(byte)2),b->b.put(33,(byte)1),
                b->b.putShort(34,(short)256),b->b.putInt(36,-1),b->b.putInt(36,11),b->b.putInt(36,Integer.MAX_VALUE),
                b->b.putInt(52,-1),b->b.putInt(52,65),b->b.putInt(52,Integer.MAX_VALUE),b->b.putInt(56,-2),
                b->b.put(208,(byte)0),b->b.putInt(212,12),b->b.putInt(204,0),b->b.putInt(220,-1));
        for(int i=0;i<edits.size();i++) {
            byte[] bad=valid.clone(); edits.get(i).accept(ByteBuffer.wrap(bad)); int mutation=i;
            assertThrows(IOException.class,()->Rac1ShopMaterialCodec.read(bad),"mutation"+mutation);
        }
        for(int length=0;length<valid.length;length++) {
            byte[] bad=Arrays.copyOf(valid,length);
            assertThrows(IOException.class,()->Rac1ShopMaterialCodec.read(bad),"truncation"+length);
        }
        assertThrows(IOException.class,()->Rac1ShopMaterialCodec.read(Arrays.copyOf(valid,valid.length+1)));
        assertThrows(IOException.class,()->Rac1ShopMaterialCodec.read(new byte[Rac1ShopMaterialCodec.MAX_BYTES+1]));
    }

    @Test void externalRecordsAreValidatedWithoutSortingOrRepairingPacketOrder() {
        var source=Rac1ShopCacheTest.metadataFixture();
        var normal=source.get(11).packets().getFirst();
        var metal=new PacketMetadata(3,true,1,new byte[0],List.of(new PacketMaterial(-2,1,2)));
        List<List<PacketMetadata>> invalid=List.of(
                List.of(normal,normal),List.of(metal,normal),
                List.of(metal,new PacketMetadata(2,true,1,new byte[0],metal.materials())),
                List.of(new PacketMetadata(1,false,3,new byte[11],List.of())),
                List.of(new PacketMetadata(1,true,1,new byte[4],metal.materials())));
        for(var packets:invalid) {
            var bad=new HashMap<>(source); bad.put(11,new ClassMaterialMetadata(0,0,0,packets));
            assertThrows(IOException.class,()->Rac1ShopMaterialCodec.write(bad));
        }
        var missing=new HashMap<>(source); missing.remove(1143);
        assertThrows(IOException.class,()->Rac1ShopMaterialCodec.write(missing));
        var unknown=new HashMap<>(source); unknown.put(168,source.get(11));
        assertThrows(IOException.class,()->Rac1ShopMaterialCodec.write(unknown));
        for(int[] modes:new int[][]{{-1,0},{65536,0},{0,-1},{0,256}}) {
            var bad=new HashMap<>(source); bad.put(11,new ClassMaterialMetadata(0,modes[0],modes[1],List.of(normal)));
            assertThrows(IOException.class,()->Rac1ShopMaterialCodec.write(bad));
        }
    }
    @Test void boundaryCountsAndEmptyMaterialCarryRemainLossless() throws Exception {
        List<PacketMetadata> packets=new ArrayList<>();
        var normalMaterials=Collections.nCopies(64,new PacketMaterial(Integer.MAX_VALUE,Long.MIN_VALUE,Long.MAX_VALUE));
        var metalMaterials=Collections.nCopies(64,new PacketMaterial(-2,Long.MAX_VALUE,Long.MIN_VALUE));
        byte[] raw=new byte[1020]; Arrays.fill(raw,(byte)0xfe);
        for(int i=0;i<255;i++) packets.add(new PacketMetadata(i,false,255,raw,normalMaterials));
        for(int i=510;i<765;i++) packets.add(new PacketMetadata(i,true,255,new byte[0],metalMaterials));
        Map<Integer,ClassMaterialMetadata> metadata=new HashMap<>();
        for(int id:Rac1ShopAssets.MATERIAL_CLASSES)
            metadata.put(id,new ClassMaterialMetadata(Integer.MIN_VALUE,65535,255,packets));
        byte[] bytes=Rac1ShopMaterialCodec.write(metadata);
        assertEquals(12+5*(16+255*(16+1020+1280)+255*(16+1280)),bytes.length);
        assertArrayEquals(bytes,Rac1ShopMaterialCodec.write(Rac1ShopMaterialCodec.read(bytes)));
        var carry=new PacketMetadata(0,false,0,new byte[0],List.of());
        for(int id:Rac1ShopAssets.MATERIAL_CLASSES)
            metadata.put(id,new ClassMaterialMetadata(0,0,0,List.of(carry)));
        assertTrue(Rac1ShopMaterialCodec.read(Rac1ShopMaterialCodec.write(metadata)).get(11)
                .packets().getFirst().materials().isEmpty());
    }
}
