package com.github.giannigr.racmc.asset.pda;

import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.sound.*;
import java.io.IOException;
import java.util.*;

/** Exact class11 remote-path bindings from the selected supported image. No speech or substitute. */
public record Rac1PdaRemoteAudio(Map<Role,Rac1AudioCatalog.Clip> clips) {
    public enum Role {
        SELECT(0),CURSOR(1),DENIED(2),OPEN(3),SCREENS_ON(4),BACK(5),CLOSED(6),PURCHASE(7);
        private final int slot;
        Role(int slot) { this.slot=slot; }
        public int slot() { return slot; }
    }
    public Rac1PdaRemoteAudio { clips=Map.copyOf(clips); }
    public static Rac1PdaRemoteAudio read(Rac1MediaLease lease,Rac1PdaRemoteProgram program) throws Exception {
        lease.checkValid();
        if (program.pal()!=(lease.revision()==com.github.giannigr.racmc.iso.Rac1DiscRevision.PAL_V2_00))
            throw new IOException("PDA program/audio region differs");
        var tables=Rac1DiscTables.read(lease);
        var header=Rac1LevelHeader.read(lease,tables.levelSector(1));
        var data=Rac1LevelData.read(lease,header);
        byte[] index=lease.readBytes(data.base()+data.coreIndexOffset(),data.coreIndexSize());
        var level=Rac1LevelAssets.loadCore(lease,1,data,index);
        var entry=level.coreIndex().mobyClass(11);
        if (entry==null || entry.offset()<=0) throw new IOException("PDA remote sound class11 missing");
        var blob=level.classBlob(entry.offset());
        var bank=new Rac1SoundBank(data.soundBank(lease));
        int[] frames={107,61,514,616,504,589,345,660};
        var clips=new EnumMap<Role,Rac1AudioCatalog.Clip>(Role.class);
        for (var role:Role.values()) {
            var definition=Rac1SoundMap.resolve(index,blob,11,role.slot);
            if (definition.cue()!=68+role.slot) throw new IOException("PDA remote class remap changed");
            var tone=bank.tone(definition.cue()); var decoded=tone.decoded();
            if (tone.grain()!=302+role.slot || tone.toneFlags()!=0 || decoded.repeat()
                    || decoded.loopStart()!=-1 || decoded.frames()!=frames[role.slot]
                    || tone.centerNote()!=(role==Role.CLOSED ? 77:72)
                    || tone.fine()!=(role==Role.CLOSED ? 6:0))
                throw new IOException("PDA remote one-shot metadata changed");
            clips.put(role,new Rac1AudioCatalog.Clip(decoded.pcm(),tone.sampleRate(),definition.metadata(),
                    tone.rawCue(),tone.rawGrain(),bank.digest(),tone.adpcmHash(),tone.cue(),
                    decoded.loopStart(),decoded.loopEnd(),decoded.repeat()));
        }
        lease.checkValid(); return new Rac1PdaRemoteAudio(clips);
    }
}
