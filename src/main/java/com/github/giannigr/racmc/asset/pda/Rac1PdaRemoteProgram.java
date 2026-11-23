package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.security.MessageDigest;
import java.util.HexFormat;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Fail-closed primary program identity for the PDA's real remote vendor path, not kiosk inference. */
public record Rac1PdaRemoteProgram(boolean pal,byte[] overlay) {
    public Rac1PdaRemoteProgram { overlay=overlay.clone(); }
    @Override public byte[] overlay() { return overlay.clone(); }
    public static Rac1PdaRemoteProgram read(Rac1MediaLease lease) throws Exception {
        lease.checkValid();
        var tables=Rac1DiscTables.read(lease);
        var header=Rac1LevelHeader.read(lease,tables.levelSector(1));
        long base=header.dataSector()*2048L;
        var container=lease.readBytes(base,0x58);
        int offset=word(container,0),size=word(container,4);
        if (offset<0 || size<=0 || size>4*1024*1024) throw new AssetFormatException("PDA remote overlay bounds");
        var overlay=lease.readBytes(base+offset,size);
        if (WadDecoder.isWad(overlay)) overlay=WadDecoder.decode(overlay,-1);
        boolean pal=lease.revision()==Rac1DiscRevision.PAL_V2_00;
        verify(overlay,pal); lease.checkValid();
        return new Rac1PdaRemoteProgram(pal,overlay);
    }
    public static void verify(byte[] overlay,boolean pal) throws Exception {
        // Independent retained resume9-scene-18 numeric hashes, followed by PDA-branch analysis.
        int[] addresses=pal ? new int[]{0x2af438,0x2b0a78,0x2b51d8,0x2b42e8,0x2b4bf8,0x2b48b8}
                :new int[]{0x2ae1a0,0x2af7e8,0x2b4020,0x2b3130,0x2b3a40,0x2b3700};
        int[] sizes={1216,3024,1104,1488,648,832};
        String[] hashes=pal ? new String[]{
                "92926b7b9df87b4d7bd099c4a6d81ad16a3f296cb417cf7649322d76a4defc71",
                "39c4f043ea186af3264f0a2ea653456556512a3ab7b9c5aac69125ce658f3057",
                "ed59a69814b2aa04db095f7c8ca2026c6cad8d1d0fb04c70cc3d9093bfdb1ad0",
                "8e8281d5eeef7bd8834ef8a3bb9c4bca198da5feb1e299680c3e430c045a73ce",
                "03ba45d1b69030d3f14431e09d6c245386c7e9e12049bef053d0b2c50a6dfecb",
                "d851e841ecaad149f6b5eb9cc2edae5330bb15760bd961af2290358a4c7c6115"}
                :new String[]{
                "e3f36d13bfa4fd72c60d7964d372846510356e85025a66615c8babccbb4f8e69",
                "52fc7e455a4c94d03325893aaf97a8a24835b635f70efc0c2385301317047c3e",
                "9b2865a2170c2aff3f8edc09c37ddf27072560ddb5d555f380f689f8a4297e18",
                "ad4d3e5fa454b2e7648036005120f7d10ef298ad76fa634094239e3e2862e4f8",
                "fa810c498ec37569f686832e2f2b3e06c733e6f12b139868acd7da1fdbbe68ee",
                "d9f2ecfb5cd1410b53d6962fe804542b7e536e650f24556dbde512c4b01b688f"};
        for (int i=0;i<addresses.length;i++) {
            var bytes=Rac1HudImporter.overlayRange(overlay,addresses[i],sizes[i]);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(hashes[i])) throw new AssetFormatException("Original PDA remote program differs at0x"+Integer.toHexString(addresses[i]));
        }
    }
}
