package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.MobyClassReader.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopMetalGeometryTest {
    private float[] identity(int count) {
        float[] result=new float[count*16];
        for(int j=0;j<count;j++) for(int i=0;i<4;i++) result[j*16+i*5]=1;
        return result;
    }
    private MetalVertex vertex(int count,byte[] joints,byte[] weights) {
        return new MetalVertex((short)2,(short)-1,(short)3,0,0,count,joints,weights);
    }
    private List<MetalPacket> packet(MetalVertex v) {
        return List.of(new MetalPacket(0,List.of(v),List.of()));
    }
    @Test void literalPoseTimesBindNotAttachmentAndQuantizationPrecedesUnitsAndAxes() {
        var source=new Rac1ShopMetalGeometry(13,1,512,packet(vertex(1,new byte[3],new byte[3])));
        float[] p={0,2,0,0,-3,0,0,0,0,0,4,0,.75f,18.75f,2.5f,1};
        float[] s={1,0,0,0,0,1,0,0,0,0,1,0,-10,0,0,1};
        float[] expectedF={0,2,0,0,-3,0,0,0,0,0,4,0,.75f,-1.25f,2.5f,1};
        assertArrayEquals(expectedF,source.skinPalette(p,s));
        var result=source.pose(13,expectedF).getFirst().vertices().getFirst();
        // Literal p'=(3.75,2.75,14.5), truncate=(3,2,14), C=.5*(x,z,-y).
        assertEquals(3,result.packedX()); assertEquals(2,result.packedY()); assertEquals(14,result.packedZ());
        assertEquals(1.5f,result.x()); assertEquals(7,result.y()); assertEquals(-1,result.z());
        assertEquals(0,result.sourceNormalX()); assertEquals(2,result.sourceNormalY()); assertEquals(0,result.sourceNormalZ());
        assertEquals(0,result.normalX()); assertEquals(0,result.normalY()); assertEquals(-2,result.normalZ());
        assertEquals(22,source.pose(13,p).getFirst().vertices().getFirst().packedY()); // pose-only is observably wrong
        assertEquals(18.75f,p[13]); assertEquals(-10,s[12]);
    }
    @Test void convertedPaletteConjugationUsesIndependentLiteralMatricesNotSelfRoundTrip() {
        var source=new Rac1ShopMetalGeometry(13,1,512,packet(vertex(1,new byte[3],new byte[3])));
        // Independently expanding C=.5*(x,z,-y) yields Fmc*(X,Y,Z)=(3Z+.375,4Y+1.25,-2X+.625).
        float[] converted={0,0,-2,0,0,4,0,0,3,0,0,0,.375f,1.25f,.625f,1};
        // The reflected zero lanes preserve their IEEE negative signs; no numerical tolerance.
        float[] expectedRaw={0,2,0,0,-3,0,-0f,0,0,-0f,4,0,.75f,-1.25f,2.5f,1};
        assertArrayEquals(expectedRaw,source.originalPalette(converted));
        var result=source.poseConverted(13,converted).getFirst().vertices().getFirst();
        assertEquals(3,result.packedX()); assertEquals(2,result.packedY()); assertEquals(14,result.packedZ());
        assertEquals(1.5f,result.x()); assertEquals(7,result.y()); assertEquals(-1,result.z());
        assertEquals(-2,result.normalZ());
    }
    @Test void rawZeroOneIgnoreWeightsButTwoThreeBlendDirectJointsOver256() {
        var vertices=List.of(vertex(0,new byte[]{1,-1,-1},new byte[]{5,7,9}),
                vertex(1,new byte[]{1,2,2},new byte[]{-1,-1,-1}),
                vertex(2,new byte[]{0,2,0},new byte[]{-128,-128,0}),
                vertex(3,new byte[]{0,1,2},new byte[]{64,64,-128}));
        var source=new Rac1ShopMetalGeometry(1143,3,512,List.of(new MetalPacket(9,vertices,List.of())));
        float[] palette=identity(3); palette[12]=.75f; palette[28]=2.75f; palette[44]=4.75f;
        var result=source.pose(1143,palette).getFirst().vertices();
        assertEquals(4,result.get(0).packedX()); assertEquals(4,result.get(1).packedX());
        assertEquals(4,result.get(2).packedX()); assertEquals(5,result.get(3).packedX());
        for(var v:result) {
            assertEquals(1,v.sourceNormalX()); assertEquals(0,v.sourceNormalY()); assertEquals(0,v.sourceNormalZ());
            assertEquals(1,v.normalX()); // no /255 and no need to repair-normalize
        }
    }
    @Test void stripNoKickMaterialCarryPacketResetAndDegeneratesStayLiteral() {
        var v=vertex(1,new byte[3],new byte[3]);
        var vertices=Collections.nCopies(3,v);
        List<MetalCorner> corners=List.of(new MetalCorner(0,true,0,0),new MetalCorner(1,true,0,0),
                new MetalCorner(2,false,0,0),new MetalCorner(1,true,0,1),new MetalCorner(2,true,0,1),
                new MetalCorner(0,false,0,1),new MetalCorner(0,false,0,1));
        var source=new Rac1ShopMetalGeometry(13,1,512,List.of(new MetalPacket(0,vertices,corners),
                new MetalPacket(1,vertices,corners.subList(0,3))));
        var packets=source.pose(13,identity(1));
        assertEquals(List.of(new Rac1ShopMetalGeometry.Triangle(0,1,2,0,0),
                new Rac1ShopMetalGeometry.Triangle(1,2,0,0,1),
                new Rac1ShopMetalGeometry.Triangle(2,0,0,0,1)),packets.get(0).triangles());
        assertEquals(1,packets.get(1).triangles().size());
        assertThrows(UnsupportedOperationException.class,packets::clear);
        assertThrows(UnsupportedOperationException.class,()->packets.getFirst().triangles().clear());
    }
    @Test void unsupportedPaletteClassPackingOverflowAndNonfiniteFailNotWrapOrSaturate() {
        var source=new Rac1ShopMetalGeometry(13,1,512,packet(vertex(1,new byte[3],new byte[3])));
        assertThrows(IllegalArgumentException.class,()->source.pose(0,identity(1)));
        assertThrows(IllegalArgumentException.class,()->source.pose(13,identity(2)));
        float[] p=identity(1); p[12]=40000;
        assertThrows(IllegalArgumentException.class,()->source.pose(13,p));
        p[12]=Float.NaN;
        assertThrows(IllegalArgumentException.class,()->source.poseConverted(13,p));
        p[12]=0; p[3]=1;
        assertThrows(IllegalArgumentException.class,()->source.pose(13,p));
        assertThrows(IllegalArgumentException.class,()->new Rac1ShopMetalGeometry(13,1,512,
                packet(vertex(1,new byte[]{2,0,0},new byte[3]))));
        assertThrows(IllegalArgumentException.class,()->new MetalVertex((short)0,(short)0,(short)0,256,0,1,new byte[3],new byte[3]));
    }
}
