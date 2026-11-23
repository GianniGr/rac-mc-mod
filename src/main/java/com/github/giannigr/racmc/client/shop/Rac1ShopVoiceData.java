package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.hud.Rac1HudImporter;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.util.List;

/** Optional screen-local memory catalog. Only previously approved ranges; no cache/export. */
public record Rac1ShopVoiceData(List<Rac1ShopVoiceSource.Clip> clips,Rac1ShopVoiceTiming timing,String issue) {
    public Rac1ShopVoiceData { clips=List.copyOf(clips); }
    public boolean ready() { return timing!=null && clips.size()==6 && issue==null; }
    public static Rac1ShopVoiceData read(Rac1DiscImage image,byte[] toc,byte[] overlay,boolean pal) {
        try {
            var timing=Rac1ShopVoiceTiming.read(
                    Rac1HudImporter.overlayRange(overlay,pal ? 0x20a820:0x20a530,52),
                    Rac1HudImporter.overlayRange(overlay,pal ? 0x161190:0x161110,24),pal);
            var clips=Rac1ShopVoiceSource.read(image,toc,
                    Rac1HudImporter.overlayRange(overlay,pal ? 0x27a978:0x279c08,208));
            return new Rac1ShopVoiceData(clips,timing,null);
        } catch (IOException | AssetFormatException failure) {
            return new Rac1ShopVoiceData(List.of(),null,"Verified English dialogue unavailable: "+failure.getMessage());
        }
    }
}
