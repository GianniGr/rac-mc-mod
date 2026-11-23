package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import java.util.Set;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/**
 * Three prototype text roles bound to primary regional call-site literals, NOT a global ID shift.
 * Does not change the existing shop cache/text completeness contract or install a caller.
 */
public record Rac1ShopPrototypeTextIds(int buy,int exit,int denied) {
    public static final int GS_TEXT_COLOR=0x80f0f0f0;
    public Rac1ShopPrototypeTextIds {
        if (exit!=20192 || !((buy==21044 && denied==21048)||(buy==21049 && denied==21053)))
            throw new IllegalArgumentException("Unsupported original prototype text roles");
    }
    public Set<Integer> required() { return Set.of(buy,exit,denied); }
    public static Rac1ShopPrototypeTextIds read(byte[] novalisOverlay,boolean pal) throws AssetFormatException {
        int buy=literal(novalisOverlay,pal ? 0x2b37a4:0x2b2590,3,pal ? 21049:21044);
        int exit=literal(novalisOverlay,pal ? 0x2b399c:0x2b2814,4,20192);
        int denied=literal(novalisOverlay,pal ? 0x2b3b80:0x2b29c8,4,pal ? 21053:21048);
        tint(novalisOverlay,pal ? 0x2b3830:0x2b2614,5);
        tint(novalisOverlay,pal ? 0x2b39dc:0x2b2824,6);
        tint(novalisOverlay,pal ? 0x2b3e58:0x2b2ca0,5);
        return new Rac1ShopPrototypeTextIds(buy,exit,denied);
    }
    private static int literal(byte[] overlay,int pc,int register,int expected) throws AssetFormatException {
        int instruction=word(Rac1HudImporter.overlayRange(overlay,pc,4),0);
        if ((instruction&0xffff0000)!=(0x24000000|register<<16) || (instruction&65535)!=expected)
            throw new AssetFormatException("Original prototype text call-site mismatch");
        return instruction&65535;
    }
    private static void tint(byte[] overlay,int pc,int r) throws AssetFormatException {
        byte[] instructions=Rac1HudImporter.overlayRange(overlay,pc,12);
        if (word(instructions,0)!=(0x340080f0|r<<16)
                || word(instructions,4)!=(r<<16|r<<11|16<<6|0x38)
                || word(instructions,8)!=(0x3400f0f0|r<<21|r<<16))
            throw new AssetFormatException("Original prototype text tint call-site mismatch");
    }
}
