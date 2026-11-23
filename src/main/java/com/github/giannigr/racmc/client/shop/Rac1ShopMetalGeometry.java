package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.MobyClassReader.*;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.MobyClassReader;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.DiscFormatException;
import java.util.*;

/**
 * In-memory shop13/1143 metal adapter. No renderer, UV/color fabrication, model-wire or cache section.
 * Palettes are class-local SKIN F=P*S, not pose-only attachments. Java float evaluation is not a VU emulator.
 */
public final class Rac1ShopMetalGeometry {
    public record Vertex(short packedX, short packedY, short packedZ, float x, float y, float z,
                         float sourceNormalX, float sourceNormalY, float sourceNormalZ) {
        public float normalX() { return sourceNormalX; }
        public float normalY() { return sourceNormalZ; }
        public float normalZ() { return -sourceNormalY; }
    }
    public record Triangle(int a, int b, int c, int materialPacket, int materialBlock) {}
    public record Packet(int packetIndex, List<Vertex> vertices, List<Triangle> triangles) {
        public Packet { vertices=List.copyOf(vertices); triangles=List.copyOf(triangles); }
    }

    private final int classNumber, jointCount;
    private final float classScale;
    private final List<MetalPacket> packets;

    /** Consumes the additive raw API directly, bound to a Novalis class-table identity, in memory only. */
    public static Rac1ShopMetalGeometry read(Rac1LevelAssets level, int classNumber) throws AssetFormatException {
        if (level.level() != 1 || (classNumber != 13 && classNumber != 1143))
            throw new AssetFormatException("Unsupported shop metal source identity");
        var entry=level.coreIndex().mobyClass(classNumber);
        if (entry == null || entry.offset() <= 0) throw new AssetFormatException("Missing original shop metal class");
        try {
            var reader=MobyClassReader.of(level.classBlob(entry.offset()));
            var packets=reader.readMetalPackets();
            if (packets.isEmpty()) throw new AssetFormatException("Missing required original shop metal packets");
            return new Rac1ShopMetalGeometry(classNumber,reader.jointCount(),reader.scale(),packets);
        } catch (DiscFormatException | IllegalArgumentException failure) {
            throw new AssetFormatException("Invalid original shop metal source",failure);
        }
    }

    public Rac1ShopMetalGeometry(int classNumber, int jointCount, float classScale, List<MetalPacket> packets) {
        if ((classNumber != 13 && classNumber != 1143) || jointCount < 1 || jointCount > 111
                || !Float.isFinite(classScale) || classScale <= 0 || classScale > 1000 || packets.size() > 255)
            throw new IllegalArgumentException("Unsupported original shop metal source");
        this.classNumber=classNumber; this.jointCount=jointCount; this.classScale=classScale;
        this.packets=List.copyOf(packets);
        Set<Integer> indices=new HashSet<>();
        int previous=-1;
        for (var packet : packets) {
            if (!indices.add(packet.packetIndex()) || packet.packetIndex() <= previous)
                throw new IllegalArgumentException("Duplicate/unordered metal packet");
            previous=packet.packetIndex();
            for (var v : packet.vertices()) {
                byte[] joints=v.jointIndices();
                for (int k=0;k<Math.max(1,v.count());k++)
                    if ((joints[k]&255) >= jointCount) throw new IllegalArgumentException("Metal joint outside local source palette");
            }
            for (var corner : packet.corners())
                if (corner.vertex() >= packet.vertices().size()) throw new IllegalArgumentException("Metal corner outside local packet");
        }
        for (var packet : packets) for (var corner : packet.corners())
            if (!indices.contains(corner.materialPacket()) || corner.materialPacket() > packet.packetIndex())
                throw new IllegalArgumentException("Metal binding outside this class's preceding metal packets");
    }

    /** Column-major original packed-unit P and inverse-bind S. Input counts must match this actor. */
    public float[] skinPalette(float[] pose, float[] inverseBind) {
        palette(pose); palette(inverseBind);
        float[] result=new float[pose.length];
        for (int j=0;j<jointCount;j++) {
            int at=j*16;
            for (int col=0;col<4;col++) for (int row=0;row<4;row++) {
                float value=0;
                for (int k=0;k<4;k++) value+=pose[at+k*4+row]*inverseBind[at+col*4+k];
                result[at+col*4+row]=value;
            }
        }
        palette(result);
        return result;
    }

