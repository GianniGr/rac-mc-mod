package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Resume16 bounded BOTH-DISC primary call-site guards, not blanket regional ID offsets. */
public record Rac1ShopPresentationSource(float[] presenterPosition,float[] panelBase) {
    public Rac1ShopPresentationSource { presenterPosition=presenterPosition.clone(); panelBase=panelBase.clone(); }
    @Override public float[] presenterPosition() { return presenterPosition.clone(); }
    @Override public float[] panelBase() { return panelBase.clone(); }
    public static Rac1ShopPresentationSource read(byte[] overlay,boolean pal) throws AssetFormatException {
        int ticker=pal ? 0x2b2c60:0x2b1a48,led=pal ? 0x2b2a50:0x2b1838;
        require(overlay,ticker+0x48,0x24030014); // lifetime compares strlen against scroll/20
        require(overlay,ticker+0x64,0x24820002); // +2 scroll
        require(overlay,led+0xd8,0x2404001e);   // ScaleTicks30
        require(overlay,led+0xec,0x2404000a);   // +ScaleTicks10 period
        int item=pal ? 0x2b0680:0x2af3f0;
        require(overlay,item+0x60,0x8c530010);  // item definition's weapon class, not ammo class
        require(overlay,item+0xe8,0x1440000f);  // entry-kind test gates weapon-only extra Z
        int delta=pal ? 0x80:0;
        return new Rac1ShopPresentationSource(vector(overlay,0x161080+delta),vector(overlay,0x161090+delta));
    }
    private static float[] vector(byte[] overlay,int pc) throws AssetFormatException {
        byte[] bytes=Rac1HudImporter.overlayRange(overlay,pc,16); float[] values=new float[3];
        for (int i=0;i<3;i++) {
            values[i]=Float.intBitsToFloat(word(bytes,i*4));
            if (!Float.isFinite(values[i]) || Math.abs(values[i])>20) throw new AssetFormatException("Invalid actor placement");
        }
        return values;
    }
    private static void require(byte[] overlay,int pc,int value) throws AssetFormatException {
        if (word(Rac1HudImporter.overlayRange(overlay,pc,4),0)!=value)
            throw new AssetFormatException("Original presentation instruction mismatch at "+Integer.toHexString(pc));
    }
}
