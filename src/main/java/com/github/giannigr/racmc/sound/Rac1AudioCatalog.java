package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Immutable, bounded client-owned generation. No game bytes travel over the network. */
public record Rac1AudioCatalog(Map<Rac1AudioEvent, Clip> clips) {
    public Rac1AudioCatalog { clips = Map.copyOf(clips); }
    public record Clip(byte[] pcm, float rate, byte[] definition, byte[] cue, byte[] grain,
                       String bankHash, String adpcmHash, int cueId, int loopStart, int loopEnd, boolean repeat) {
        public Clip {
            pcm = pcm.clone(); definition = definition.clone(); cue = cue.clone(); grain = grain.clone();
            if (pcm.length == 0 || (pcm.length & 1) != 0 || pcm.length > PsAdpcm.MAX_FRAMES * 56
                    || !Float.isFinite(rate) || rate < 1000 || rate > 96000
                    || definition.length != 32 || cue.length != 12 || grain.length != 40
                    || !bankHash.matches("[0-9a-f]{64}") || !adpcmHash.matches("[0-9a-f]{64}")
                    || cueId < 0 || loopStart < -1 || loopEnd != pcm.length / 2
                    || loopStart >= loopEnd) throw new IllegalArgumentException("Invalid audio clip");
        }
        @Override public byte[] pcm() { return pcm.clone(); }
        @Override public byte[] definition() { return definition.clone(); }
        @Override public byte[] cue() { return cue.clone(); }
        @Override public byte[] grain() { return grain.clone(); }
        public float gain() {
            // Near volume is a 0..1024 game gain; cue/tone volumes are 0..127.
            int near = SoundBytes.view(definition).getInt(12);
            return Math.clamp(near / 1024f * (cue[0] & 255) / 127f * (grain[9] & 255) / 127f, 0f, 1f);
        }
    }
    /** Import only the explicitly supported reference profile; caller owns a verified image. */
    public static Rac1AudioCatalog extract(Rac1DiscImage image) throws Exception {
        var tables = Rac1DiscTables.read(image);
        var header = Rac1LevelHeader.read(image, tables.levelSector(1));
        var container = Rac1LevelData.read(image, header);
        var bank = new Rac1SoundBank(container.soundBank(image));
        byte[] index = image.readBytes(container.base() + container.coreIndexOffset(), container.coreIndexSize());
        var assets = Rac1LevelAssets.load(image, 1);
        var result = new EnumMap<Rac1AudioEvent, Clip>(Rac1AudioEvent.class);
        for (var event : Rac1AudioEvent.values()) {
            var entry = assets.coreIndex().mobyClass(event.classId());
            if (entry == null) throw new IOException("Missing reference sound class");
            var definition = Rac1SoundMap.resolve(index, assets.classBlob(entry.offset()), event.classId(), event.slot());
            var tone = bank.tone(definition.cue());
            var decoded = tone.decoded();
            result.put(event, new Clip(decoded.pcm(), tone.sampleRate(), definition.metadata(),
                    tone.rawCue(), tone.rawGrain(), bank.digest(), tone.adpcmHash(), tone.cue(),
                    decoded.loopStart(), decoded.loopEnd(), decoded.repeat()));
        }
        return new Rac1AudioCatalog(result);
    }
}
