package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import java.util.Set;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Primary regional popup roles and bone inset, not a global ID offset or fitted popup rectangle. */
public record Rac1ShopUiSource(int maxed,int howMany,int purchase,int yes,int no,int back) {
    public Set<Integer> required() { return Set.of(maxed,howMany,purchase,yes,no,back); }
    public static Rac1ShopUiSource read(byte[] overlay,boolean pal) throws AssetFormatException {
        int[] pcs=pal ? new int[]{0x2b3b08,0x2b3bac,0x2b3dcc,0x2b3df4,0x2b3e1c}
                :new int[]{0x2b2950,0x2b29f4,0x2b2c14,0x2b2c3c,0x2b2c64};
        int[] ids=pal ? new int[]{21051,21052,21054,21049,21072}:new int[]{21046,21047,21049,21070,21067};
        for (int i=0;i<5;i++) require(overlay,pcs[i],0x24040000|ids[i]);
        int back=pal ? 21048:21043;
        require(overlay,pal ? 0x2b3730:0x2b2518,0x24000000|((pal ? 16:6)<<16)|back);
        int bones=pal ? 0x1611f8:0x161178;
        for (int i=0;i<3;i++) require(overlay,bones+i*4,i);
        // DrawPopup's literal .04, .03, .06 inset values, independently observed both discs.
        int draw=pal ? 0x2b4bf8:0x2b3a40;
        require(overlay,draw+0x6c,0x3c013d23); require(overlay,draw+0x70,0x3421d70a);
        require(overlay,draw+0xa0,0x3c013cf5); require(overlay,draw+0xa4,0x3421c28f);
        require(overlay,draw+0xcc,0x3c013d75); require(overlay,draw+0xd0,0x3421c28f);
        return new Rac1ShopUiSource(ids[0],ids[1],ids[2],ids[3],ids[4],back);
    }
    private static void require(byte[] overlay,int pc,int expected) throws AssetFormatException {
        if (word(Rac1HudImporter.overlayRange(overlay,pc,4),0)!=expected)
            throw new AssetFormatException("Original popup role/layout source mismatch at "+Integer.toHexString(pc));
    }
}
