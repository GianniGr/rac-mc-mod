package com.github.giannigr.racmc.asset;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Hand-built source bytes only; also compiled with the PRIOR reader by a private JDK runner. */
final class MobyMaterialFixture {
    static final int TABLE = 0x48, LIST = 0x200, HEADER = 0x600;

    static byte[] blob() {
        byte[] data = new byte[0x1000];
        var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,TABLE); data[4]=2; data[5]=1; data[6]=2; data[7]=3; data[8]=1;
        b.putFloat(0x24,1); b.putInt(0x40,0x80969696); b.putShort(0x44,(short)0xabcd); data[0x47]=(byte)255;
        for (int p : new int[]{0,1,3,4}) {
            boolean metal = p >= 3;
            int entry = TABLE+p*16, list = LIST+p*0x80, header = HEADER+p*0x100;
            b.putInt(entry,list); b.putShort(entry+4,(short)(metal ? 6 : 7));
            b.putInt(entry+8,header); data[entry+12]=(byte)(metal ? 4 : 7); data[entry+15]=3;
            int at = list;
            if (!metal) {
                b.putInt(at,0x65030000);
                for (int v=0;v<3;v++) { b.putShort(at+4+v*4,(short)(v*100)); b.putShort(at+6+v*4,(short)(v*200)); }
                at+=16;
            }
            b.putInt(at,0x6e030000);
            for (int v=0;v<6;v++) data[at+8+v]=(byte)(v<3 ? v+1 : 3);
            at+=16;
            b.putInt(at,0x6c040000);
            b.putLong(at+4,0xfedcba9876543210L+p); b.putLong(at+20,0x8877665544332211L+p);
            b.putInt(at+36,metal ? (p==3 ? -2 : -3) : (p==0 ? -1 : 3));
            if (metal) {
                b.putInt(header,3);
            } else {
                b.putInt(header+12,3); b.putInt(header+20,3);
                b.putInt(header+24,32); b.putInt(header+28,96);
                for (int v=0;v<3;v++) {
                    int record = header+32+v*16;
                    data[record+9]=64;
                    b.putShort(record+10,(short)(v*100)); b.putShort(record+12,(short)(v*200));
                    b.putShort(record+14,(short)(p*1024));
                }
                for (int v=0;v<12;v++) data[header+96+v]=(byte)(p*32+v);
            }
        }
        // The low-LOD packet has deliberately absent payload; the additive API must not decode it.
        return data;
    }

    static String digest(MobyClassReader.Mesh mesh) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256");
        for (float[] values : new float[][]{mesh.positions(),mesh.normals(),mesh.uvs(),mesh.weights()}) {
            var bytes = ByteBuffer.allocate(4+values.length*4).order(ByteOrder.LITTLE_ENDIAN);
            bytes.putInt(values.length); for (float v : values) bytes.putInt(Float.floatToRawIntBits(v));
            hash.update(bytes.array());
        }
        for (int[] values : new int[][]{mesh.indices(),mesh.materials(),mesh.joints()}) {
            var bytes = ByteBuffer.allocate(4+values.length*4).order(ByteOrder.LITTLE_ENDIAN);
            bytes.putInt(values.length); for (int v : values) bytes.putInt(v);
            hash.update(bytes.array());
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    public static void main(String[] args) throws Exception {
        System.out.println(digest(MobyClassReader.of(blob()).readMesh()));
    }
}
