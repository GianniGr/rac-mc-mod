package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import java.nio.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopNormalGeometryTest {
    @Test void everyNormalTransferReversesToPrimarySigned16AndLiteralPoseTruncatesBeforeScale() throws Exception {
        try (var outcome=new Rac1DiscValidator().inspect(Path.of(System.getProperty("racmc.isoImage")))) {
            assertTrue(outcome.isSupported());
            var level=Rac1LevelAssets.load(outcome.image(),1);
            for (int id:new int[]{11,12,13,1137,1143}) {
                byte[] blob=level.classBlob(level.coreIndex().mobyClass(id).offset());
                var b=ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                var reader=MobyClassReader.of(blob); var g=Rac1ShopNormalGeometry.read(level,id);
                float[] f=new float[reader.jointCount()*16];
                for (int j=0;j<reader.jointCount();j++) {
                    int at=j*16;
                    f[at]=.5f; f[at+1]=-.25f; f[at+4]=.25f; f[at+5]=.5f;
                    f[at+10]=1; f[at+12]=.75f; f[at+13]=-1.25f; f[at+14]=.5f; f[at+15]=1;
                }
                var posed=g.pose(id,f);
                short[] recovered=g.sourcePositions(); int output=0,checked=0;
                for (int p=0;p<(blob[4]&255);p++) {
                    int entry=b.getInt(0)+p*16,header=b.getInt(entry+8);
                    int unique=b.getInt(header+4)+b.getInt(header+8)+b.getInt(header+12);
                    int total=b.getInt(header+20),records=header+b.getInt(header+24);
                    assertTrue(unique>0 && unique<=total && total<=255);
                    assertTrue(records>=0 && (long)records+unique*16<=blob.length);
                    for (int v=0;v<unique;v++) {
                        int at=records+v*16; short x=b.getShort(at+10),y=b.getShort(at+12),z=b.getShort(at+14);
                        assertEquals(x,recovered[(output+v)*3]); assertEquals(y,recovered[(output+v)*3+1]);
                        assertEquals(z,recovered[(output+v)*3+2]);
                        var actual=posed.vertices().get(output+v);
                        assertEquals((short)(int)(.5*x+.25*y+.75),actual.packedX());
                        assertEquals((short)(int)(-.25*x+.5*y-1.25),actual.packedY());
                        assertEquals((short)(int)(z+.5),actual.packedZ());
                        assertEquals(actual.packedX()*reader.scale()/1024f,actual.x());
                        double a=(blob[at+8]&255)*Math.PI/128,e=(blob[at+9]&255)*Math.PI/128;
                        double nx=Math.cos(a)*Math.cos(e),ny=Math.sin(a)*Math.cos(e),nz=Math.sin(e);
                        assertEquals(.5*nx+.25*ny,actual.nx(),2e-6);
                        assertEquals(-.25*nx+.5*ny,actual.ny(),2e-6); assertEquals(nz,actual.nz(),2e-6);
                        checked++;
                    }
                    output+=total;
                }
                assertEquals(output,posed.vertices().size()); assertTrue(checked>0);
                assertEquals(reader.readMesh().materials().length,posed.triangles().size());
                if (id==1137) { assertEquals(92,output); assertTrue(posed.triangles().stream().allMatch(t -> t.selector()==-1)); }
                assertThrows(IllegalArgumentException.class,() -> g.pose(-1,f));
                System.out.printf("%s class%d normal primary unique=%d total=%d triangles=%d reversible-unit/literal-F oracle pass%n",
                        outcome.result().revision(),id,checked,output,posed.triangles().size());
            }
        }
    }
}
