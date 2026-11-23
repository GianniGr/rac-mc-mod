package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native configured keys + bounded local prediction. Only server snapshots select effective remote pack/access. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1HydroClient {
    public record View(Rac1HydroWire.Snapshot state,long localTick) {}
    private record Pending(long serial,Rac1HydroBoost.Controls keys,double lookPitch) {}
    private static final Map<UUID,View> VIEWS=new HashMap<>();
    private static final ArrayDeque<Pending> PENDING=new ArrayDeque<>();
    private static Rac1HydroMotion motion=new Rac1HydroMotion();
    private static final Rac1HydroHeldKeys.Sprint SPRINT=new Rac1HydroHeldKeys.Sprint();
    private static Rac1HydroBoost.Controls controls=Rac1HydroBoost.Controls.INACTIVE;
    private static Rac1HydroBoost boost=Rac1HydroBoost.BLOCKED;
    private static Vec3 expected;
    private static Rac1HydroWater.Mode previousWater=Rac1HydroWater.Mode.DRY;
    private static Object world;
    private static long epoch=-1,serial,lastTick=-1,lastAck=-1;
    private static int inputTick=-1,travelTick=-1,controlledTick=-1;
    private Rac1HydroClient() {}
    public static View view(UUID player){return VIEWS.get(player);}
    private static boolean submerged(Rac1HydroWire.Snapshot state) {
        return state.wet()&&state.water()==Rac1HydroWater.Mode.SUBMERGED;
    }
    public static void accept(Rac1HydroWire.Snapshot state) {
        var minecraft=Minecraft.getInstance();if(minecraft.level==null)return;
        var old=VIEWS.get(state.player());
        if(old!=null&&(state.epoch()<old.state().epoch()
                ||state.epoch()==old.state().epoch()&&state.tick()<old.state().tick()))return;
        if(VIEWS.size()>=256&&!VIEWS.containsKey(state.player()))
            VIEWS.entrySet().stream().min(java.util.Comparator.comparingLong(e->e.getValue().localTick()))
                    .ifPresent(e->VIEWS.remove(e.getKey()));
        VIEWS.put(state.player(),new View(state,minecraft.level.getGameTime()));
        var player=minecraft.player;if(player==null||!player.getUUID().equals(state.player()))return;
        Rac1HydroClientDiagnostics.snapshot(player,state);
        if(!state.access()){reset(-1,null);return;}
        if(world!=player.level()||epoch!=state.epoch()) {
            reset(state.epoch(),player.level());motion.restore(state.motion());previousWater=state.water();return;
        }
        if(state.tick()<=lastTick||state.acknowledged()<lastAck||state.acknowledged()>=serial)return;
        lastTick=state.tick();lastAck=state.acknowledged();
        while(!PENDING.isEmpty()&&PENDING.peekFirst().serial()<=lastAck)PENDING.removeFirst();
        boolean correcting=state.controlled()||motion.controlled()||controlledTick>=player.tickCount-2;
        boolean eligible=submerged(state)&&Rac1HydroWater.observe(player).propulsion();
        if(!submerged(state)) {
            // A server-observed water transition invalidates old predicted physical edges.
            PENDING.clear();controls=Rac1HydroBoost.Controls.INACTIVE;inputTick=-1;
        }
        motion.restore(state.motion());
        boost=eligible?state.boost():Rac1HydroBoost.BLOCKED;
        if(correcting&&eligible&&!externalVelocity(player)) {
            player.setPos(state.position().x(),state.position().y(),state.position().z());
            player.setDeltaMovement(Rac1HydroRuntime.vec(motion.velocity()).scale(1.0/20));
            expected=state.controlled()?player.getDeltaMovement():null;
            for(var pending:PENDING)simulate(player,pending.keys(),pending.lookPitch());
        }else {
            motion.cancel();expected=null;
            for(var pending:PENDING)boost=boost.sample(pending.keys(),eligible);
        }
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        var minecraft=Minecraft.getInstance();
        if(!(event.getEntity() instanceof LocalPlayer player)||minecraft.player!=player)return;
        var view=VIEWS.get(player.getUUID());
        if(view==null||!view.state().access())return;
        if(epoch!=view.state().epoch()||world!=player.level())reset(view.state().epoch(),player.level());
        var keys=event.getInput();
        var water=Rac1HydroWater.observe(player);
        boolean active=minecraft.screen==null&&minecraft.isWindowActive();
        var sprint=SPRINT.sample(minecraft.options.keySprint,active&&submerged(view.state())&&water.propulsion()
                &&player.level().getGameTime()-view.localTick()<=4);
        controls=sprint!=Rac1HydroHeldKeys.Sample.UNAVAILABLE?new Rac1HydroBoost.Controls(true,
                sprint==Rac1HydroHeldKeys.Sample.DOWN,
                 Rac1HydroHeldKeys.down(minecraft.options.keyJump,active),
                 Rac1HydroHeldKeys.down(minecraft.options.keyShift,active),
                 (keys.left?1:0)-(keys.right?1:0),(keys.up?1:0)-(keys.down?1:0),player.getYRot())
                 :Rac1HydroBoost.Controls.INACTIVE;
        inputTick=player.tickCount;
        long next=serial++;PENDING.addLast(new Pending(next,controls,Rac1HydroMotion.boundedLookPitch(player.getXRot())));
        while(PENDING.size()>8)PENDING.removeFirst();
        PacketDistributor.sendToServer(new Rac1HydroNetwork.Input(new Rac1HydroWire.Input(epoch,next,controls)));
        var probe=motion.copy();var preview=boost.sample(controls,water.propulsion());var frame=preview.drive(controls);
        Rac1HydroClientDiagnostics.input(player,controls,preview,active,water);
        if(water.propulsion()&&!externalVelocity(player))for(int i=0;i<3;i++) {
            var step=probe.step(frame,Rac1HydroRuntime.vector(player.getDeltaMovement().scale(20)),
                    previousWater==Rac1HydroWater.Mode.SURFACE&&frame.dive(),water.lowAir(),player.getXRot());
            if(step.controlled()){keys.jumping=false;keys.shiftKeyDown=false;break;}
        }
    }
    /** Called only through the existing Clank travel bridge for the native local player. */
    public static boolean travel(LocalPlayer player) {
        var view=VIEWS.get(player.getUUID());
        if(view==null||!view.state().access()||view.state().epoch()!=epoch||world!=player.level())return false;
        if(travelTick==player.tickCount)return controlledTick==player.tickCount;
        travelTick=player.tickCount;
        boolean controlled=simulate(player,inputTick==player.tickCount?controls:Rac1HydroBoost.Controls.INACTIVE,
                player.getXRot());
        if(controlled)controlledTick=player.tickCount;
        return controlled;
    }
    private static boolean externalVelocity(LocalPlayer player) {
        return expected!=null&&player.getDeltaMovement().distanceToSqr(expected)>.0025;
    }
    private static boolean simulate(LocalPlayer player,Rac1HydroBoost.Controls keys,double lookPitch) {
        var water=Rac1HydroWater.observe(player);
        boolean dive=previousWater==Rac1HydroWater.Mode.SURFACE&&water.mode()==Rac1HydroWater.Mode.SUBMERGED;
        previousWater=water.mode();
        boost=boost.sample(keys,water.propulsion());
        if(!water.propulsion()||externalVelocity(player)){motion.cancel();expected=null;return false;}
        var frame=boost.drive(keys);
        var nativeVelocity=Rac1HydroRuntime.vector(player.getDeltaMovement().scale(20));
        if(nativeVelocity.length()>32){motion.cancel();expected=null;return false;}
        boolean controlled=false;
        for(int i=0;i<3;i++) {
            var current=Rac1HydroWater.observe(player);
            if(!current.propulsion()){boost=Rac1HydroBoost.BLOCKED;motion.cancel();break;}
            var step=motion.step(frame,nativeVelocity,dive&&frame.dive(),current.lowAir(),lookPitch);
            if(!step.controlled())continue;
            if(step.displacement().length()>.54){motion.cancel();break;}
            controlled=true;var before=player.position();
            player.move(MoverType.SELF,Rac1HydroRuntime.vec(step.displacement()));
            motion.resolved(Rac1HydroRuntime.vector(player.position().subtract(before)));dive=false;
        }
        if(controlled){
            player.setDeltaMovement(Rac1HydroRuntime.vec(motion.velocity()).scale(1.0/20));
            expected=player.getDeltaMovement();
            if(!Rac1HydroWater.observe(player).propulsion()){boost=Rac1HydroBoost.BLOCKED;motion.cancel();}
        }else expected=null;
        return controlled;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level=Minecraft.getInstance().level;
        if(level==null)return;
        VIEWS.entrySet().removeIf(e->{
            boolean expired=level.getGameTime()-e.getValue().localTick()>200;
            if(expired)Rac1HydroPlayback.forget(e.getKey());
            return expired;
        });
        var player=Minecraft.getInstance().player;
        if(epoch>=0&&(player==null||!VIEWS.containsKey(player.getUUID())))reset(-1,null);
        Rac1HydroResources.update(VIEWS.values().stream().anyMatch(v->v.state().access()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEWS.clear();reset(-1,null);Rac1HydroResources.clear();
    }
    private static void reset(long next,Object level) {
        epoch=next;world=level;serial=0;lastAck=-1;lastTick=-1;inputTick=-1;travelTick=-1;controlledTick=-1;
        PENDING.clear();motion=new Rac1HydroMotion();controls=Rac1HydroBoost.Controls.INACTIVE;
        boost=Rac1HydroBoost.BLOCKED;expected=null;
        previousWater=Rac1HydroWater.Mode.DRY;
    }
    @EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(()->Rac1HydroNetwork.receiver(Rac1HydroClient::accept));
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    manager->Rac1HydroResources.clear());
        }
    }
}
