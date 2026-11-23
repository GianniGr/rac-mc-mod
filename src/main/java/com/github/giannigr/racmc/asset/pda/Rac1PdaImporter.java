package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.word;

/** Bounded PDA-only selection from the existing validated image capability. No persistent asset dump. */
public final class Rac1PdaImporter {
    private Rac1PdaImporter() {}
    public static Rac1PdaAssets read(Rac1MediaLease lease) throws Exception {
        lease.checkValid();
        var toc=Rac1DiscTables.read(lease).bytes();
        var header=sectors(lease,toc,0x14f8,64*1024);
        var banks=new byte[5][];
        for (int i=0;i<5;i++) {
            int expected=word(header,0x54+i*4);
            if (expected<0 || expected>16*1024*1024) throw new AssetFormatException("PDA HUD bank too large");
            banks[i]=WadDecoder.decode(sectors(lease,toc,0x1500+i*8,4*1024*1024),expected);
        }
        var icons=new Rac1HudBankDecoder(header,banks).decode(Rac1PdaAssets.ICON);
        var level=Rac1LevelAssets.load(lease,1);
        // A gadget table lookup for619, never class11/vendor geometry or a hero fallback.
        if (level.coreIndex().gadget(Rac1PdaAssets.CLASS)==null)
            throw new AssetFormatException("Original PDA gadget619 is not present in Novalis");
        var metadata=MobyClassReader.of(level.gadgetClass(Rac1PdaAssets.CLASS)).readMaterialMetadata();
        if (metadata.packets().stream().anyMatch(MobyClassReader.PacketMetadata::metal))
            throw new AssetFormatException("PDA619 has metal geometry not supported by this item renderer; refusing partial mesh");
        var handheld=Rac1AssetExtractor.readGadgetAsset(level,Rac1PdaAssets.CLASS);
        var result=new Rac1PdaAssets(icons,handheld);
        lease.checkValid();
        return result;
    }
    private static byte[] sectors(Rac1MediaLease lease,byte[] toc,int at,int limit) throws Exception {
        int sector=word(toc,at),count=word(toc,at+4);
        if (sector<=0 || count<=0 || count>limit/2048) throw new AssetFormatException("Invalid PDA source range");
        return lease.readSectors(sector,count);
    }
}
