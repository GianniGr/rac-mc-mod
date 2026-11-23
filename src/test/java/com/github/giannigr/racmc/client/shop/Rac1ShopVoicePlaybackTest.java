package com.github.giannigr.racmc.client.shop;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopVoicePlaybackTest {
    static Rac1ShopVoiceSource.Clip clip() { return new Rac1ShopVoiceSource.Clip(0,44056,1,new byte[56]); }
    static class Driver implements Rac1ShopVoiceSound.Driver {
        final Set<Rac1ShopVoiceSound> active=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Rac1ShopVoiceSound> submitted=new ArrayList<>(),stopped=new ArrayList<>();
        boolean drop,failPlay,failPoll,failStop;
        @Override public void play(Rac1ShopVoiceSound sound) {
            submitted.add(sound);
            if (!drop) active.add(sound);
            if (failPlay) throw new IllegalStateException("synthetic partial submission");
        }
        @Override public boolean active(Rac1ShopVoiceSound sound) {
            if (failPoll) throw new IllegalStateException("synthetic query failure");
            return active.contains(sound);
        }
        @Override public void stop(Rac1ShopVoiceSound sound) {
            stopped.add(sound); active.remove(sound);
            if (failStop) throw new IllegalStateException("synthetic stop failure");
        }
    }
    @Test void privateAdapterIsNonLoopingRelativeSpeechPcmWithoutSubtitleOrFileLookup() throws Exception {
        var sound=new Rac1ShopVoiceSound(clip(),.5f);
        try {
            assertNotNull(sound.resolve(null),"Descriptor does not query an OGG/event catalog");
            assertTrue(sound.getSound().shouldStream());
            assertEquals(net.minecraft.sounds.SoundSource.VOICE,sound.getSource());
            assertTrue(sound.isRelative()); assertFalse(sound.isLooping());
            assertEquals(net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,sound.getAttenuation());
            assertEquals(.5f,sound.getVolume()); assertEquals(1f,sound.getPitch());
            assertTrue(sound.getStream(null,null,false).isDone(),"No asynchronous file/decoder completion");
        } finally { sound.cancel(); }
        assertTrue(sound.isStopped()); assertFalse(sound.canPlaySound());
        assertEquals(0,sound.getStream(null,null,false).join().read(1024).remaining());
    }
    @Test void completionUsesBackendDrainNotPcmEofAndNeverReplays() throws Exception {
        var driver=new Driver();
        try (var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver))) {
            assertEquals(Rac1ShopVoicePlayback.State.IDLE,owner.state());
            var request=owner.request();
            assertTrue(owner.complete(request,clip(),1));
            var sound=driver.submitted.getFirst();
            var stream=sound.getStream(null,null,false).join();
            assertEquals(56,stream.read(1024).remaining());
            assertEquals(0,stream.read(1024).remaining());
            owner.tick();
            assertEquals(Rac1ShopVoicePlayback.State.PLAYING,owner.state(),"Queued PCM drain is not channel completion");
            driver.active.clear(); owner.tick();
            assertEquals(Rac1ShopVoicePlayback.State.FINISHED,owner.state());
            assertEquals(1,driver.stopped.size());
            owner.tick();
            assertFalse(owner.complete(request,clip(),1));
            assertEquals(1,driver.submitted.size());
        }
        assertEquals(1,driver.stopped.size());
    }
    @Test void cancelAndCloseFenceQueuedStreamRequestsAndStaleImports() throws Exception {
        var driver=new Driver();
        var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        var old=owner.request(); var current=owner.request();
        assertFalse(owner.complete(old,clip(),1));
        assertTrue(owner.complete(current,clip(),1));
        var sound=driver.submitted.getFirst();
        owner.cancel();
        assertFalse(sound.canPlaySound()); assertTrue(sound.isStopped());
        assertEquals(0,sound.getStream(null,null,false).join().read(1024).remaining());
        assertFalse(owner.complete(current,clip(),1));
        var pending=owner.request(); owner.close(); owner.close();
        assertEquals(Rac1ShopVoicePlayback.State.CLOSED,owner.state());
        assertNull(owner.request()); assertFalse(owner.complete(pending,clip(),1));
        assertEquals(1,driver.submitted.size()); assertEquals(1,driver.stopped.size());
    }
    @Test void scopeLossBeforeTickPreventsPlaybackAndStopsOnlyOwningViewer() {
        var driver=new Driver(); var scope=new AtomicBoolean(true);
        try (var a=new Rac1ShopVoicePlayback(scope::get,Rac1ShopVoiceSound.backend(driver));
             var b=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver))) {
            var ar=a.request(); var br=b.request();
            assertFalse(b.complete(ar,clip(),1),"Foreign viewer request is not transferable");
            assertTrue(a.complete(ar,clip(),1)); assertTrue(b.complete(br,clip(),1));
            var first=driver.submitted.get(0); var second=driver.submitted.get(1);
            scope.set(false); a.tick();
            assertEquals(Rac1ShopVoicePlayback.State.CLOSED,a.state());
            assertFalse(driver.active.contains(first)); assertTrue(driver.active.contains(second));
            b.tick(); assertEquals(Rac1ShopVoicePlayback.State.PLAYING,b.state());
        }
        var another=new Rac1ShopVoicePlayback(scope::get,Rac1ShopVoiceSound.backend(driver));
        assertNull(another.request());
        var live=new AtomicBoolean(true);
        try (var pending=new Rac1ShopVoicePlayback(live::get,Rac1ShopVoiceSound.backend(driver))) {
            var token=pending.request(); int count=driver.submitted.size();
            live.set(false);
            assertFalse(pending.complete(token,clip(),1),"Late completion must recheck screen/world/source, not wait for tick");
            assertEquals(count,driver.submitted.size());
        }
    }
    @Test void mutedUnavailableAndSubmissionFailureReleaseWithoutRetry() throws Exception {
        var driver=new Driver(); driver.drop=true;
        try (var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver))) {
            assertFalse(owner.complete(owner.request(),clip(),0));
            assertEquals(Rac1ShopVoicePlayback.State.DROPPED,owner.state());
            owner.tick(); assertEquals(1,driver.submitted.size());
            assertEquals(1,driver.stopped.size());
        }
        driver=new Driver(); driver.failPlay=true;
        try (var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver))) {
            assertFalse(owner.complete(owner.request(),clip(),1));
            assertEquals(Rac1ShopVoicePlayback.State.FAILED,owner.state());
            assertNotNull(owner.failure()); assertNull(owner.request());
            var sound=driver.submitted.getFirst();
            assertFalse(sound.canPlaySound());
            assertEquals(0,sound.getStream(null,null,false).join().read(1024).remaining());
            assertEquals(1,driver.stopped.size());
        }
    }
    @Test void pollingAndStopFailuresStillFenceStreamsAndCloseIsTerminal() {
        var driver=new Driver();
        var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver));
        assertTrue(owner.complete(owner.request(),clip(),1));
        driver.failPoll=true; driver.failStop=true;
        owner.tick();
        assertEquals(Rac1ShopVoicePlayback.State.FAILED,owner.state());
        assertNotNull(owner.failure()); assertEquals(1,owner.failure().getSuppressed().length);
        assertFalse(driver.submitted.getFirst().canPlaySound());
        owner.close(); assertEquals(Rac1ShopVoicePlayback.State.CLOSED,owner.state());
    }
    @Test void ownerThreadAndBoundedGainAreEnforcedWithoutStartingAudio() throws Exception {
        var driver=new Driver();
        try (var owner=new Rac1ShopVoicePlayback(() -> true,Rac1ShopVoiceSound.backend(driver))) {
            var error=assertThrows(ExecutionException.class,() -> CompletableFuture.runAsync(owner::request).get());
            assertInstanceOf(IllegalStateException.class,error.getCause());
            assertFalse(owner.complete(owner.request(),clip(),Float.NaN));
            assertEquals(Rac1ShopVoicePlayback.State.FAILED,owner.state());
            assertTrue(driver.submitted.isEmpty());
        }
        assertThrows(IllegalArgumentException.class,() -> new Rac1ShopVoiceSound(
                new Rac1ShopVoiceSource.Clip(36,44056,1,new byte[56]),1));
    }
}
