package com.github.giannigr.racmc.client.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.sound.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Accepted server presentation events only; no prediction, key or rendered hero/Steve sampling. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class Rac1ClankActionAudioClient {
    private record Voice(long epoch,long serial,Rac1ClankActionSound sound,long started,boolean completed) {}
    private record Selector(long epoch,Rac1ClankActionMixer.Selector value) {}
    private static final Rac1ClankActionInbox INBOX=new Rac1ClankActionInbox();
    private static final Map<UUID,Voice> VOICES=new HashMap<>();
    private static final Map<UUID,Selector> SELECTORS=new HashMap<>();
    private static Object world;
    private static long generation=-1;
    private static boolean ready;
    private Rac1ClankActionAudioClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->Rac1ClankActionAudioNetwork.receiver(value->{
            var mc=Minecraft.getInstance();
            if(mc.level==null||world!=mc.level)return;
            INBOX.offer(value,mc.level.getGameTime(),generation,ready);
        }));
    }
    static void tick(List<Rac1ClankAudioScene.Actor> actors,Rac1ClankAudioCatalog catalog,long gen,long tick) {
        world=Minecraft.getInstance().level;generation=gen;ready=catalog!=null;
        var pendingEvents=INBOX.drain(tick,gen);
        for(var pending:pendingEvents)if(pending.event().local()==0) {
            var event=pending.event();var voice=VOICES.get(event.actor());
            if(voice!=null&&voice.epoch()==event.epoch()&&voice.serial()==event.serial())
                VOICES.put(event.actor(),new Voice(voice.epoch(),voice.serial(),voice.sound(),voice.started(),true));
        }
        var admitted=new LinkedHashMap<UUID,Rac1ClankAudioScene.Actor>();
        // Main Clank audio subscriber already sorted by distance. Hard separate two-action-channel budget.
        for(var actor:actors) {
            var c=actor.clank();
            var voice=VOICES.get(c.player());
            if(!actor.valid()||!c.equipment().worn()||c.action()==Rac1ClankCapability.Action.IDLE
                    &&(voice==null||!voice.completed())
                    ||c.action()==Rac1ClankCapability.Action.GLIDE
                    ||actor.hydro()!=null&&actor.hydro().wet()
                    ||!Double.isFinite(actor.distanceSquared())||actor.distanceSquared()<0
                    ||actor.distanceSquared()>Rac1ClankAudioScene.RANGE_SQUARED)continue;
            if(admitted.size()<16)admitted.put(c.player(),actor);
        }
        var nearest=new HashSet<>(admitted.keySet().stream().limit(2).toList());
        VOICES.entrySet().removeIf(e->{
            var actor=admitted.get(e.getKey());var v=e.getValue();
            boolean stop=actor==null||!nearest.contains(e.getKey())||actor.clank().epoch()!=v.epoch()
                    ||actor.clank().actionSerial()!=v.serial()||v.sound().isStopped()
                    ||tick-v.started()>=5&&!Minecraft.getInstance().getSoundManager().isActive(v.sound());
            if(stop)stop(v);else v.sound().position(actor.position());return stop;
        });
        var selectorOwners=new HashSet<UUID>();
        for(var actor:actors)if(actor.valid()&&actor.clank().equipment().worn()
                &&Double.isFinite(actor.distanceSquared())&&actor.distanceSquared()>=0
                &&actor.distanceSquared()<=Rac1ClankAudioScene.RANGE_SQUARED&&selectorOwners.size()<16)
            selectorOwners.add(actor.clank().player());
        SELECTORS.keySet().retainAll(selectorOwners); // Previous choice survives ordinary idle, not session loss.
        for(var pending:pendingEvents) {
            var event=pending.event();
            if(event.local()==0||catalog==null)continue;
            // A reached event can arrive before its same-tick state broadcast, including from idle.
            var observed=actors.stream().filter(a->a.clank().player().equals(event.actor())).findFirst().orElse(null);
            if(observed==null||!observed.valid())continue;
            var c=observed.clank();
            if(Rac1ClankActionInbox.needsSnapshot(pending,c.epoch(),c.actionSerial(),c.serverTick(),tick)) {
                INBOX.defer(pending);continue;
            }
            var actor=admitted.get(event.actor());
            if(actor==null||c.action()==Rac1ClankCapability.Action.IDLE)continue;
            if(c.epoch()!=event.epoch()||c.actionSerial()!=event.serial()
                    ||c.serverTick()<event.tick()||c.serverTick()-event.tick()>2
                    ||c.phaseSourceSteps()<=event.sourceStep())continue;
            var program=catalog.actions().get(event.local());if(program==null)continue;
            // Events from displaced actors are consumed, not queued for budget/readiness recovery.
            if(!nearest.contains(event.actor()))continue;
            var selector=SELECTORS.get(event.actor());
            if(selector==null||selector.epoch()!=c.epoch()) {
                selector=new Selector(c.epoch(),new Rac1ClankActionMixer.Selector());
                SELECTORS.put(event.actor(),selector);
            }
            long seed=event.actor().getLeastSignificantBits()^event.serial()*31^event.sourceStep();
            var choice=selector.value().choose(program,new Random(seed));
            if(!choice.audible())continue; // Actual register-gated branch, never substitute a grunt.
            var old=VOICES.remove(event.actor());if(old!=null)stop(old);
            var sound=new Rac1ClankActionSound(seed,actor.position(),program,choice);
            Minecraft.getInstance().getSoundManager().play(sound);
            VOICES.put(event.actor(),new Voice(c.epoch(),c.actionSerial(),sound,tick,false));
        }
    }
    private static void stop(Voice v){Minecraft.getInstance().getSoundManager().stop(v.sound());v.sound().close();}
    static void clear() {
        VOICES.values().forEach(Rac1ClankActionAudioClient::stop);VOICES.clear();
        SELECTORS.clear();INBOX.clear();world=null;generation=-1;ready=false;
    }
}
