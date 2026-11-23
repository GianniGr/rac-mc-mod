package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.asset.MobyClassReader.*;
import com.github.giannigr.racmc.asset.SyntheticVendor;
import com.github.giannigr.racmc.asset.shop.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopMaterialAdmissionTest {
    private Rac1ShopAssets with(Rac1ShopAssets source,Map<Integer,ClassMaterialMetadata> metadata) {
        return new Rac1ShopAssets(source.discSha1(),source.language(),source.graphics(),source.text(),
                source.sceneTables(),source.models(),metadata);
    }
    private Map<Integer,ClassMaterialMetadata> wrongCount(int id) {
        var metadata=new HashMap<>(Rac1ShopCacheTest.metadataFixture());
        // Shape-valid metadata with an intentionally DIFFERENT actual normal vertex sum.
        metadata.put(id,new ClassMaterialMetadata(0,0,0,List.of(
                new PacketMetadata(0,false,2,new byte[8],List.of()))));
        return metadata;
    }
    @Test void missingMalformedAndMismatchedModelMetadataNeverPassAdmission() throws Exception {
        var complete=Rac1ShopCacheTest.fixture();
        var missing=with(complete,Map.of());
        assertTrue(missing.missingMaterialMetadata().contains("shop_material_metadata"));
        assertThrows(IOException.class,()->Rac1ShopCacheCodec.write(missing));
        for(int id:new int[]{12,13,1137}) {
            var wrong=with(complete,wrongCount(id));
            assertTrue(wrong.missingMaterialMetadata().contains("shop_material_model_vertex_count_"+id));
            assertThrows(IOException.class,()->Rac1ShopCacheCodec.write(wrong));
        }
        var main=SyntheticVendor.content();
        for(int id:new int[]{11,1143}) {
            var wrong=with(complete,wrongCount(id));
            // Main model references are checked at combined admission, not fabricated in the sidecar.
            var decoded=Rac1ShopCacheCodec.read(Rac1ShopCacheCodec.write(wrong),wrong.discSha1(),0);
            assertTrue(Rac1ShopReadiness.inspect(decoded,main).missingResources()
                    .contains("referenced_material_model_vertex_count_"+id));
        }
        var report=Rac1ShopReadiness.inspect(complete,main);
        assertFalse(report.missingResources().stream().anyMatch(s->s.contains("material")));
        assertFalse(report.ready());
        assertTrue(report.pendingSceneComponents().contains("verified_vendor_audio_UNBOUND"));
    }

    private int metadataLengthOffset(byte[] bytes) throws Exception {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        in.skipNBytes(12); in.readUTF(); in.readInt();
        in.skipNBytes(in.readInt()); in.skipNBytes(Rac1ShopSceneTableCodec.BYTES);
        int modelLength=in.readInt();
        assertArrayEquals(Rac1ShopModelCodec.write(Rac1ShopCacheTest.fixture().models()),in.readNBytes(modelLength));
        return bytes.length-in.available();
    }
    private void checksum(byte[] bytes) {
        var crc=new CRC32(); crc.update(bytes,0,bytes.length-8);
        ByteBuffer.wrap(bytes).putLong(bytes.length-8,crc.getValue());
    }
    @Test void exactEnvelopePositionAndRechecksummedMalformedMetadataStillReject() throws Exception {
        var fixture=Rac1ShopCacheTest.fixture();
        byte[] good=Rac1ShopCacheCodec.write(fixture);
        int offset=metadataLengthOffset(good);
        int length=ByteBuffer.wrap(good).getInt(offset);
        assertArrayEquals(Rac1ShopMaterialCodec.write(fixture.materialMetadata()),Arrays.copyOfRange(good,offset+4,offset+4+length));
        assertEquals(fixture.text().ids().size(),ByteBuffer.wrap(good).getInt(offset+4+length));
        for(int invalid:new int[]{-1,0,91,Rac1ShopMaterialCodec.MAX_BYTES+1,Integer.MAX_VALUE}) {
            byte[] bad=good.clone(); ByteBuffer.wrap(bad).putInt(offset,invalid); checksum(bad);
            assertThrows(IOException.class,()->Rac1ShopCacheCodec.read(bad,fixture.discSha1(),0));
        }
        for(int relative:new int[]{0,4,8,12,23,24,28,32,33,36,52,56}) {
            byte[] bad=good.clone(); bad[offset+4+relative]^=(byte)0x80; checksum(bad);
            assertThrows(IOException.class,()->Rac1ShopCacheCodec.read(bad,fixture.discSha1(),0),"metadata field"+relative);
        }
        byte[] old=good.clone(); ByteBuffer.wrap(old).putInt(4,4).putInt(8,4); checksum(old);
        assertThrows(IOException.class,()->Rac1ShopCacheCodec.read(old,fixture.discSha1(),0));
        byte[] extra=Arrays.copyOf(good,good.length+1); checksum(extra);
        assertThrows(IOException.class,()->Rac1ShopCacheCodec.read(extra,fixture.discSha1(),0));
    }
    @Test void shapeValidButWrongStoredMeshCountsRejectOnReadWithValidCrc() throws Exception {
        var fixture=Rac1ShopCacheTest.fixture();
        byte[] good=Rac1ShopCacheCodec.write(fixture);
        int offset=metadataLengthOffset(good), oldLength=ByteBuffer.wrap(good).getInt(offset);
        for(int id:new int[]{12,13,1137}) {
            byte[] replacement=Rac1ShopMaterialCodec.write(wrongCount(id));
            byte[] bad=new byte[good.length-oldLength+replacement.length];
            System.arraycopy(good,0,bad,0,offset);
            ByteBuffer.wrap(bad).putInt(offset,replacement.length);
            System.arraycopy(replacement,0,bad,offset+4,replacement.length);
            System.arraycopy(good,offset+4+oldLength,bad,offset+4+replacement.length,
                    good.length-offset-4-oldLength);
            checksum(bad);
            IOException failure=assertThrows(IOException.class,()->Rac1ShopCacheCodec.read(bad,fixture.discSha1(),0));
            assertTrue(failure.getMessage().contains("Incomplete shop material metadata"));
        }
    }
}