    /** Explicit C^-1*Fmc*C, C=s*(x,z,-y); no helper-based pose/fit or attachment substitution. */
    public float[] originalPalette(float[] convertedSkin) {
        palette(convertedSkin);
        float unit=classScale/1024f*Rac1AssetExtractor.UNIT_SCALE;
        if (!Float.isFinite(unit) || unit <= 0) throw new IllegalArgumentException("Invalid packed unit scale");
        int[] axis={0,2,1}, sign={1,-1,1};
        float[] original=new float[convertedSkin.length];
        for (int j=0;j<jointCount;j++) {
            int at=j*16;
            for (int col=0;col<3;col++) for (int row=0;row<3;row++)
                original[at+col*4+row]=sign[row]*sign[col]*convertedSkin[at+axis[col]*4+axis[row]];
            for (int row=0;row<3;row++) original[at+12+row]=sign[row]*convertedSkin[at+12+axis[row]]/unit;
            original[at+15]=1;
        }
        palette(original);
        return original;
    }

    public List<Packet> poseConverted(int paletteClass, float[] convertedSkin) {
        if (paletteClass != classNumber) throw new IllegalArgumentException("Metal palette belongs to a different actor");
        return pose(paletteClass,originalPalette(convertedSkin));
    }

    /** Position truncation happens in original packed units BEFORE class scale/1024 and axis/unit map. */
    public List<Packet> pose(int paletteClass, float[] originalSkin) {
        if (paletteClass != classNumber) throw new IllegalArgumentException("Metal palette belongs to a different actor");
        palette(originalSkin);
        float unit=classScale/1024f*Rac1AssetExtractor.UNIT_SCALE;
        if (!Float.isFinite(unit) || unit <= 0) throw new IllegalArgumentException("Invalid packed unit scale");
        List<Packet> result=new ArrayList<>();
        for (var packet : packets) {
            List<Vertex> vertices=new ArrayList<>(packet.vertices().size());
            for (var vertex : packet.vertices()) {
                float[] matrix=new float[16]; byte[] joints=vertex.jointIndices(), weights=vertex.weightBytes();
                for (int k=0;k<Math.max(1,vertex.count());k++) {
                    int at=(joints[k]&255)*16;
                    float weight=vertex.count() <= 1 ? 1 : (weights[k]&255)/256f;
                    for (int i=0;i<16;i++) matrix[i]+=originalSkin[at+i]*weight;
                }
                short x=packed(transform(matrix,0,vertex.x(),vertex.y(),vertex.z(),true));
                short y=packed(transform(matrix,1,vertex.x(),vertex.y(),vertex.z(),true));
                short z=packed(transform(matrix,2,vertex.x(),vertex.y(),vertex.z(),true));
                double a=vertex.normalAzimuth()*Math.PI/128, e=vertex.normalElevation()*Math.PI/128;
                float nx=(float)(Math.cos(a)*Math.cos(e)), ny=(float)(Math.sin(a)*Math.cos(e)), nz=(float)Math.sin(e);
                float transformedX=transform(matrix,0,nx,ny,nz,false);
                float transformedY=transform(matrix,1,nx,ny,nz,false);
                float transformedZ=transform(matrix,2,nx,ny,nz,false);
                finite(transformedX); finite(transformedY); finite(transformedZ);
                vertices.add(new Vertex(x,y,z,x*unit,z*unit,-y*unit,transformedX,transformedY,transformedZ));
            }
            List<Triangle> triangles=new ArrayList<>();
            var corners=packet.corners();
            for (int i=2;i<corners.size();i++) {
                var c=corners.get(i);
                if (!c.noKick()) triangles.add(new Triangle(corners.get(i-2).vertex(),corners.get(i-1).vertex(),
                        c.vertex(),c.materialPacket(),c.materialBlock()));
            }
            result.add(new Packet(packet.packetIndex(),vertices,triangles));
        }
        return List.copyOf(result);
    }

    private float transform(float[] m, int row, float x, float y, float z, boolean position) {
        return m[row]*x+m[4+row]*y+m[8+row]*z+(position ? m[12+row] : 0);
    }
    private short packed(float value) {
        finite(value);
        int truncated=(int)value;
        if (truncated < Short.MIN_VALUE || truncated > Short.MAX_VALUE)
            throw new IllegalArgumentException("Posed metal position outside proved signed16 packing domain");
        return (short)truncated; // no guessed wrapping/saturation for unsupported overflow
    }
    private void palette(float[] palette) {
        if (palette == null || palette.length != jointCount*16) throw new IllegalArgumentException("Wrong class-local metal palette extent");
        for (int j=0;j<jointCount;j++) {
            int at=j*16;
            for (int i=0;i<16;i++) finite(palette[at+i]);
            if (palette[at+3] != 0 || palette[at+7] != 0 || palette[at+11] != 0 || palette[at+15] != 1)
                throw new IllegalArgumentException("Nonaffine metal skin palette");
        }
    }
    private void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite posed metal value");
    }
}
