package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import java.util.Objects;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/**
 * Immutable original Novalis light/fog inputs. In memory only, not a runtime scene,
 * Minecraft light adapter, point-light snapshot or persistence extension.
 */
public record Rac1ShopSceneLightSource(String discSha1, float[] menuDirection, float[] menuColor,
                                       int vendorInstance, int[] vendorAmbient, int vendorLightWord,
                                       float[] vendorLightRecord, Fog worldFog) {
    public record Fog(int red, int green, int blue, float near, float far, float nearValue, float farValue) {
        public Fog {
            if ((red|green|blue)<0 || red>255 || green>255 || blue>255
                    || !Float.isFinite(near) || !Float.isFinite(far) || near<0 || far<=near
                    || !Float.isFinite(nearValue) || !Float.isFinite(farValue)
                    || farValue<0 || nearValue>255 || farValue>=nearValue)
                throw new IllegalArgumentException("Invalid original fog inputs");
        }
    }
    public Rac1ShopSceneLightSource {
        if (discSha1==null || !discSha1.matches("[0-9a-f]{40}") || vendorInstance!=3 || vendorLightWord!=0
                || vendorAmbient.length!=3) throw new IllegalArgumentException("Unsupported source vendor identity");
        finite(menuDirection,4); finite(menuColor,4); finite(vendorLightRecord,16);
        for (int v:vendorAmbient) if (v<0 || v>255) throw new IllegalArgumentException("Invalid ambient byte");
        menuDirection=menuDirection.clone(); menuColor=menuColor.clone();
        vendorAmbient=vendorAmbient.clone(); vendorLightRecord=vendorLightRecord.clone();
        Objects.requireNonNull(worldFog);
    }
    @Override public float[] menuDirection() { return menuDirection.clone(); }
    @Override public float[] menuColor() { return menuColor.clone(); }
    @Override public int[] vendorAmbient() { return vendorAmbient.clone(); }
    @Override public float[] vendorLightRecord() { return vendorLightRecord.clone(); }

    /** Caller supplies the validated selected-disc identity and explicit decompressed gameplay variant. */
    public static Rac1ShopSceneLightSource read(byte[] overlay, boolean pal, byte[] gameplay, String sha1)
            throws AssetFormatException {
        if (gameplay.length<0x94 || gameplay.length>8*1024*1024)
            throw new AssetFormatException("Invalid shop gameplay extent");
        int settings=section(gameplay,0,0x50), lights=section(gameplay,4,16);
        int actors=section(gameplay,0x44,16), count=word(gameplay,actors), lightCount=word(gameplay,lights);
        if (count<1 || count>16384 || lightCount<1 || lightCount>16)
            throw new AssetFormatException("Invalid source scene counts");
        range(gameplay,actors+16,count*0x78); range(gameplay,lights+16,lightCount*64);
        int found=-1;
        for (int i=0;i<count;i++) {
            int at=actors+16+i*0x78;
            if (word(gameplay,at)!=0x78) throw new AssetFormatException("Unsupported source actor stride");
            if (word(gameplay,at+0x18)==11) {
                if (found!=-1) throw new AssetFormatException("Ambiguous source vendor");
                found=i;
            }
        }
        if (found<0) throw new AssetFormatException("Missing source vendor");
        int actor=actors+16+found*0x78, light=word(gameplay,actor+0x70);
        int[] ambient={word(gameplay,actor+0x64),word(gameplay,actor+0x68),word(gameplay,actor+0x6c)};
        int delta=pal ? 0x80 : 0;
        float[] direction=floats(Rac1HudImporter.overlayRange(overlay,0x1610d0+delta,16),0,4);
        float[] color=floats(Rac1HudImporter.overlayRange(overlay,0x1610e0+delta,16),0,4);
        try {
            return new Rac1ShopSceneLightSource(sha1,direction,color,found,ambient,light,
                    floats(gameplay,lights+16,16),new Fog(word(gameplay,settings+12),
                    word(gameplay,settings+16),word(gameplay,settings+20),
                    f(gameplay,settings+24),f(gameplay,settings+28),
                    f(gameplay,settings+32),f(gameplay,settings+36)));
        } catch (IllegalArgumentException failure) {
            throw new AssetFormatException("Unsupported original shop light/fog source",failure);
        }
    }
    private static int section(byte[] bytes,int field,int size) throws AssetFormatException {
        int at=word(bytes,field);
        if (at<0x94 || (at&15)!=0) throw new AssetFormatException("Invalid shop gameplay section");
        range(bytes,at,size); return at;
    }
    private static float f(byte[] b,int at) throws AssetFormatException { return Float.intBitsToFloat(word(b,at)); }
    private static float[] floats(byte[] b,int at,int count) throws AssetFormatException {
        range(b,at,count*4); float[] out=new float[count];
        for (int i=0;i<count;i++) out[i]=f(b,at+i*4);
        return out;
    }
    private static void finite(float[] bank,int size) {
        if (bank.length!=size) throw new IllegalArgumentException("Invalid source vector count");
        for (float value:bank) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite source vector");
    }
}
