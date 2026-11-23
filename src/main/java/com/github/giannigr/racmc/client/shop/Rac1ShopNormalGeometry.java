package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.disc.*;
import java.util.*;

/** Shop-local reversible-unit adapter. Old shared mesh decode/wire bytes are never changed. */
public final class Rac1ShopNormalGeometry {
    public record Vertex(short packedX,short packedY,short packedZ,float x,float y,float z,
                         float nx,float ny,float nz,float s,float t,int red,int green,int blue,int alpha) {}
    public record Triangle(int a,int b,int c,int selector) {}
    public record Posed(List<Vertex> vertices,List<Triangle> triangles) {
        public Posed { vertices=List.copyOf(vertices); triangles=List.copyOf(triangles); }
    }
    private final int classId,joints;
    private final float scale;
    private final short[] positions;
    private final float[] normals,uv,weights;
    private final int[] skin,indices,selectors,multipliers;

    public static Rac1ShopNormalGeometry read(Rac1LevelAssets level,int id) throws AssetFormatException {
        if (level.level()!=1 || !Set.of(11,12,13,168,1006,1137,1143).contains(id))
            throw new AssetFormatException("Unsupported shop normal identity");
        try {
            var entry=level.coreIndex().mobyClass(id);
            if (entry==null || id!=168 && entry.offset()<=0) throw new AssetFormatException("Missing normal source");
            var reader=MobyClassReader.of(id==168 ? level.gadgetClass(id):level.classBlob(entry.offset()));
            if (id==1006 && reader.jointCount()!=0) throw new AssetFormatException("Ammo must use rigid source geometry");
            return new Rac1ShopNormalGeometry(id,Math.max(1,reader.jointCount()),reader.scale(),reader.readMesh(),reader.readMaterialMetadata());
        } catch (DiscFormatException | com.github.giannigr.racmc.wad.WadFormatException | IllegalArgumentException failure) {
            throw new AssetFormatException("Invalid shop normal geometry",failure);
        }
    }
    private Rac1ShopNormalGeometry(int id,int joints,float scale,MobyClassReader.Mesh mesh,
                                  MobyClassReader.ClassMaterialMetadata metadata) {
        if (joints<1 || joints>111 || !Float.isFinite(scale) || scale<=0 || mesh.vertexCount()>32768)
            throw new IllegalArgumentException("Invalid normal source extent");
        this.classId=id; this.joints=joints; this.scale=scale;
        float[] p=mesh.positions(); positions=new short[p.length];
        for (int i=0;i<p.length;i++) positions[i]=recover(p[i],scale);
        normals=mesh.normals().clone(); uv=mesh.uvs().clone(); weights=mesh.weights().clone();
        skin=mesh.joints().clone(); indices=mesh.indices().clone(); selectors=mesh.materials().clone();
        multipliers=new int[mesh.vertexCount()*4]; int at=0;
        for (var packet:metadata.packets()) if (!packet.metal()) {
            for (byte value:packet.normalMultipliers()) {
                if (at>=multipliers.length) throw new IllegalArgumentException("Normal multiplier overflow");
                multipliers[at++]=value&255;
            }
        }
        if (at!=multipliers.length || normals.length!=p.length || weights.length!=p.length || skin.length!=p.length
                || uv.length!=mesh.vertexCount()*2 || indices.length!=selectors.length*3)
            throw new IllegalArgumentException("Normal stream correlations differ");
        for (int selector:selectors) if (selector < -1) throw new IllegalArgumentException("Metal is not normal geometry");
    }
    /** Recover an existing decoded signed16 source position, not a pose quantization in scaled units. */
    public static short recover(float decoded,float sourceScale) {
        if (!Float.isFinite(decoded) || !Float.isFinite(sourceScale) || sourceScale<=0)
            throw new IllegalArgumentException("Invalid reversible normal units");
        double packed=(double)decoded*1024/sourceScale;
        long nearest=Math.round(packed);
        if (nearest<Short.MIN_VALUE || nearest>Short.MAX_VALUE || Math.abs(packed-nearest)>.01)
            throw new IllegalArgumentException("Decoded position is not reversible signed16 source data");
        return (short)nearest;
    }
    public short[] sourcePositions() { return positions.clone(); }
    public float[] originalPalette(float[] converted) {
        palette(converted);
        float unit=scale/1024f*Rac1AssetExtractor.UNIT_SCALE;
        int[] axis={0,2,1},sign={1,-1,1}; float[] raw=new float[converted.length];
        for (int j=0;j<joints;j++) {
            int at=j*16;
            for (int col=0;col<3;col++) for (int row=0;row<3;row++)
                raw[at+col*4+row]=sign[row]*sign[col]*converted[at+axis[col]*4+axis[row]];
            for (int row=0;row<3;row++) raw[at+12+row]=sign[row]*converted[at+12+axis[row]]/unit;
            raw[at+15]=1;
        }
        palette(raw); return raw;
    }
    /** Explicit class-local F=P*S in raw packed units; outputs ORIGINAL axes, not world/camera fitted. */
    public Posed pose(int paletteClass,float[] rawSkin) {
        if (paletteClass!=classId) throw new IllegalArgumentException("Normal palette class mismatch");
        palette(rawSkin); List<Vertex> vertices=new ArrayList<>();
        for (int v=0;v<positions.length/3;v++) {
            float[] m=new float[16]; float total=0;
            for (int k=0;k<3;k++) {
                int lane=v*3+k; float weight=weights[lane]; int joint=skin[lane];
                if (!Float.isFinite(weight) || weight<0 || joint<0 || joint>=joints)
                    throw new IllegalArgumentException("Invalid normal skin influence");
                total+=weight;
                for (int c=0;c<16;c++) m[c]+=rawSkin[joint*16+c]*weight;
            }
            if (Math.abs(total-1)>.0001) throw new IllegalArgumentException("Normal skin weights are not /256 unity");
            int at=v*3;
            short x=truncate(transform(m,0,positions[at],positions[at+1],positions[at+2],true));
            short y=truncate(transform(m,1,positions[at],positions[at+1],positions[at+2],true));
            short z=truncate(transform(m,2,positions[at],positions[at+1],positions[at+2],true));
            float nx=transform(m,0,normals[at],normals[at+1],normals[at+2],false);
            float ny=transform(m,1,normals[at],normals[at+1],normals[at+2],false);
            float nz=transform(m,2,normals[at],normals[at+1],normals[at+2],false);
            for (float value:new float[]{nx,ny,nz}) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite normal");
            vertices.add(new Vertex(x,y,z,x*scale/1024f,y*scale/1024f,z*scale/1024f,nx,ny,nz,
                    uv[v*2],uv[v*2+1],multipliers[v*4],multipliers[v*4+1],multipliers[v*4+2],multipliers[v*4+3]));
        }
        List<Triangle> triangles=new ArrayList<>();
        for (int t=0;t<selectors.length;t++) triangles.add(new Triangle(indices[t*3],indices[t*3+1],indices[t*3+2],selectors[t]));
        return new Posed(vertices,triangles);
    }
    private static float transform(float[] m,int row,float x,float y,float z,boolean pos) {
        return m[row]*x+m[4+row]*y+m[8+row]*z+(pos ? m[12+row]:0);
    }
    private static short truncate(float v) {
        if (!Float.isFinite(v) || v<=Short.MIN_VALUE-1f || v>=Short.MAX_VALUE+1f)
            throw new IllegalArgumentException("Normal pose outside proven signed16 domain");
        return (short)(int)v;
    }
    private void palette(float[] values) {
        if (values==null || values.length!=joints*16) throw new IllegalArgumentException("Invalid normal palette extent");
        for (int j=0;j<joints;j++) {
            int at=j*16;
            for (int c=0;c<16;c++) if (!Float.isFinite(values[at+c])) throw new IllegalArgumentException("Nonfinite normal palette");
            if (values[at+3]!=0 || values[at+7]!=0 || values[at+11]!=0 || values[at+15]!=1)
                throw new IllegalArgumentException("Nonaffine normal palette");
        }
    }
}
