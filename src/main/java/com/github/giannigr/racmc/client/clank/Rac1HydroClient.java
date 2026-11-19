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
    private record Pending(long serial,Rac1HydroMotion.Input frame) {}
    private static final Map<UUID,View> VIEWS=new HashMap<>();
    private static final ArrayDeque<Pending> PENDING=new ArrayDeque<>();
    private static Rac1HydroMotion motion=new Rac1HydroMotion();
    private static Rac1HydroMotion.Input frame=Rac1HydroMotion.Input.RELEASED;
    private static Rac1HydroWater.Mode previousWater=Rac1HydroWater.Mode.DRY;
    private static Object world;
    private static long epoch=-1,serial,lastTick=-1,lastAck=-1;
    private static int travelTick=-1,controlledTick=-1;
    private Rac1HydroClient() {}
    public static View view(UUID player){return VIEWS.get(player);}
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
        if(!state.access()){reset(-1,null);return;}
        if(world!=player.level()||epoch!=state.epoch()) {
            reset(state.epoch(),player.level());motion.restore(state.motion());previousWater=state.water();return;
        }
        if(state.tick()<=lastTick||state.acknowledged()<lastAck||state.acknowledged()>=serial)return;
        lastTick=state.tick();lastAck=state.acknowledged();
        while(!PENDING.isEmpty()&&PENDING.peekFirst().serial()<=lastAck)PENDING.removeFirst();
        boolean correcting=state.controlled()||motion.controlled()||controlledTick>=player.tickCount-2;
        motion.restore(state.motion());
        if(correcting&&Rac1HydroWater.observe(player).compatible()) {
            player.setPos(state.position().x(),state.position().y(),state.position().z());
            player.setDeltaMovement(Rac1HydroRuntime.vec(motion.velocity()).scale(1.0/20));
            for(var pending:PENDING){frame=pending.frame();simulate(player);}
        }
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        var minecraft=Minecraft.getInstance();
        if(!(event.getEntity() instanceof LocalPlayer player)||minecraft.player!=player)return;
        var view=VIEWS.get(player.getUUID());
        if(view==null||!view.state().access())return;
        if(epoch!=view.state().epoch()||world!=player.level())reset(view.state().epoch(),player.level());
        var keys=event.getInput();
        frame=minecraft.screen==null?new Rac1HydroMotion.Input(minecraft.options.keySprint.isDown(),
                minecraft.options.keyJump.isDown(),minecraft.options.keyShift.isDown(),
                (keys.left?1:0)-(keys.right?1:0),(keys.up?1:0)-(keys.down?1:0),player.getYRot())
                :Rac1HydroMotion.Input.RELEASED;
        long next=serial++;PENDING.addLast(new Pending(next,frame));
        while(PENDING.size()>8)PENDING.removeFirst();
        PacketDistributor.sendToServer(new Rac1HydroNetwork.Input(new Rac1HydroWire.Input(epoch,next,frame)));
        var water=Rac1HydroWater.observe(player);var probe=motion.copy();
        if(water.propulsion())for(int i=0;i<3;i++) {
            var step=probe.step(frame,Rac1HydroRuntime.vector(player.getDeltaMovement().scale(20)),
                    previousWater==Rac1HydroWater.Mode.SURFACE&&frame.dive(),water.lowAir());
            if(step.controlled()){keys.jumping=false;keys.shiftKeyDown=false;break;}
        }
    }
    /** Called only through the existing Clank travel bridge for the native local player. */
    public static boolean travel(LocalPlayer player) {
        var view=VIEWS.get(player.getUUID());
        if(view==null||!view.state().access()||view.state().epoch()!=epoch||world!=player.level())return false;
        if(travelTick==player.tickCount)return controlledTick==player.tickCount;
        travelTick=player.tickCount;
        boolean controlled=simulate(player);if(controlled)controlledTick=player.tickCount;
        return controlled;
    }
    private static boolean simulate(LocalPlayer player) {
        var water=Rac1HydroWater.observe(player);
        boolean dive=previousWater==Rac1HydroWater.Mode.SURFACE&&water.mode()==Rac1HydroWater.Mode.SUBMERGED;
        previousWater=water.mode();
        if(!water.propulsion()){motion.cancel();return false;}
        var nativeVelocity=Rac1HydroRuntime.vector(player.getDeltaMovement().scale(20));
        if(nativeVelocity.length()>32){motion.cancel();return false;}
        boolean controlled=false;
        for(int i=0;i<3;i++) {
            if(!Rac1HydroWater.observe(player).propulsion()){motion.cancel();break;}
            var step=motion.step(frame,nativeVelocity,dive&&frame.dive(),water.lowAir());
            if(!step.controlled())continue;
            if(step.displacement().length()>.54){motion.cancel();break;}
            controlled=true;var before=player.position();
            player.move(MoverType.SELF,Rac1HydroRuntime.vec(step.displacement()));
            motion.resolved(Rac1HydroRuntime.vector(player.position().subtract(before)));dive=false;
        }
        if(controlled)player.setDeltaMovement(Rac1HydroRuntime.vec(motion.velocity()).scale(1.0/20));
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
        Rac1HydroResources.update(VIEWS.values().stream().anyMatch(v->v.state().access()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIEWS.clear();reset(-1,null);Rac1HydroResources.clear();
    }
    private static void reset(long next,Object level) {
        epoch=next;world=level;serial=0;lastAck=-1;lastTick=-1;travelTick=-1;controlledTick=-1;
        PENDING.clear();motion=new Rac1HydroMotion();frame=Rac1HydroMotion.Input.RELEASED;
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
