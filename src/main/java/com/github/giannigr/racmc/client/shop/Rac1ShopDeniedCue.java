package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.sound.*;
import java.io.IOException;

/** R17's exact level-1 class11/local2 binding, not a generic bank70 fallback. No sidecar/schema. */
final class Rac1ShopDeniedCue {
    private Rac1ShopDeniedCue() {}
    static Rac1AudioCatalog.Clip read(Rac1DiscImage image,Rac1LevelAssets level) throws Exception {
        var entry=level.coreIndex().mobyClass(11);
        if (entry==null) throw new IOException("Missing vendor sound class");
        var definition=Rac1SoundMap.resolve(level.coreIndex().bytes(),level.classBlob(entry.offset()),11,2);
        if (definition.cue()!=70) throw new IOException("Vendor denied sound binding differs from verified reference");
        var tables=Rac1DiscTables.read(image);
        var header=Rac1LevelHeader.read(image,tables.levelSector(1));
        var container=Rac1LevelData.read(image,header);
        var bank=new Rac1SoundBank(container.soundBank(image));
        var tone=bank.tone(definition.cue());
        var decoded=tone.decoded();
        if (tone.grain()!=304 || tone.toneFlags()!=0 || tone.centerNote()!=72 || tone.fine()!=0
                || decoded.repeat() || decoded.loopStart()!=-1 || decoded.frames()!=514)
            throw new IOException("Vendor denied one-shot metadata differs from verified reference");
        return new Rac1AudioCatalog.Clip(decoded.pcm(),tone.sampleRate(),definition.metadata(),
                tone.rawCue(),tone.rawGrain(),bank.digest(),tone.adpcmHash(),tone.cue(),
                decoded.loopStart(),decoded.loopEnd(),decoded.repeat());
    }
    static Rac1AudioCatalog.Clip optional(Rac1DiscImage image,Rac1LevelAssets level) {
        try { return read(image,level); }
        catch (Exception unavailable) {
            org.slf4j.LoggerFactory.getLogger(Rac1ShopDeniedCue.class)
                    .warn("Verified original denied cue unavailable; no substitute: {}",unavailable.toString());
            return null;
        }
    }
}
