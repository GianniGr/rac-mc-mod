package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.MobyClassReader.*;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.*;
import java.nio.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Python canonical source hashes; real count1 geometry, controlled literal posed palettes. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopMetalGeometryTest {
    @Test void rawCanonicalHashesAndLiteralClassLocalPosedGeometryMatchEachSupportedDisc() throws Exception {
        try(var outcome=new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported(),outcome.result().message());
            boolean pal=outcome.result().revision()==Rac1DiscRevision.PAL_V2_00;
            var level=Rac1LevelAssets.load(outcome.image(),1);
            for(int id:new int[]{13,1143}) {
                var reader=MobyClassReader.of(level.classBlob(level.coreIndex().mobyClass(id).offset()));
                var raw=reader.readMetalPackets();
                var geometry=Rac1ShopMetalGeometry.read(level,id);
                var metadata=reader.readMaterialMetadata();
                String[] hashes=hashes(id,pal);
                assertEquals(hashes.length,raw.size());
                float[] identity=new float[reader.jointCount()*16], literal=new float[identity.length];
                for(int j=0;j<reader.jointCount();j++) {
                    int at=j*16;
                    for(int d=0;d<4;d++) identity[at+d*5]=1;
                    // Explicit source F: x'=.5x+.25y+.75+2j, y'=-.25x+.5y-1.25-j, z'=z+.5+3j.
                    literal[at]=.5f; literal[at+1]=-.25f; literal[at+4]=.25f; literal[at+5]=.5f;
                    literal[at+10]=1; literal[at+12]=.75f+2*j; literal[at+13]=-1.25f-j; literal[at+14]=.5f+3*j;
                    literal[at+15]=1;
                }
                var bind=geometry.pose(id,identity); var posed=geometry.pose(id,literal);
                int vertices=0,triangles=0;
                for(int p=0;p<raw.size();p++) {
                    var packet=raw.get(p); var posedPacket=posed.get(p);
                    var digest=MessageDigest.getInstance("SHA-256");
                    for(int v=0;v<packet.vertices().size();v++) {
                        var original=packet.vertices().get(v); assertEquals(1,original.count(),"Retail shop evidence is count1 only");
                        var bytes=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                        bytes.putShort(original.x()).putShort(original.y()).putShort(original.z());
                        bytes.put((byte)original.normalAzimuth()).put((byte)original.normalElevation());
                        bytes.put(original.jointIndices()).put((byte)original.count()).put(original.weightBytes()).put((byte)0);
                        digest.update(bytes.array());
                        var unchanged=bind.get(p).vertices().get(v);
                        assertEquals(original.x(),unchanged.packedX()); assertEquals(original.y(),unchanged.packedY());
                        assertEquals(original.z(),unchanged.packedZ());
                        int joint=original.jointIndices()[0]&255;
                        int x=(int)(.5*original.x()+.25*original.y()+.75+2*joint);
                        int y=(int)(-.25*original.x()+.5*original.y()-1.25-joint);
                        int z=(int)(original.z()+.5+3*joint);
                        var actual=posedPacket.vertices().get(v);
                        assertEquals(x,actual.packedX()); assertEquals(y,actual.packedY()); assertEquals(z,actual.packedZ());
                        assertEquals(x*reader.scale()/1024f,actual.x());
                        assertEquals(z*reader.scale()/1024f,actual.y());
                        assertEquals(-y*reader.scale()/1024f,actual.z());
                        double a=original.normalAzimuth()*Math.PI/128,e=original.normalElevation()*Math.PI/128;
                        double nx=Math.cos(a)*Math.cos(e),ny=Math.sin(a)*Math.cos(e),nz=Math.sin(e);
                        assertEquals(.5*nx+.25*ny,actual.normalX(),1e-6);
                        assertEquals(nz,actual.normalY(),1e-6);
                        assertEquals(.25*nx-.5*ny,actual.normalZ(),1e-6);
                    }
                    for(var triangle:bind.get(p).triangles()) {
                        var source=metadata.packets().stream().filter(m->m.packetIndex()==triangle.materialPacket()).findFirst().orElseThrow();
                        int selector=source.materials().get(triangle.materialBlock()).textureSelector();
                        digest.update(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(triangle.a()).putInt(triangle.b()).putInt(triangle.c()).putInt(selector).array());
                    }
                    assertEquals(hashes[p],HexFormat.of().formatHex(digest.digest()),"independent packet hash "+packet.packetIndex());
                    vertices+=packet.vertices().size(); triangles+=bind.get(p).triangles().size();
                }
                assertEquals(id==13 ? 156 : pal ? 573 : 576,vertices);
                assertEquals(id==13 ? 52 : 698,triangles);
                // Integration smoke only, NOT a self-derived oracle: the exact literal conjugation oracle is synthetic.
                var asset=Rac1AssetExtractor.readAnimatedAsset(level,id);
                var palette=Rac1AnimationEvaluator.evaluate(asset.skeleton(),asset.animations()[0],0,1);
                assertEquals(raw.size(),geometry.poseConverted(id,palette).size());
                System.out.printf("%s class%d raw metal vertices=%d triangles=%d; independent hashes and literal posed-unit oracle passed%n",
                        outcome.result().revision(),id,vertices,triangles);
            }
        }
    }
    private String[] hashes(int id,boolean pal) {
        if(id==13) return new String[]{
                "679bf721c469a46032571548df118505775e7a0da6f7a6e6ed41ea429c4588da",
                "89823a9291183d1d79388ae4b13b9d560bb3900e253afdd4ff706a5ab1924352"};
        if(pal) return new String[]{
                "1c62b27e1fc433ec6d6ef5c1a33b3f73f87ab092821c8f134c171cc2e07c33b1",
                "d8e921b7e4a27c09510273fc308674979438dce816a02e2aec137c5ea43a6591",
                "1301003eebcd4c78557fa3e8e5779dfb5800ffade8254e0ce2aa7670f58e3c77",
                "b11009ca64f18515b7ed55bb14e7b61bda8213cdb12ffeb8f6c6882d87d1f428",
                "d1edf617ddb4fdfbe838f654693bab48ec5c5e1acddb410cbc1a83224fe4505c",
                "3f773fde68cffe7f2e330ae66a1cdf324d3bf498c0e8599143738538a52f6db7",
                "a62a1df85403cb0607fabff10dd9872e5fe0cf98a446b015d18bce1a2f11d369"};
        return new String[]{
                "6c359ef962347cd2cd6a5c6af77178972a7a3c1df3e6ea1ca4ae52173a1e928b",
                "c812eb52fda466de5596427fcdfc250f1e2c2034f6c9dc4fa0bbf599e2d90fbb",
                "b524ef9f748259f378cdf87bbcf1b9453a5fbaf4fb6a88295a1e5ead574cf0bd",
                "3d229309dc64aa82bbde9e7b886099a129e21f4201d35278e621e6d281a8451a",
                "02eaea6624f20b9e1749466e59824921ef9862160fa320d37eff408cae3cd62e",
                "e1bd5db70382e78b2877efe60985049423fbd751aa50f969054fde315c5aa29a",
                "91ec25fd1f5b0432fb426335e149fd9763380f865083042b62ee1a711aa5e318"};
    }
}
