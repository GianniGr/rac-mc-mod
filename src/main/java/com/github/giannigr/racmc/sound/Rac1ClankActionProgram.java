package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.util.*;

/** Only retained local43/46/62 programs; no generic bank VM or first-tone fallback. */
public record Rac1ClankActionProgram(int local,List<Rac1AudioCatalog.Clip> tones) {
    public Rac1ClankActionProgram {
        tones=List.copyOf(tones);
        if(local!=1&&local!=43&&local!=46&&local!=62||tones.size()!=(local==62?2:1)
                ||(local==1||local==62)&&tones.stream().anyMatch(Rac1AudioCatalog.Clip::repeat))
            throw new IllegalArgumentException("Unsupported action script");
    }
    public int startDelay240(){return local==43?10:local==46?50:0;}
    public int keyOff240(){return local==43?250:local==46?270:-1;}
    public int stop240(){return local==43?350:local==46?430:-1;}
    /** Decode only the exactly retained cue/program/tone references inside the already hash-checked bank. */
    public static Rac1ClankActionProgram read(int local,Rac1SoundMap.Definition def,byte[] bytes)throws IOException {
        String hash=SoundBytes.hash(bytes);
        if(!hash.equals("50e2f3ad91d16288ed2d4bd1da432e2824705c8b35d589e7a65ccf8da685f366")
                &&!hash.equals("2330fb88665d0ba255cf7359f7208be0cd433a86926a90320b90b98c671bc8dd"))
            throw new IOException("Unverified action bank");
        int cueId=local==43?54:local==46?55:local==62?63:-1;
        if(cueId<0||def.cue()!=cueId||def.metadata().length!=32
                ||SoundBytes.view(def.metadata()).getShort(26)!=local)
            throw new IOException("Wrong action definition");
        var b=SoundBytes.view(bytes);int base=b.getInt(8),sampleBase=b.getInt(16),sampleSize=b.getInt(20);
        int cues=base+b.getInt(base+28),grains=base+b.getInt(base+32);
        byte[] cue=Arrays.copyOfRange(bytes,cues+cueId*12,cues+(cueId+1)*12);
        int first=local==43?258:local==46?261:290,count=local==62?7:3;
        if((cue[0]&255)!=127||(cue[4]&255)!=count||SoundBytes.view(cue).getInt(8)!=first*40)
            throw new IOException("Wrong action program row");
        byte[] raw=Arrays.copyOfRange(bytes,grains+first*40,grains+(first+count)*40);
        if(!Arrays.equals(raw,metadata(local)))throw new IOException("Action script differs from retained evidence");
        var tones=new ArrayList<Rac1AudioCatalog.Clip>();
        byte[] samples=Arrays.copyOfRange(bytes,sampleBase,sampleBase+sampleSize);
        for(int n=0;n<count;n++) {
            byte[] row=Arrays.copyOfRange(raw,n*40,(n+1)*40);var g=SoundBytes.view(row);
            if(g.getInt(0)!=1)continue;
            int start=g.getInt(24);
            var decoded=PsAdpcm.decode(samples,start,sampleSize);
            String adpcmHash=SoundBytes.hash(Arrays.copyOfRange(samples,start,start+decoded.frames()*16));
            var tone=new Rac1SoundBank.Tone(cueId,first+n,127,row[9]&255,row[10],row[11]&255,
                    Short.toUnsignedInt(g.getShort(22)),cue,row,adpcmHash,decoded);
            tones.add(new Rac1AudioCatalog.Clip(decoded.pcm(),tone.sampleRate(),def.metadata(),cue,row,
                    hash,adpcmHash,cueId,decoded.loopStart(),decoded.loopEnd(),decoded.repeat()));
        }
        return new Rac1ClankActionProgram(local,tones);
    }
    static byte[] metadata(int local) {
        String hex=switch(local) {
            case 43 -> "010000000a000000647fb07c000000000000ffb4cc9f0100907b0200000000000000000000000000"
                    +"29000000f00000000000000000000000000000000000000000000000000000000000000000000000"
                    +"18000000640000000000000000000000000000000000000000000000000000000000000000000000";
            case 46 -> "0100000032000000647fb07c000000000000ffb9eb9f0100907b0200000000000000000000000000"
                    +"29000000dc0000000000000000000000000000000000000000000000000000000000000000000000"
                    +"18000000a00000000000000000000000000000000000000000000000000000000000000000000000";
            case 62 -> "1f000000000000000000000027000000000000000000000000000000000000000000000000000000"
                    +"22000000000000000000020000000000000000000000000000000000000000000000000000000000"
                    +"18000000000000000000000000000000000000000000000000000000000000000000000000000000"
                    +"1b000000000000005600000000000000000000000000000000000000000000000000000000000000"
                    +"19000000000000000200010000000000000000000000000000000000000000000000000000000000"
                    +"0100000000000000647faa42000000000102ff80c09f0100f0880600000000000000000000000000"
                    +"0100000000000000647faa42000000000102ff80c09f010050a00600000000000000000000000000";
            default -> throw new IllegalArgumentException("Outside action program scope");
        };
        return HexFormat.of().parseHex(hex);
    }
}
