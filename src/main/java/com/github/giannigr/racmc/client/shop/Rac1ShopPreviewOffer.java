package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import java.nio.*;

/** Read-only offer facts. No bundle, arrow cap, starting grant, debit or entitlement policy. */
public record Rac1ShopPreviewOffer(boolean ammo) {
    public int iconFrame() { return ammo ? 2:0; }
    public int displayedPrice(Facts facts) { return ammo ? facts.ammoUnitPrice():facts.weaponPrice(); }
    /** A local offer snapshot never authorizes spending; only a server-issued quote can do so. */
    public boolean purchaseEnabled() { return false; }
    public record Facts(int weaponPrice,int ammoUnitPrice,int pickup,int originalCap,int initialField) {
        public Facts {
            if (weaponPrice!=2500 || ammoUnitPrice!=1 || pickup!=25 || originalCap!=200 || initialField!=100)
                throw new IllegalArgumentException("Unsupported Blaster source facts");
        }
        public static Facts read(byte[] overlay,boolean pal) throws AssetFormatException {
            var b=ByteBuffer.wrap(Rac1HudImporter.overlayRange(overlay,(pal ? 0x1c47b0:0x1c4530)+15*24,24))
                    .order(ByteOrder.LITTLE_ENDIAN);
            try { return new Facts(b.getInt(0),b.getShort(8)&65535,b.getShort(12)&65535,
                    b.getShort(14)&65535,b.getShort(18)&65535); }
            catch (IllegalArgumentException bad) { throw new AssetFormatException("Blaster original price/ammo record mismatch"); }
        }
    }
    /** Primary factual guard only: no Minecraft ammo count or grant is computed here. */
    static void verifyOriginalAmmoPrograms(byte[] overlay,boolean pal) throws AssetFormatException {
        int give=pal ? 0x276750:0x275760,add=pal ? 0x249cd0:0x2494d8;
        // Existing ownership exits before ammo initialization; ammo-less records skip it.
        require(overlay,give+0x88,0x1440005f);
        require(overlay,give+0xac,0x94820008);
        require(overlay,give+0xb0,0x10400009);
        // initial = record.u16(+18); current = ammo[item]; store max(initial,current).
        require(overlay,give+0xc0,0x94830012);
        require(overlay,give+0xc8,0x8ca40000);
        require(overlay,give+0xcc,0x0064102a);
        require(overlay,give+0xd0,0x0082180b);
        require(overlay,give+0xd4,0xaca30000);
        // AddAmmo: total=current+requested; max from +14; positive overflow returns
        // total-max and stores max. Otherwise returns0 with the total already stored.
        require(overlay,add+0x20,0x8c820000);
        require(overlay,add+0x28,0x00451021);
        require(overlay,add+0x2c,0x94c3000e);
        require(overlay,add+0x34,0xac820000);
        require(overlay,add+0x38,0x94c6000e);
        require(overlay,add+0x3c,0x00461023);
        require(overlay,add+0x40,0x18400003);
        require(overlay,add+0x4c,0xac860000);
        require(overlay,add+0x54,0x0000102d);
    }
    private static void require(byte[] overlay,int pc,int expected) throws AssetFormatException {
        if (com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word(Rac1HudImporter.overlayRange(overlay,pc,4),0)!=expected)
            throw new AssetFormatException("Original ammo application code mismatch at "+Integer.toHexString(pc));
    }
}
