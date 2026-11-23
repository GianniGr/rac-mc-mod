package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopPreviewOfferTest {
    @Test void currentMembershipSelectsOriginalFramesAndPricesButNeverEnablesPurchase() {
        var facts=new Rac1ShopPreviewOffer.Facts(2500,1,25,200,100);
        var absent=new Rac1ShopPreviewOffer(false); var present=new Rac1ShopPreviewOffer(true);
        assertEquals(0,absent.iconFrame()); assertEquals(2500,absent.displayedPrice(facts));
        assertEquals(2,present.iconFrame()); assertEquals(1,present.displayedPrice(facts));
        assertFalse(absent.purchaseEnabled()); assertFalse(present.purchaseEnabled());
        // Independent immutable viewer snapshots; presence is not an entitlement.
        assertEquals(new Rac1ShopPreviewOffer(false),absent);
        assertNotEquals(absent,present);
        assertEquals(0,new Rac1ShopPreviewOffer(false).iconFrame());
    }
    @Test void bothPrimaryRegionalRecordLocationsReadSeparatePickupCapAndInitialFields() throws Exception {
        for (boolean pal:new boolean[]{false,true}) {
            assertEquals(new Rac1ShopPreviewOffer.Facts(2500,1,25,200,100),
                    Rac1ShopPreviewOffer.Facts.read(overlay(pal),pal));
        }
    }
    @Test void missingWrongRevisionOrAlteredFactsFailClosed() {
        assertThrows(AssetFormatException.class,() -> Rac1ShopPreviewOffer.Facts.read(new byte[0],false));
        assertThrows(AssetFormatException.class,() -> Rac1ShopPreviewOffer.Facts.read(overlay(false),true));
        byte[] changed=overlay(true); changed[16+15*24]=0;
        assertThrows(AssetFormatException.class,() -> Rac1ShopPreviewOffer.Facts.read(changed,true));
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopPreviewOffer.Facts(2500,25,25,200,100));
    }
    private static byte[] overlay(boolean pal) {
        byte[] bytes=new byte[16+43*24];
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,pal ? 0x1c47b0:0x1c4530); b.putInt(4,43*24);
        int at=16+15*24;
        b.putInt(at,2500); b.putShort(at+8,(short)1); b.putShort(at+12,(short)25);
        b.putShort(at+14,(short)200); b.putShort(at+18,(short)100);
        return bytes;
    }
}
