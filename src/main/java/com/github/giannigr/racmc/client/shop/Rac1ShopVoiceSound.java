package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.client.sound.Rac1PcmStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Private PCM instance, not a shared SBlk event/catalog entry or a source cue ID. */
final class Rac1ShopVoiceSound extends AbstractSoundInstance implements TickableSoundInstance {
    private final Rac1PcmStream stream;
    private boolean cancelled;
    Rac1ShopVoiceSound(Rac1ShopVoiceSource.Clip clip,float gain) {
        // Internal transport label only. No guessed original subtitle or sound-event registration.
        super(ResourceLocation.fromNamespaceAndPath("racmc","shop_pcm"),SoundSource.VOICE,RandomSource.create(0));
        if (clip.rate()!=44056 || clip.frames()<1 || clip.frames()>3037
                || clip.pcm().length!=clip.frames()*56 || !Float.isFinite(gain) || gain<0 || gain>1)
            throw new IllegalArgumentException("Invalid bounded dialogue");
        try { Rac1ShopVoiceSource.englishIndex(10000+clip.index()); }
        catch (java.io.IOException invalid) { throw new IllegalArgumentException(invalid); }
        stream=new Rac1PcmStream(clip.pcm(),clip.rate());
        volume=gain; pitch=1; looping=false; relative=true; attenuation=Attenuation.NONE;
    }
    @Override public void tick() { }
    @Override public boolean isStopped() { return cancelled; }
    @Override public boolean canPlaySound() { return !cancelled; }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound=new Sound(location,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
        return new WeighedSoundEvents(location,null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound selected,boolean loop) {
        // Immediate, memory-only completion: no late file/decoder task can attach after cancellation.
        return CompletableFuture.completedFuture(stream);
    }
    void cancel() {
        if (cancelled) return;
        cancelled=true; stream.close();
    }
    interface Driver {
        void play(Rac1ShopVoiceSound sound);
        boolean active(Rac1ShopVoiceSound sound);
        void stop(Rac1ShopVoiceSound sound);
    }
    static Rac1ShopVoicePlayback.Backend backend(Supplier<SoundManager> manager) {
        return (clip,gain) -> {
            SoundManager owner=Objects.requireNonNull(manager.get());
            return start(new Driver() {
                @Override public void play(Rac1ShopVoiceSound sound) { owner.play(sound); }
                @Override public boolean active(Rac1ShopVoiceSound sound) { return owner.isActive(sound); }
                @Override public void stop(Rac1ShopVoiceSound sound) { owner.stop(sound); }
            },clip,gain);
        };
    }
    static Rac1ShopVoicePlayback.Backend backend(Driver driver) {
        return (clip,gain) -> start(driver,clip,gain);
    }
    private static Rac1ShopVoicePlayback.Handle start(Driver owner,Rac1ShopVoiceSource.Clip clip,float gain) {
            var sound=new Rac1ShopVoiceSound(clip,gain);
            try { owner.play(sound); }
            catch (RuntimeException failure) {
                sound.cancel();
                try { owner.stop(sound); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            return new Rac1ShopVoicePlayback.Handle() {
                private boolean closed;
                @Override public boolean active() { return !closed && owner.active(sound); }
                @Override public void close() {
                    if (closed) return;
                    closed=true;
                    sound.cancel(); // fence future stream reads before stopping only this instance
                    owner.stop(sound);
                }
            };
    }
}
