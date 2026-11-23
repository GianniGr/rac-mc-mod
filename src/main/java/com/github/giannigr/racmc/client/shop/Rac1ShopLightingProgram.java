package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.shop.Rac1ShopSceneLightSource;

/**
 * Shop-local algebra from original MobyProc/VU0, not a PS2 float emulator or renderer.
 * Matrices contain three column vectors in original axes, without scale or translation.
 * All scene inputs are explicit; this class never chooses a camera or a light bank.
 */
public final class Rac1ShopLightingProgram {
    private Rac1ShopLightingProgram() {}

    /** Transform an original world direction to a model-space to-light vector, -R transpose*d. */
    public static float[] toLight(float[] modelAxes, float[] worldDirection) {
        rotation(modelAxes); bank(worldDirection,3);
        float[] out=new float[3];
        for (int i=0;i<3;i++) {
            int p=i*3;
            out[i]=(-modelAxes[p]*worldDirection[0]-modelAxes[p+1]*worldDirection[1])
                    -modelAxes[p+2]*worldDirection[2];
        }
        bank(out,3); return out;
    }

    /**
     * E from the source world-space camera-to-current-sequence-sphere vector, view and moby rotations.
     * Normalize the world vector once, then view-transform; do NOT separately normalize its yz/xz pairs.
     */
    public static float[] environmentBasis(float[] viewAxes,float[] mobyAxes,float[] cameraToSphere) {
        rotation(viewAxes); rotation(mobyAxes); bank(cameraToSphere,3);
        float length=length(cameraToSphere[0],cameraToSphere[1],cameraToSphere[2]);
        float[] d=transform(viewAxes,cameraToSphere[0]/length,cameraToSphere[1]/length,cameraToSphere[2]/length);
        float[] e=new float[9];
        for (int i=0;i<3;i++) {
            int p=i*3;
            float[] m=transform(viewAxes,mobyAxes[p],mobyAxes[p+1],mobyAxes[p+2]);
            float ax=m[0], ay=d[2]*m[1]-d[1]*m[2], az=d[1]*m[1]+d[2]*m[2];
            e[p]=d[2]*ax-d[0]*az; e[p+1]=ay; e[p+2]=d[0]*ax+d[2]*az;
        }
        bank(e,9); return e;
    }

    /**
     * Separate NORMAL program: three direction xyz vectors, three color xyzw vectors (w=back-light K),
     * ambient RGB bytes, source vertex alpha and four original multiplier bytes. No defaults.
     * Bounds reject values outside the proven positive signed-halfword prepack domain.
     */
    public static int[] normal(float nx,float ny,float nz,float[] directions,float[] colors,
                               int[] ambient,int alpha,int[] multipliers) {
        bank(directions,9); bank(colors,12);
        bytes(ambient,3); bytes(multipliers,4);
        if (alpha<0 || alpha>128) throw new IllegalArgumentException("Invalid original vertex alpha");
        float norm=length(nx,ny,nz);
        float[] lit=new float[3];
        for (int k=0;k<3;k++) {
            int d=k*3,c=k*4;
            float dot=directions[d]*nx+directions[d+1]*ny+directions[d+2]*nz;
            float factor=Math.max(dot,-Math.abs(colors[c+3])*dot);
            for (int channel=0;channel<3;channel++) {
                if (colors[c+channel]<0) throw new IllegalArgumentException("Negative light RGB");
                lit[channel]+=colors[c+channel]*factor;
            }
        }
        int[] out=new int[4];
        for (int c=0;c<3;c++) {
            float scaled=ambient[c]+128f*(lit[c]/norm);
            if (!Float.isFinite(scaled) || scaled<0 || scaled>=32768)
                throw new IllegalArgumentException("Normal color outside bounded prepack domain");
            out[c]=Math.min(32767,((int)scaled)*multipliers[c])>>7;
        }
        out[3]=Math.min(32767,alpha*multipliers[3])>>7;
        return out;
    }

    /**
     * Per-vertex original view DEPTH, packed units; screen-linear interpolation afterwards.
     * Uses truncation as an explicit CPU approximation, not VU1's large-bias packing rounding.
     * Primary 13859 clamps to the source farValue floor and a literal255 ceiling, not nearValue.
     */
    public static int fogByte(float viewDepth,Rac1ShopSceneLightSource.Fog fog) {
        if (!Float.isFinite(viewDepth)) throw new IllegalArgumentException("Nonfinite view depth");
        float value=fog.nearValue()+(fog.farValue()-fog.nearValue())
                *((viewDepth-fog.near())/(fog.far()-fog.near()));
        return (int)Math.max(fog.farValue(),Math.min(255,value));
    }
    private static float[] transform(float[] a,float x,float y,float z) {
        return new float[]{a[0]*x+a[3]*y+a[6]*z,a[1]*x+a[4]*y+a[7]*z,a[2]*x+a[5]*y+a[8]*z};
    }
    private static float length(float x,float y,float z) {
        float length=(float)Math.sqrt((double)x*x+(double)y*y+(double)z*z);
        if (!Float.isFinite(length) || length<=0) throw new IllegalArgumentException("Degenerate original vector");
        return length;
    }
    private static void rotation(float[] axes) {
        bank(axes,9);
        for (int a=0;a<3;a++) for (int b=a;b<3;b++) {
            float dot=0;
            for (int c=0;c<3;c++) dot+=axes[a*3+c]*axes[b*3+c];
            if (Math.abs(dot-(a==b ? 1:0))>.002f)
                throw new IllegalArgumentException("Rotation must not include model/scene fit scale");
        }
    }
    private static void bank(float[] values,int count) {
        if (values==null || values.length!=count) throw new IllegalArgumentException("Invalid vector count");
        for (float value:values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite vector");
    }
    private static void bytes(int[] values,int count) {
        if (values==null || values.length!=count) throw new IllegalArgumentException("Invalid byte count");
        for (int value:values) if (value<0 || value>255) throw new IllegalArgumentException("Invalid byte");
    }
}
