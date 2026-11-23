package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.clank.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.sound.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Optional Clank audio only. No shared sound catalog/cache/Blaster state or renderer dependency. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ClankAudioClient {
    private static final Rac1ClankAudioGeneration IMPORT=new Rac1ClankAudioGeneration();
    private record Voice(UUID actor,Rac1ClankPcmSound sound,long started) {}
    private static final Map<UUID,Long> RETRY=new HashMap<>();
    private static final Rac1ClankHighEntryAudio HIGH_ENTRIES=new Rac1ClankHighEntryAudio();
    private record EntryVoice(long epoch,Rac1ClankEntrySound sound,long started) {}
    private static final Map<UUID,EntryVoice> ENTRY_VOICES=new HashMap<>();
    private static Rac1ClankAudioLoad.Request source;
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1),task->{
                var thread=new Thread(task,"racmc-clank-audio");thread.setDaemon(true);return thread;
            });
    private static Object world;
    private static long generation=-1;
    private static long tick;
    private record HydroVoice(Rac1HydroHostSound sound,long started) {}
    private static final Rac1HydroAudioLifecycle HYDRO=new Rac1HydroAudioLifecycle(new Rac1HydroAudioLifecycle.Backend() {
        public void decision(UUID actor,String decision,int phase) {
            Rac1HydroTrace.LOG.transition("audio-admission:"+actor,decision,
                    ()->"decision="+decision+" phase="+phase+" clientTick="+tick);
        }
        public Object start(UUID actor,Point at) {
            var catalog=IMPORT.current().catalog();
            if(catalog==null||catalog.hydro()==null)return null;
            var sound=new Rac1HydroHostSound(actor.getLeastSignificantBits(),at,catalog.hydro());
            var mc=Minecraft.getInstance();
            Rac1HydroTrace.LOG.transition("audio-request",actor+":"+tick,()->"actor="+actor+" clientTick="+tick
                    +" soundManager.play Hydro host; PLAYERS="+mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.PLAYERS)
                    +" MASTER="+mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
            Minecraft.getInstance().getSoundManager().play(sound);return new HydroVoice(sound,tick);
        }
        public void position(Object voice,Point at){((HydroVoice)voice).sound().position(at);}
        public void release(Object voice){((HydroVoice)voice).sound().release();}
        public void abort(Object voice) {
            var sound=((HydroVoice)voice).sound();
            Minecraft.getInstance().getSoundManager().stop(sound);sound.close();
        }
        public boolean finished(Object voice) {
            var v=(HydroVoice)voice;
            boolean stopped=v.sound().isStopped();
            if(tick-v.started()<5)return stopped;
            boolean active=Minecraft.getInstance().getSoundManager().isActive(v.sound());
            Rac1HydroTrace.LOG.transition("audio-channel",v.started()+":"+active+":"+stopped,
                    ()->"requestedTick="+v.started()+" clientTick="+tick+" isActive="+active+" streamFinished="+stopped);
            return stopped||!active;
        }
    });
    private static final Rac1ClankLoopLifecycle LOOPS=new Rac1ClankLoopLifecycle(new Rac1ClankLoopLifecycle.Backend() {
        public Object start(UUID actor,Rac1ClankAv.Loop cue,Point at) {
            var snapshot=IMPORT.current();
            var catalog=snapshot.catalog();
            if(snapshot.generation()!=generation||catalog==null||tick<RETRY.getOrDefault(actor,Long.MIN_VALUE))
                return null;
            var clip=catalog.clips().get(cue);
            if(clip==null)return null; // Hydro is never in this catalog.
            var sound=new Rac1ClankPcmSound(cue,actor.getLeastSignificantBits(),at,clip);
            Minecraft.getInstance().getSoundManager().play(sound);
            RacMcMod.LOGGER.debug("Clank {} owned voice requested for {}",cue,actor);
            return new Voice(actor,sound,tick);
        }
        public void position(Object handle,Point at){((Voice)handle).sound().position(at);}
        public void stop(Object handle) {
            var voice=(Voice)handle;
            Minecraft.getInstance().getSoundManager().stop(voice.sound());
            voice.sound().close();
            RacMcMod.LOGGER.debug("Clank owned voice retired for {}",voice.actor());
        }
        public boolean alive(Object handle) {
            var voice=(Voice)handle;
            // SoundManager starts channels asynchronously. Do not restart a pending intro every tick.
            if(tick-voice.started()<5||Minecraft.getInstance().getSoundManager().isActive(voice.sound()))return true;
            RETRY.put(voice.actor(),tick+20); // Drop/rejection under channel pressure: bounded retry.
            RacMcMod.LOGGER.debug("Clank voice not active after channel startup for {}; retry delayed",voice.actor());
            return false;
        }
    },Rac1ClankAudioScene.MAX_VOICES);
    private Rac1ClankAudioClient() {}
    private static void start(Rac1ClankAudioLoad.Request request) {
        clearOwned();source=request;
        long token=IMPORT.invalidate();
        WORK.getQueue().clear(); // One active read plus one latest request, never unbounded revalidations.
        long started=System.nanoTime();
        CompletableFuture.supplyAsync(()->{
            try {
                return Rac1ClankAudioLoad.load(request,r->RacMcClient.media().lease(r.image(),r.discKey()));
            } catch(Exception failure) {
                RacMcMod.LOGGER.warn("Optional Clank audio unavailable; other audio unaffected: {}",failure.toString());
                return null;
            }
        },WORK).thenAccept(built->Minecraft.getInstance().execute(()->{
            if(!request.equals(source)||!request.equals(selection()))return;
            if(!IMPORT.publish(token,built))return;
            if(built!=null) {
                RacMcMod.LOGGER.info("Optional Clank verified glide cues ready {} in {} ms (not audible-output acceptance).",
                        built.clips().keySet(),(System.nanoTime()-started)/1e6);
                built.failures().forEach((cue,failure)->RacMcMod.LOGGER.warn(
                        "Optional Clank {} audio unavailable; other cues unaffected: {}",cue,failure));
                built.highEntries().failures().forEach((cue,failure)->RacMcMod.LOGGER.warn(
                        "Optional Clank {} high entry unavailable: {}",cue,failure));
                if(built.hydro()!=null)RacMcMod.LOGGER.info(
                        "Optional NTSC Hydro host script ready (48-kHz host adaptation, not audible-output acceptance).");
                else RacMcMod.LOGGER.debug("Optional Hydro host script unavailable: {}",built.hydroFailure());
            }
        }));
    }
    private static Rac1ClankAudioLoad.Request selection() {
        var media=RacMcClient.media();
        if(!media.isReady()||media.isChecking())return null;
        var content=RacMcClient.assets().content();var image=media.configuredPath();
        return content.isEmpty()||image.isEmpty()?null:
                new Rac1ClankAudioLoad.Request(image.get(),content.get().discKey(),media.generation());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        var configured=selection();
        if(configured==null) {
            if(source!=null){IMPORT.invalidate();source=null;WORK.getQueue().clear();}
            clearOwned();world=mc.level;return;
        }
        if(!configured.equals(source))start(configured);
        var capability=IMPORT.current();
        if(world!=mc.level||generation!=capability.generation()) {
            clearOwned();world=mc.level;generation=capability.generation();
            return; // Do not consume a previous world's view during subscriber teardown.
        }
        if(mc.level==null||mc.player==null){clearOwned();return;}
        tick=mc.level.getGameTime();
        var actors=new ArrayList<Rac1ClankAudioScene.Actor>();
        for(var player:mc.level.players()) {
            var id=player.getUUID();var clank=Rac1ClankClient.view(id);
            if(clank==null)continue;
            var hydro=Rac1HydroClient.view(id);
            boolean fresh=Rac1ClankClient.fresh(id,tick)
                    &&(hydro==null||tick-hydro.localTick()<=4);
            fresh|=hydro!=null&&Rac1HydroPresentation.fresh(clank,hydro.state(),hydro.localTick(),tick);
            var at=player.position();
            actors.add(new Rac1ClankAudioScene.Actor(clank,hydro==null?null:hydro.state(),
                    fresh&&player.isAlive()&&!player.isSpectator()&&!player.isRemoved(),
                    player.getDeltaMovement().y,player.distanceToSqr(mc.player),new Point(at.x,at.y,at.z)));
        }
        var requests=Rac1ClankAudioScene.requests(actors,capability.catalog());
        highEntries(actors,capability.catalog());
        Rac1ClankActionAudioClient.tick(actors,capability.catalog(),generation,tick);
        hydro(actors,capability.catalog());
        Set<UUID> retained=new HashSet<>();requests.forEach(r->retained.add(r.actor()));
        // Retire untracked/out-of-range/budget-displaced voices BEFORE admitting nearer voices.
        LOOPS.retain(retained);RETRY.keySet().retainAll(retained);
        for(var request:requests)LOOPS.update(request.actor(),request.epoch(),request.actionSerial(),
                request.cue(),request.position());
    }
    private static void highEntries(List<Rac1ClankAudioScene.Actor> actors,Rac1ClankAudioCatalog catalog) {
        actors.sort(Comparator.comparingDouble(Rac1ClankAudioScene.Actor::distanceSquared));
        Set<UUID> retained=new HashSet<>();
        for(var actor:actors) {
            var state=actor.clank();var id=state.player();
            boolean valid=actor.valid()&&state.equipment().worn()
                    &&Double.isFinite(actor.distanceSquared())&&actor.distanceSquared()>=0
                    &&actor.distanceSquared()<=Rac1ClankAudioScene.RANGE_SQUARED
                    &&(actor.hydro()==null||!actor.hydro().wet());
            if(valid&&retained.size()<16)retained.add(id);else continue;
            var voice=ENTRY_VOICES.get(id);
            if(voice!=null&&(voice.epoch()!=state.epoch()||tick-voice.started()>=5
                    &&!Minecraft.getInstance().getSoundManager().isActive(voice.sound()))) {
                stopEntry(voice);ENTRY_VOICES.remove(id);voice=null;
            }
            if(voice!=null)voice.sound().position(actor.position());
            var cue=HIGH_ENTRIES.observe(state,true);
            if(cue.isEmpty()||catalog==null)continue; // Clock progresses even before media is ready.
            var clip=catalog.highEntries().clips().get(cue.get());
            if(clip==null||ENTRY_VOICES.size()>=2&&voice==null)continue; // Separate bounded one-shot budget.
            if(voice!=null)stopEntry(voice);
            var sound=new Rac1ClankEntrySound(cue.get(),id.getLeastSignificantBits()^state.actionSerial(),actor.position(),clip);
            Minecraft.getInstance().getSoundManager().play(sound);
            ENTRY_VOICES.put(id,new EntryVoice(state.epoch(),sound,tick));
        }
        HIGH_ENTRIES.retain(retained);
        ENTRY_VOICES.entrySet().removeIf(e->{if(retained.contains(e.getKey()))return false;stopEntry(e.getValue());return true;});
    }
    private static void stopEntry(EntryVoice voice) {
        Minecraft.getInstance().getSoundManager().stop(voice.sound());voice.sound().close();
    }
    private static void hydro(List<Rac1ClankAudioScene.Actor> actors,Rac1ClankAudioCatalog catalog) {
        var retained=new HashSet<UUID>();
        // highEntries has already sorted by distance. Separate nearest-two Hydro actor budget.
        for(var actor:actors) {
            var c=actor.clank();var h=actor.hydro();
            if(!actor.valid()||h==null||!h.access()||!h.wet()||!c.equipment().worn()
                    ||h.clankEpoch()!=c.epoch()||!h.player().equals(c.player())
                    ||!Double.isFinite(actor.distanceSquared())||actor.distanceSquared()<0
                    ||actor.distanceSquared()>Rac1ClankAudioScene.RANGE_SQUARED||retained.size()>=2)continue;
            retained.add(c.player());
        }
        HYDRO.retain(retained); // Hard invalidation before admission, never synthesize a tail.
        if(Rac1HydroTrace.LOG.enabled())for(var actor:actors) {
            var h=actor.hydro();var player=Minecraft.getInstance().player;
            if(h==null||player==null||!actor.clank().player().equals(player.getUUID()))continue;
            String state=h.epoch()+":"+h.water()+":"+h.controlled()+":"+h.motion().stage()
                    +":"+actor.valid()+":"+retained.contains(h.player())+":"+(catalog!=null&&catalog.hydro()!=null);
            Rac1HydroTrace.LOG.transition("client-audio-state",state,()->"water="+h.water()
                    +" controlled="+h.controlled()+" stage="+h.motion().stage()+" phase="+h.motion().phase()
                    +" stroke="+h.motion().stroke()+" actorValid="+actor.valid()
                    +" aerialFresh="+Rac1ClankClient.fresh(h.player(),tick)
                    +" budgetAdmitted="+retained.contains(h.player())+" catalogReady="+(catalog!=null&&catalog.hydro()!=null)
                    +" pose="+player.getPose()+" swimming="+player.isSwimming()+" vanillaSprint="+player.isSprinting());
        }
        for(var actor:actors)if(retained.contains(actor.clank().player())) {
            var h=actor.hydro();
            boolean thrust=h.controlled()&&h.water()==Rac1HydroWater.Mode.SUBMERGED
                    &&h.motion().stage()==Rac1HydroMotion.Stage.THRUST;
            HYDRO.update(h.player(),h.epoch(),h.tick(),thrust,h.motion().phase(),
                    catalog!=null&&catalog.hydro()!=null,actor.position());
        }
    }
    private static void clearOwned(){
        Rac1ClankActionAudioClient.clear();
        HYDRO.clear();LOOPS.clear();RETRY.clear();HIGH_ENTRIES.clear();ENTRY_VOICES.values().forEach(Rac1ClankAudioClient::stopEntry);
        ENTRY_VOICES.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        clearOwned();world=null;IMPORT.invalidate();source=null;WORK.getQueue().clear();
    }
    private static void reload() {
        clearOwned();IMPORT.invalidate();source=null;WORK.getQueue().clear();
    }
    @EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    resources->Minecraft.getInstance().execute(Rac1ClankAudioClient::reload));
        }
    }
}
