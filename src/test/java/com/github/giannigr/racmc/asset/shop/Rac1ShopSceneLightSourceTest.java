package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopSceneLightSourceTest {
    private static final String SHA="0123456789012345678901234567890123456789";
    @Test void readsExplicitSourceVariantAndOwnsArrays() throws Exception {
        byte[] gameplay=gameplay();
        var source=Rac1ShopSceneLightSource.read(overlay(false),false,gameplay,SHA);
        var pal=Rac1ShopSceneLightSource.read(overlay(true),true,gameplay,SHA);
        assertArrayEquals(source.menuColor(),pal.menuColor());
        assertEquals(-.25f,source.menuColor()[3]);
        assertEquals(3,source.vendorInstance()); assertEquals(0,source.vendorLightWord());
        assertArrayEquals(new int[]{41,41,41},source.vendorAmbient());
        assertEquals(245760,source.worldFog().far());
        source.menuColor()[0]=0; source.vendorAmbient()[0]=0; source.vendorLightRecord()[0]=0;
        gameplay[0x110]=0;
        assertEquals(.9f,source.menuColor()[0]); assertEquals(41,source.vendorAmbient()[0]);
        assertEquals(.5f,source.vendorLightRecord()[0]);
    }
    @Test void rejectsMissingDuplicateUnsupportedAndUnboundedSourceData() {
        assertThrows(AssetFormatException.class,() -> read(new byte[0]));
        assertThrows(AssetFormatException.class,() -> read(new byte[8*1024*1024+1]));
        reject(4,-16); reject(0x100,17); reject(0x150,16385);
        reject(0x160,0x70); reject(0x160+3*0x78+0x18,12);
        reject(0x160+0x18,11); reject(0x160+3*0x78+0x70,1);
        reject(0x160+3*0x78+0x64,256);
        reject(0xb0+28,Float.floatToRawIntBits(0));
        assertThrows(AssetFormatException.class,
                () -> Rac1ShopSceneLightSource.read(new byte[0],false,gameplay(),SHA));
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1ShopSceneLightSource.Fog(0,0,0,0,0,255,0));
    }
    private static void reject(int at,int value) {
        byte[] data=gameplay(); b(data).putInt(at,value);
        assertThrows(AssetFormatException.class,() -> read(data));
    }
    private static Rac1ShopSceneLightSource read(byte[] gameplay) throws Exception {
        return Rac1ShopSceneLightSource.read(overlay(false),false,gameplay,SHA);
    }
    private static byte[] gameplay() {
        byte[] bytes=new byte[0x340]; var b=b(bytes);
        b.putInt(0,0xb0); b.putInt(4,0x100); b.putInt(0x44,0x150);
        b.putInt(0xb0+12,105); b.putInt(0xb0+16,127); b.putInt(0xb0+20,180);
        b.putFloat(0xb0+28,245760); b.putFloat(0xb0+32,255); b.putFloat(0xb0+36,102);
        b.putInt(0x100,1); b.putFloat(0x110,.5f);
        b.putInt(0x150,4);
        for (int i=0;i<4;i++) b.putInt(0x160+i*0x78,0x78);
        int actor=0x160+3*0x78;
        b.putInt(actor+0x18,11);
        for (int i=0;i<3;i++) b.putInt(actor+0x64+i*4,41);
        return bytes;
    }
    private static byte[] overlay(boolean pal) {
        byte[] out=new byte[0x110]; var b=b(out);
        int base=0x1610d0+(pal ? 0x80:0);
        b.putInt(0,base); b.putInt(4,out.length-16);
        float[] values={-.42f,-.7f,-.577f,0,.9f,.9f,.6f,-.25f};
        for (int i=0;i<8;i++) b.putFloat(16+i*4,values[i]);
        return out;
    }
    private static ByteBuffer b(byte[] data) { return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); }
}
