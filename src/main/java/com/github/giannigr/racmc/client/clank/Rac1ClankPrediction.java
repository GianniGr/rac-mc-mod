package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native-remapped intent collection and <=8-frame collision-checked local prediction/replay. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ClankPrediction {
    private record Pending(long serial,Rac1ClankInput frame) {}
    private static final ArrayDeque<Pending> PENDING=new ArrayDeque<>();
    private static Rac1ClankMotion motion=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
    private static long epoch=-1,serial,lastServerTick=-1,lastAcknowledged=-1;
    private static int travelTick=-1,controlledTick=-1;
    private static Rac1ClankInput frame=Rac1ClankInput.RELEASED;
    private static Vector initial=Vector.ZERO;
    private static Object world;
    private Rac1ClankPrediction() {}
    public static void install() {
        Rac1ClankTravel.prediction(Rac1ClankPrediction::travel);
        Rac1ClankNetwork.movementReceiver(Rac1ClankPrediction::correct);
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        var minecraft=Minecraft.getInstance();
        if(!(event.getEntity() instanceof LocalPlayer player)||player!=minecraft.player)return;
        var view=Rac1ClankClient.VIEWS.get(player.getUUID());
        if(view==null||!view.equipment().worn()){clear();return;}
        if(epoch!=view.epoch()||world!=player.level())reset(view.epoch(),player.level());
        var nativeInput=event.getInput();
        frame=minecraft.screen==null?Rac1ClankInput.digital(
                minecraft.options.keyJump.isDown(),nativeInput.jumping,nativeInput.shiftKeyDown,
                nativeInput.left,nativeInput.right,nativeInput.up,nativeInput.down,player.getYRot())
                :Rac1ClankInput.RELEASED;
        initial=Rac1ClankNativeCollision.sourceVelocity(player.getDeltaMovement());
        motion.input(frame);
        long next=serial++;
        PENDING.addLast(new Pending(next,frame));
        while(PENDING.size()>8)PENDING.removeFirst();
        PacketDistributor.sendToServer(new Rac1ClankNetwork.HeldInput(
                new Rac1ClankMovementWire.Input(epoch,next,frame)));
        // Probe a detached clock only: no extra movement/physics steps or published source time.
        var preview=motion.copy();
        var observation=Rac1ClankNativeCollision.observe(player,initial);
        boolean admitted=false;
        if(Rac1ClankNativeCollision.compatible(player)) {
            for(int i=0;i<3;i++)if(preview.step(view.equipment(),observation).controlled()){
                admitted=true;break;
            }
        }
        if(admitted)nativeInput.jumping=false;
        // Denied/waiting/ordinary jumps retain native input and native travel.
    }
    private static boolean travel(Player entity) {
        var player=Minecraft.getInstance().player;
        if(entity!=player||player==null)return false;
        var view=Rac1ClankClient.VIEWS.get(player.getUUID());
        if(view==null||!view.equipment().worn()||epoch!=view.epoch()
                ||world!=player.level()||!Rac1ClankNativeCollision.compatible(player)){
            motion.cancel();return false;
        }
        if(travelTick==player.tickCount)return controlledTick==player.tickCount;
        travelTick=player.tickCount;
        boolean moved=simulate(player,view.equipment());
        if(moved)controlledTick=player.tickCount;
        return moved;
    }
    private static boolean simulate(LocalPlayer player,Rac1ClankEquipment equipment) {
        boolean controlled=false;
        for(int i=0;i<3;i++) {
            var observation=Rac1ClankNativeCollision.observe(player,
                    motion.action()==Action.IDLE?initial:motion.velocity());
            var step=motion.step(equipment,observation);
            if(!step.controlled())continue;
            if(step.displacement().length()>.43+1e-6){motion.cancel();break;}
            controlled=true;
            var before=player.position();
            player.move(MoverType.SELF,new Vec3(step.displacement().x(),
                    step.displacement().y(),step.displacement().z()));
            var actual=player.position().subtract(before);
            if(motion.resolved(new Vector(actual.x,actual.y,actual.z),player.onGround()))break;
        }
        if(controlled)player.setDeltaMovement(Rac1ClankNativeCollision.minecraftDelta(motion.velocity()));
        return controlled;
    }
    private static void correct(Rac1ClankMovementWire.Correction update) {
        var player=Minecraft.getInstance().player;
        if(player==null||!player.getUUID().equals(update.player()))return;
        var view=Rac1ClankClient.VIEWS.get(player.getUUID());
        if(view==null||update.epoch()!=view.epoch()||update.epoch()<epoch)return;
        if(epoch!=update.epoch()||world!=player.level())reset(update.epoch(),player.level());
        if(update.serverTick()<=lastServerTick||update.acknowledged()<lastAcknowledged
                ||update.acknowledged()>=serial)return;
        lastServerTick=update.serverTick();
        lastAcknowledged=update.acknowledged();
        while(!PENDING.isEmpty()&&PENDING.peekFirst().serial()<=update.acknowledged())PENDING.removeFirst();
        boolean correcting=motion.action()!=Action.IDLE||update.state().action()!=Action.IDLE
                ||controlledTick>=player.tickCount-2;
        motion.restore(update.state());
        if(correcting&&Rac1ClankNativeCollision.compatible(player)) {
            player.setPos(update.position().x(),update.position().y(),update.position().z());
            player.setDeltaMovement(Rac1ClankNativeCollision.minecraftDelta(motion.velocity()));
            for(var pending:PENDING) {
                motion.input(pending.frame());
                initial=Rac1ClankNativeCollision.sourceVelocity(player.getDeltaMovement());
                simulate(player,view.equipment());
            }
        }
        // Ordinary inactive vanilla movement is never replayed or repositioned by Clank.
    }
    private static void reset(long nextEpoch,Object nextWorld) {
        epoch=nextEpoch;world=nextWorld;serial=0;lastServerTick=lastAcknowledged=-1;
        travelTick=controlledTick=-1;PENDING.clear();
        motion=new Rac1ClankMotion(Rac1ClankClock.Profile.ORIGINAL_60);
    }
    public static void clear(){reset(-1,null);}
}
