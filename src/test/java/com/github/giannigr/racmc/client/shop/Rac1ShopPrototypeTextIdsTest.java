package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopPrototypeTextIdsTest {
    @Test void primaryRegionalRolesAreNotAssumedToBeStableNumericIds() throws Exception {
        assertEquals(new Rac1ShopPrototypeTextIds(21044,20192,21048),Rac1ShopPrototypeTextIds.read(overlay(false),false));
        assertEquals(new Rac1ShopPrototypeTextIds(21049,20192,21053),Rac1ShopPrototypeTextIds.read(overlay(true),true));
        assertEquals(3,Rac1ShopPrototypeTextIds.read(overlay(true),true).required().size());
    }
    @Test void wrongOpcodeLiteralRevisionAndMissingSourceFailClosed() {
        byte[] bad=overlay(true);
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(16+0x7a4,0x24035234);
        assertThrows(AssetFormatException.class,() -> Rac1ShopPrototypeTextIds.read(bad,true));
        assertThrows(AssetFormatException.class,() -> Rac1ShopPrototypeTextIds.read(overlay(false),true));
        assertThrows(AssetFormatException.class,() -> Rac1ShopPrototypeTextIds.read(new byte[0],false));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopPrototypeTextIds(21044,20192,21053));
    }
    private static byte[] overlay(boolean pal) {
        int base=pal ? 0x2b3000:0x2b2000;
        byte[] out=new byte[16+0x1000]; var b=ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,base); b.putInt(4,0x1000);
        b.putInt(16+(pal ? 0x2b37a4:0x2b2590)-base,0x24030000|(pal ? 21049:21044));
        b.putInt(16+(pal ? 0x2b399c:0x2b2814)-base,0x24040000|20192);
        b.putInt(16+(pal ? 0x2b3b80:0x2b29c8)-base,0x24040000|(pal ? 21053:21048));
        int[] pcs=pal ? new int[]{0x2b3830,0x2b39dc,0x2b3e58}:new int[]{0x2b2614,0x2b2824,0x2b2ca0};
        for (int i=0;i<3;i++) {
            int r=i==1 ? 6:5,at=16+pcs[i]-base;
            b.putInt(at,0x340080f0|r<<16); b.putInt(at+4,r<<16|r<<11|16<<6|0x38);
            b.putInt(at+8,0x3400f0f0|r<<21|r<<16);
        }
        return out;
    }
}
