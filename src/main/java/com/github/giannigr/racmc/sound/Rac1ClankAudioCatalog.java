package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.clank.Rac1ClankAv.Loop;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.WadDecoder;
import java.io.IOException;
import java.util.*;

/** Optional, volatile capability. Never added to the shared event catalog or its persistent cache. */
public record Rac1ClankAudioCatalog(Map<Loop,Rac1AudioCatalog.Clip> clips,Map<Loop,String> failures,
                                   Rac1ClankHighEntryAudio.Catalog highEntries,Rac1HydroAudio hydro,String hydroFailure,
                                   Map<Integer,Rac1ClankActionProgram> actions) {
    public Rac1ClankAudioCatalog(Map<Loop,Rac1AudioCatalog.Clip> clips,Map<Loop,String> failures,
                                Rac1ClankHighEntryAudio.Catalog highEntries,Rac1HydroAudio hydro,String hydroFailure) {
        this(clips,failures,highEntries,hydro,hydroFailure,Map.of());
    }
    public Rac1ClankAudioCatalog(Map<Loop,Rac1AudioCatalog.Clip> clips,Map<Loop,String> failures,
                                Rac1ClankHighEntryAudio.Catalog highEntries) {
        this(clips,failures,highEntries,null,"Not imported");
    }
    private static final List<Loop> SUPPORTED=List.of(Loop.HELI,Loop.THRUSTER);
    public Rac1ClankAudioCatalog(Map<Loop,Rac1AudioCatalog.Clip> clips,Map<Loop,String> failures) {
        this(clips,failures,Rac1ClankHighEntryAudio.Catalog.EMPTY,null,"Not imported");
    }
    public Rac1ClankAudioCatalog {
        clips=Map.copyOf(clips);failures=Map.copyOf(failures);
        Objects.requireNonNull(highEntries);
        actions=Map.copyOf(actions);
        if(!SUPPORTED.containsAll(clips.keySet())||!SUPPORTED.containsAll(failures.keySet()))
            throw new IllegalArgumentException("Unsupported Clank audio binding");
    }
    @FunctionalInterface public interface Reader {
        Rac1AudioCatalog.Clip read(Loop loop) throws Exception;
    }
    /** A corrupt optional cue does not discard another cue, much less the shared catalog. */
    public static Rac1ClankAudioCatalog read(Reader reader) {
        var clips=new EnumMap<Loop,Rac1AudioCatalog.Clip>(Loop.class);
        var failures=new EnumMap<Loop,String>(Loop.class);
        for(var loop:SUPPORTED) {
            try { clips.put(loop,Objects.requireNonNull(reader.read(loop))); }
            catch(Exception failure) { failures.put(loop,failure.toString()); }
        }
        return new Rac1ClankAudioCatalog(clips,failures);
    }
    /** Runtime-only import from the user's verified image; never called by synthetic tests. */
    public static Rac1ClankAudioCatalog extract(Rac1DiscImage image,Rac1DiscRevision revision) throws Exception {
        var tables=Rac1DiscTables.read(image);
        var header=Rac1LevelHeader.read(image,tables.levelSector(1));
        var data=Rac1LevelData.read(image,header);
        byte[] bankBytes=data.soundBank(image);
        var bank=new Rac1SoundBank(bankBytes);
        String hash=bank.digest();
        String expected=switch(revision) {
            case NTSC_U_V1_00 -> "50e2f3ad91d16288ed2d4bd1da432e2824705c8b35d589e7a65ccf8da685f366";
            case PAL_V2_00 -> "2330fb88665d0ba255cf7359f7208be0cd433a86926a90320b90b98c671bc8dd";
        };
        if(!expected.equals(hash))throw new IOException("Unverified Clank reference bank");
        byte[] index=image.readBytes(data.base()+data.coreIndexOffset(),data.coreIndexSize());
        var coreIndex=Rac1CoreIndex.parse(index);
        var hero=coreIndex.mobyClass(0);
        if(hero==null)throw new IOException("Missing hero audio definitions");
        // No GS RAM, texture, other class or overlay reads. The core is one compressed stream.
        byte[] core=WadDecoder.decode(image.readBytes(data.base()+data.coreDataOffset(),data.coreDataSize()),
                coreIndex.decompressedSize());
        int offset=hero.offset(),size=coreIndex.blockSize(offset);
        if(offset<=0||size<=0||offset>core.length-size)throw new IOException("Invalid hero audio blob");
        byte[] blob=Arrays.copyOfRange(core,offset,offset+size);
        var loops=read(loop->{
            // local() is the definition entry, NOT the source handler's ownership slot3/4.
            var definition=Rac1SoundMap.resolve(index,blob,0,loop.local());
            return verifiedClip(loop,definition,bank.tone(definition.cue()),hash);
        });
        var entries=new EnumMap<Rac1ClankHighEntryAudio.Cue,Rac1AudioCatalog.Clip>(Rac1ClankHighEntryAudio.Cue.class);
        var errors=new EnumMap<Rac1ClankHighEntryAudio.Cue,String>(Rac1ClankHighEntryAudio.Cue.class);
        for(var cue:Rac1ClankHighEntryAudio.Cue.values())try {
            var definition=Rac1SoundMap.resolve(index,blob,0,cue.local);
            entries.put(cue,Rac1ClankHighEntryAudio.verified(cue,definition,bank.tone(definition.cue()),hash));
        }catch(Exception unavailable){errors.put(cue,unavailable.toString());}
        Rac1HydroAudio hydro=null;String hydroFailure=null;
        try {hydro=Rac1HydroAudio.read(bankBytes,Rac1SoundMap.resolve(index,blob,0,19),revision);}
        catch(Exception unavailable){hydroFailure=unavailable.toString();}
        var actions=new HashMap<Integer,Rac1ClankActionProgram>();
        var thruster=entries.get(Rac1ClankHighEntryAudio.Cue.THRUSTER);
        if(thruster!=null)actions.put(1,new Rac1ClankActionProgram(1,List.of(thruster)));
        // Precision-limited runtime capability: only these retained programs, no new research read.
        if(revision==Rac1DiscRevision.NTSC_U_V1_00)for(int local:new int[]{43,46,62})try {
            actions.put(local,Rac1ClankActionProgram.read(local,Rac1SoundMap.resolve(index,blob,0,local),bankBytes));
        }catch(Exception unavailable) {
            com.github.giannigr.racmc.RacMcMod.LOGGER.debug("Optional reached action {} unavailable: {}",local,unavailable.toString());
        }
        return new Rac1ClankAudioCatalog(loops.clips(),loops.failures(),
                new Rac1ClankHighEntryAudio.Catalog(entries,errors),hydro,hydroFailure,actions);
    }
    /** Checks retained per-cue evidence, including the ADPCM identity and original loop extents. */
    static Rac1AudioCatalog.Clip verifiedClip(Loop loop,Rac1SoundMap.Definition definition,
                                             Rac1SoundBank.Tone tone,String bankHash) throws IOException {
        if(!SUPPORTED.contains(loop))throw new IOException("Unsupported Clank program");
        boolean heli=loop==Loop.HELI;
        var decoded=tone.decoded();
        String expected=heli?"62babdaeb3f9ac47352498b38559a1f76f4503bc1fe7ec988e103018ebc3a471"
                :"17a3388e73b2d21631cd2a62dbc5bd52b8625da4f3cfd17d49c15562741a109d";
        byte[] metadata=definition.metadata();
        if(metadata.length!=32||SoundBytes.view(metadata).getShort(26)!=loop.local()
                ||definition.cue()!=(heli?24:36)||tone.cue()!=definition.cue()
                ||tone.grain()!=(heli?207:223)||tone.centerNote()!=(heli?-80:-74)
                ||tone.fine()!=(heli?124:65)||tone.toneFlags()!=1
                ||tone.cueVolume()!=127||tone.toneVolume()!=127
                ||!expected.equals(tone.adpcmHash())||!decoded.repeat()
                ||decoded.frames()!=(heli?730:481)||decoded.samples()!=(heli?20440:13468)
                ||decoded.loopStart()!=(heli?28:168)||decoded.loopEnd()!=decoded.samples())
            throw new IOException("Clank cue differs from verified evidence");
        return new Rac1AudioCatalog.Clip(decoded.pcm(),tone.sampleRate(),metadata,tone.rawCue(),tone.rawGrain(),
                bankHash,tone.adpcmHash(),tone.cue(),decoded.loopStart(),decoded.loopEnd(),true);
    }
}
