package com.github.giannigr.racmc.asset;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobyMaterialMetadataTest {
    @Test void rawSourceFieldsSeparateMetalAndPreserveOriginalIndices() throws Exception {
        var metadata = MobyClassReader.of(MobyMaterialFixture.blob()).readMaterialMetadata();
        assertEquals(0x80969696,metadata.glowRgba());
        assertEquals(0xabcd,metadata.mode44()); assertEquals(255,metadata.mode47());
        assertEquals(List.of(0,1,3,4),metadata.packets().stream().map(MobyClassReader.PacketMetadata::packetIndex).toList());
        for (var packet : metadata.packets()) {
            int p = packet.packetIndex();
            assertEquals(p>=3,packet.metal()); assertEquals(3,packet.vertexCount());
            assertEquals(p>=3 ? 0 : 12,packet.normalMultipliers().length);
            for (int i=0;i<packet.normalMultipliers().length;i++)
                assertEquals(p*32+i,packet.normalMultipliers()[i]&255);
            assertEquals(1,packet.materials().size());
            var material = packet.materials().getFirst();
            assertEquals(p==0 ? -1 : p==1 ? 3 : p==3 ? -2 : -3,material.textureSelector());
            assertEquals(0xfedcba9876543210L+p,material.tex1());
            assertEquals(0x8877665544332211L+p,material.clamp());
        }
    }

    @Test void resultsOwnRawBytesAndImmutableListsAtEveryBoundary() throws Exception {
        byte[] blob = MobyMaterialFixture.blob();
        var metadata = MobyClassReader.of(blob).readMaterialMetadata();
        var packet = metadata.packets().getFirst();
        byte[] before = packet.normalMultipliers();
        blob[MobyMaterialFixture.HEADER+96]=99;
        packet.normalMultipliers()[0]=77;
        assertArrayEquals(before,packet.normalMultipliers());
        assertThrows(UnsupportedOperationException.class,() -> metadata.packets().clear());
        assertThrows(UnsupportedOperationException.class,() -> packet.materials().clear());
        var materials = new ArrayList<>(packet.materials());
        var copy = new MobyClassReader.PacketMetadata(0,false,3,before,materials);
        before[0]=55; materials.clear();
        assertEquals(0,copy.normalMultipliers()[0]); assertEquals(1,copy.materials().size());
        var packets = new ArrayList<>(metadata.packets());
        var classCopy = new MobyClassReader.ClassMaterialMetadata(1,2,3,packets);
        packets.clear(); assertEquals(4,classCopy.packets().size());
    }

    @Test void oldDecodedMeshDigestRemainsPinnedBeforeAndAfterMetadataRead() throws Exception {
        byte[] source = MobyMaterialFixture.blob(), before = source.clone();
        var reader = MobyClassReader.of(source);
        // Independently compiled 90bc536 reader in a private JDK-only runner, all seven mesh arrays.
        String prior = "7843fe936e76b706cd690e3f0d2711feb5cdc971b2055cce78d9729923c68150";
        assertEquals(prior,MobyMaterialFixture.digest(reader.readMesh()));
        reader.readMaterialMetadata();
        assertEquals(prior,MobyMaterialFixture.digest(reader.readMesh()));
        assertArrayEquals(before,source);
    }

    @Test void everyFullMaterialBlockIsRetainedAndValidated() throws Exception {
        byte[] source = MobyMaterialFixture.blob();
        var b = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        int list = MobyMaterialFixture.LIST+4*0x80;
        b.putShort(MobyMaterialFixture.TABLE+4*16+4,(short)10);
        b.putInt(list+16,0x6c080000);
        b.putLong(list+20+64,0x123456789abcdef0L);
        b.putLong(list+36+64,0x8877665544332211L);
        b.putInt(list+52+64,-2);
        var materials = MobyClassReader.of(source).readMaterialMetadata().packets().getLast().materials();
        assertEquals(2,materials.size());
        assertEquals(-3,materials.getFirst().textureSelector());
        assertEquals(new MobyClassReader.PacketMaterial(-2,0x123456789abcdef0L,0x8877665544332211L),materials.getLast());
        b.putInt(list+52+64,0);
        assertThrows(AssetFormatException.class,() -> MobyClassReader.of(source).readMaterialMetadata());
    }

    @Test void absentMaterialUnpackPreservesSourceStateCarryWithoutFabricatedMaterial() throws Exception {
        byte[] blob = MobyMaterialFixture.blob();
        ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).putShort(MobyMaterialFixture.TABLE+4,(short)2);
        var packet = MobyClassReader.of(blob).readMaterialMetadata().packets().getFirst();
        assertTrue(packet.materials().isEmpty());
        assertEquals(12,packet.normalMultipliers().length);
    }

    @Test void malformedRangesCountsShapesAndSelectorsFailClosed() {
        int table = MobyMaterialFixture.TABLE, header = MobyMaterialFixture.HEADER, list = MobyMaterialFixture.LIST;
        List<Consumer<ByteBuffer>> mutations = List.of(
                b -> b.putInt(0,-1), b -> b.putInt(0,Integer.MAX_VALUE), b -> b.put(4,(byte)255),
                b -> b.put(7,(byte)0), b -> b.put(7,(byte)5),
                b -> b.putInt(table,-1), b -> b.putShort(table+4,(short)0),
                b -> b.putShort(table+4,(short)65535), b -> b.putInt(table+8,-1),
                b -> b.put(table+12,(byte)1), b -> b.put(table+15,(byte)4),
                b -> b.putInt(header,-1), b -> b.putInt(header,Integer.MAX_VALUE),
                b -> b.putInt(header+4,-1), b -> b.putInt(header+8,Integer.MAX_VALUE),
                b -> b.putInt(header+12,-1), b -> b.putInt(header+16,Integer.MAX_VALUE),
                b -> b.putInt(header+20,2), b -> b.putInt(header+24,16),
                b -> b.putInt(header+28,95), b -> b.putInt(header+28,Integer.MAX_VALUE),
                b -> b.putInt(header+28,112),
                b -> b.putInt(list,0x65020000), b -> b.putInt(list,0x61030000),
                b -> b.putInt(list+16,0x6d030000),
                b -> b.putInt(list+32,0x6c030000), // partial 64-byte material block
                b -> b.putInt(list+32,0x68040000),
                b -> b.putInt(list+32,0x6c080000), // truncated full blocks
                b -> b.putInt(list+32+36,-2), // metal selector in normal packet
                b -> b.putInt(header+3*0x100,4),
                b -> b.put(table+3*16+12,(byte)3),
                b -> b.putInt(list+3*0x80+16+36,-1));
        for (int i=0;i<mutations.size();i++) {
            byte[] blob = MobyMaterialFixture.blob();
            mutations.get(i).accept(ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN));
            int index=i;
            assertThrows(AssetFormatException.class,() -> MobyClassReader.of(blob).readMaterialMetadata(),"mutation"+index);
        }
        byte[] blob = MobyMaterialFixture.blob();
        for (int length : new int[]{0,0x47,table+79,header+107})
            assertThrows(AssetFormatException.class,() -> MobyClassReader.of(Arrays.copyOf(blob,length)).readMaterialMetadata());
    }
}
