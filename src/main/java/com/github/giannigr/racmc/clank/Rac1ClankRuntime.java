package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Authoritative once-per-tick player adapter. Packet/render/input callbacks never step it. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID)
public final class Rac1ClankRuntime {
    private static final Map<UUID,Control> CONTROLS=new HashMap<>();
    private Rac1ClankRuntime() {}
    private static final class Control {
        final ServerPlayer player;
        final Object world;
        final Rac1ClankSession session;
        final long epoch;
        final Rac1ClankInput.Stream input;
        final Rac1ClankFallEpisode fall=new Rac1ClankFallEpisode();
        final Rac1ClankLandingGrace grace=new Rac1ClankLandingGrace();
        long tick=-1,steps,creditCount;
        boolean controlled,stepping,proof,landed;
        boolean settling,heliGlide,heliReleased;
        long heliActionSerial=-1;
        long heliReleaseSerial=-1;
        double actualY,requestedY;
        Vec3 lastPosition,expectedDelta;
        Control(ServerPlayer player,Rac1ClankSession session) {
            this.player=player;world=player.level();this.session=session;epoch=session.epoch();
            input=new Rac1ClankInput.Stream(epoch);lastPosition=player.position();
        }
    }
    private static boolean teleporting(ServerPlayer player) {
        return !(player.connection instanceof Rac1ClankConnectionAccess access)
                ||access.racmc$clankAwaitingTeleport();
    }
    private static boolean eligible(Control c) {
        return c.player.level()==c.world&&c.session==Rac1ClankServer.session(c.player)
                &&c.epoch==c.session.epoch()&&c.session.equipment().worn()
                &&c.player.connection.isAcceptingMessages()
                &&Rac1ClankNativeCollision.compatible(c.player)&&c.player.hurtTime==0
                &&!teleporting(c.player);
    }
    private static boolean activeHeli(Control c) {
        return c.heliGlide&&c.fall.active()&&c.session.motion().action()==Action.GLIDE
                &&c.session.motion().airborne()
                &&c.session.equipment().selected()==Rac1ClankEquipment.Module.HELI
                &&c.session.snapshot(c.player.serverLevel().getGameTime()).actionSerial()==c.heliActionSerial;
    }
    private static void consumeZeroDistanceTouchdown(Control c) {
        c.grace.clear();
        if(activeHeli(c)) {
            c.fall.heliLandingDistance(0,true);
            cancelMotion(c);
        }
    }
    private static Control control(ServerPlayer player) {
        var session=Rac1ClankServer.session(player);
        var old=CONTROLS.get(player.getUUID());
        if(old==null||old.player!=player||old.world!=player.level()||old.epoch!=session.epoch()) {
            if(old!=null) cancel(old);
            old=new Control(player,session);CONTROLS.put(player.getUUID(),old);
        }
        return old;
    }
    public static void input(ServerPlayer player,Rac1ClankMovementWire.Input sample) {
        var c=control(player);
        boolean wasHeld=c.input.acceptedJumpHeld();
        if(eligible(c)&&c.input.offer(sample.epoch(),sample.serial(),
                player.serverLevel().getGameTime(),sample.frame())
                &&c.heliGlide&&!sample.frame().jumpHeld()) {
            if(wasHeld&&!c.heliReleased&&c.fall.active()&&!player.onGround()
                    &&c.session.motion().action()==Action.GLIDE&&c.session.motion().airborne()
                    &&c.session.equipment().selected()==Rac1ClankEquipment.Module.HELI
                    &&c.session.snapshot(player.serverLevel().getGameTime()).actionSerial()==c.heliActionSerial)
                c.grace.start(player.serverLevel().getGameTime(),c.heliActionSerial,sample.serial());
            c.heliReleased=true;c.heliReleaseSerial=sample.serial();
        }
    }
    @SubscribeEvent public static void before(PlayerTickEvent.Pre event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            stepTick(player);
            Rac1HydroRuntime.stepTick(player); // Aerial water cancellation runs first; exactly one travel owner.
        }
    }
    /** Public for exact real-ServerLevel testing; duplicate invocations in a tick are inert. */
    public static void stepTick(ServerPlayer player) {
        var c=control(player);
        long tick=player.serverLevel().getGameTime();
        if(c.tick==tick)return;
        c.tick=tick;c.controlled=false;c.proof=false;c.landed=false;c.actualY=0;
        if(!eligible(c)) {
            boolean wasActive=c.grace.active()||c.fall.active()||c.session.motion().action()!=Action.IDLE;
            cancel(c);c.lastPosition=player.position();
            if(wasActive)Rac1ClankServer.cancelEpisode(player);
            return;
        }
        if((c.grace.active()||activeHeli(c))&&player.onGround()
                &&Rac1ClankNativeCollision.observe(player,Vector.ZERO).grounded()) {
            // Native travel may land before packet-side fall settlement; settle before another jump.
            if(player.fallDistance>0)player.doCheckFallDamage(0,0,0,true);
            else consumeZeroDistanceTouchdown(c);
            if(!eligible(c)){cancel(c);return;}
        }
        // External knockback/slam takes priority over an old controlled velocity.
        if(c.expectedDelta!=null&&player.getDeltaMovement().distanceToSqr(c.expectedDelta)>.0004) {
            cancel(c);c.lastPosition=player.position();correct(player);return;
        }
        var motion=c.session.motion();
        motion.input(c.input.consume(tick).frame());
        // Re-press may restore damage protection for this still-active action, never its timers.
        if(c.heliReleased&&c.heliGlide&&c.fall.active()
                &&motion.action()==Action.GLIDE
                &&c.session.equipment().selected()==Rac1ClankEquipment.Module.HELI
                &&c.session.snapshot(tick).actionSerial()==c.heliActionSerial
                &&c.input.heldForStep(tick)&&c.input.consumed().serial()>c.heliReleaseSerial)
            c.heliReleased=false;
        if(c.input.takeJumpEdge(tick))motion.bufferJumpEdge();
        Vec3 nativeVelocity=player.position().subtract(c.lastPosition);
        if(motion.action()!=Action.IDLE)nativeVelocity=player.getDeltaMovement();
        Vector initial=Rac1ClankNativeCollision.sourceVelocity(nativeVelocity);
        Vec3 start=player.position();
        for(int substep=0;substep<3;substep++) {
            if(!eligible(c)){cancel(c);break;}
            var observed=Rac1ClankNativeCollision.observe(player,
                    motion.action()==Action.IDLE?initial:motion.velocity());
            if(motion.action()==Action.IDLE&&observed.grounded()&&player.fallDistance>0) {
                // Settle an unrelated already-grounded fall before granting a new episode.
                player.doCheckFallDamage(0,0,0,true);
                if(!eligible(c)){cancel(c);break;}
            }
            var step=motion.step(c.session.equipment(),observed);
            c.steps++;
            if(!step.controlled()) {
                if(c.fall.active()){cancelMotion(c);break;}
                continue;
            }
            // Original capsule displacement clamp is bounded; never accept a large/NaN host delta.
            if(step.displacement().length()>.43+1e-6) {cancel(c);break;}
            if(step.started())c.session.actionStarted(tick);
            if(!c.fall.active()) {
                c.fall.begin(Math.max(0,player.fallDistance));
                c.heliGlide=false;c.heliReleased=false;c.heliActionSerial=-1;c.heliReleaseSerial=-1;
            }
            if(step.started()&&step.action()==Action.GLIDE
                    &&c.session.equipment().selected()==Rac1ClankEquipment.Module.HELI) {
                c.heliGlide=true;c.heliReleased=false;c.heliReleaseSerial=-1;
                c.heliActionSerial=c.session.snapshot(tick).actionSerial();
            }
            c.controlled=true;
            c.requestedY=step.displacement().y();c.stepping=true;
            player.fallDistance=Math.max(player.fallDistance,(float)c.fall.debt());
            Vec3 before=player.position();
            Vec3 actual;
            try {
                player.move(MoverType.SELF,new Vec3(step.displacement().x(),
                        step.displacement().y(),step.displacement().z()));
                actual=player.position().subtract(before);
                if(actual.length()>.43+player.maxUpStep()+1e-5){cancel(c);break;}
                c.fall.moved(actual.y);
                // ServerPlayer.move intentionally skips falls; use the native packet-side API
                // once for the actual displacement, not a requested or client-supplied delta.
                c.settling=true;
                player.doCheckFallDamage(actual.x,actual.y,actual.z,player.onGround());
            }finally{c.stepping=false;c.settling=false;}
            if(!player.isAlive()||c.world!=player.level()||c.epoch!=c.session.epoch()){
                cancel(c);break;
            }
            boolean landed=motion.resolved(new Vector(actual.x,actual.y,actual.z),player.onGround());
            c.landed=landed;
            if(landed&&c.fall.active())c.fall.landingDistance(Math.max(0,player.fallDistance));
            c.proof=true;
            if(landed)break;
        }
        c.actualY=player.getY()-start.y;
        c.lastPosition=player.position();
        if(c.controlled&&(eligible(c)||(c.landed&&player.isAlive()
                &&c.world==player.level()&&c.epoch==c.session.epoch()))) {
            Vec3 delta=Rac1ClankNativeCollision.minecraftDelta(motion.velocity());
            player.setDeltaMovement(delta);c.expectedDelta=delta;
            // Only after actual collision-checked server steps, never per client packet.
            player.connection.resetPosition();
        } else {c.expectedDelta=null;c.controlled=false;c.proof=false;}
        Rac1ClankServer.broadcast(player);
        correct(player);
    }
    private static void cancel(Control c) {
        c.grace.clear();
        cancelMotion(c);
    }
    /** Normal end of glide hands movement/debt back to vanilla without ending the airborne grace. */
    private static void cancelMotion(Control c) {
        if(c.fall.active())c.player.fallDistance=(float)c.fall.cancelDistance(
                Math.max(0,c.player.fallDistance));
        c.session.motion().cancel();c.expectedDelta=null;c.proof=false;
        c.heliGlide=false;c.heliReleased=false;c.heliActionSerial=-1;c.heliReleaseSerial=-1;
        c.session.motion().discardBufferedInput();
    }
    @SubscribeEvent public static void after(PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            var c=CONTROLS.get(player.getUUID());
            if(c!=null&&c.player==player&&(c.grace.active()||activeHeli(c))) {
                if(!eligible(c))c.grace.clear();
                else if(player.onGround()&&player.fallDistance<=0
                        &&Rac1ClankNativeCollision.observe(player,Vector.ZERO).grounded())
                    consumeZeroDistanceTouchdown(c);
            }
        }
    }
    public static boolean ownsTravel(ServerPlayer player) {
        if(Rac1HydroRuntime.ownsTravel(player))return true;
        var c=CONTROLS.get(player.getUUID());
        return c!=null&&c.player==player&&c.tick==player.serverLevel().getGameTime()
                &&c.controlled&&(eligible(c)||(c.landed&&c.world==player.level()
                &&c.epoch==c.session.epoch()&&c.session.equipment().worn()
                &&Rac1ClankNativeCollision.compatible(player)&&!teleporting(player)));
    }
    public static ServerboundMovePlayerPacket authoritativePacket(ServerPlayer player,
            ServerboundMovePlayerPacket packet) {
        if(Rac1HydroRuntime.ownsTravel(player))return Rac1HydroRuntime.authoritativePacket(player,packet);
        var c=CONTROLS.get(player.getUUID());
        if(!ownsTravel(player)||c==null||!eligible(c))return packet;
        return new ServerboundMovePlayerPacket.PosRot(player.getX(),player.getY(),player.getZ(),
                packet.getYRot(player.getYRot()),packet.getXRot(player.getXRot()),player.onGround());
    }
    /** Pure predicate plus runtime identity/epoch/world/eligibility checks. No counter resets. */
    public static boolean descentCredit(boolean sameTick,boolean eligible,boolean collisionProof,
            boolean controlled,boolean onGround,double actualY) {
        return sameTick&&eligible&&collisionProof&&controlled&&!onGround
                &&Double.isFinite(actualY)&&actualY<=-.03125;
    }
    public static boolean floatingCredit(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        boolean credit=c!=null&&c.player==player&&descentCredit(
                c.tick==player.serverLevel().getGameTime(),eligible(c),c.proof,c.controlled,
                player.onGround(),c.actualY);
        if(credit)c.creditCount++;
        return credit;
    }
    @SubscribeEvent public static void landing(LivingFallEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var c=CONTROLS.get(player.getUUID());
        if(c!=null&&c.player==player&&(c.grace.active()||activeHeli(c))&&player.onGround()) {
            boolean nativeGround=Rac1ClankNativeCollision.observe(player,Vector.ZERO).grounded();
            if(!eligible(c)||!nativeGround
                    ||c.session.equipment().selected()!=Rac1ClankEquipment.Module.HELI)c.grace.clear();
            else {
                boolean protectedLanding=activeHeli(c);
                protectedLanding=c.grace.land(player.serverLevel().getGameTime())||protectedLanding;
                if(protectedLanding) {
                    if(c.fall.active())c.fall.heliLandingDistance(Math.max(0,event.getDistance()),true);
                    c.heliGlide=false; // First touchdown consumes this admitted episode on either route.
                    if(!c.settling)cancelMotion(c);
                    event.setDistance(0);
                    return;
                }
            }
        }
        if(c==null||c.player!=player||!c.settling||!c.controlled||!eligible(c)
                ||c.tick!=player.serverLevel().getGameTime()||!player.onGround()
                ||c.requestedY>0||!c.session.motion().airborne()||!c.fall.active())return;
        double distance=Math.max(0,event.getDistance());
        if(c.heliGlide)event.setDistance((float)c.fall.heliLandingDistance(distance,false));
        else event.setDistance((float)c.fall.landingDistance(distance));
    }
    @SubscribeEvent public static void knockback(LivingKnockBackEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            Rac1HydroRuntime.invalidate(player);
            var c=CONTROLS.get(player.getUUID());
            if(c!=null&&(c.grace.active()||c.fall.active()||c.session.motion().action()!=Action.IDLE))
                Rac1ClankServer.cancelEpisode(player);
        }
    }
    public static void invalidate(ServerPlayer player) {
        Rac1HydroRuntime.invalidate(player);
        var c=CONTROLS.remove(player.getUUID());
        if(c!=null)cancel(c);
    }
    /** Called on the native connection teleport path, including same-world teleports and corrections. */
    public static void teleport(ServerPlayer player) {
        Rac1HydroRuntime.invalidate(player);
        var c=CONTROLS.get(player.getUUID());
        if(c!=null&&(c.grace.active()||c.fall.active()||c.session.motion().action()!=Action.IDLE))
            Rac1ClankServer.cancelEpisode(player);
    }
    public record GraceDiagnostics(long releaseTick,long releaseSerial,long actionSerial,boolean covers) {}
    public static GraceDiagnostics graceDiagnostics(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        return c==null||c.player!=player?new GraceDiagnostics(-1,-1,-1,false)
                :new GraceDiagnostics(c.grace.releaseTick(),c.grace.inputSerial(),c.grace.actionSerial(),
                        c.grace.covers(player.serverLevel().getGameTime()));
    }
    static void clear(){CONTROLS.clear();}
    static void correct(ServerPlayer player) {
        var session=Rac1ClankServer.session(player);
        if(session.motion().action()==Action.IDLE)
            session.motion().nativeIdleVelocity(Rac1ClankNativeCollision.sourceVelocity(player.getDeltaMovement()));
        var c=CONTROLS.get(player.getUUID());
        PacketDistributor.sendToPlayer(player,new Rac1ClankNetwork.Correction(
                new Rac1ClankMovementWire.Correction(player.getUUID(),session.epoch(),
                        c==null||c.epoch!=session.epoch()?-1:c.input.acknowledgedSerial(),
                        player.serverLevel().getGameTime(),
                        new Vector(player.getX(),player.getY(),player.getZ()),session.motion().state())));
    }
    public record Diagnostics(long tick,long sourceSteps,double actualY,boolean controlled,
                              boolean proof,long credits,boolean fallActive) {}
    public static Diagnostics diagnostics(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        return c==null?new Diagnostics(-1,0,0,false,false,0,false)
                 :new Diagnostics(c.tick,c.steps,c.actualY,c.controlled,c.proof,c.creditCount,c.fall.active());
    }
    public record InputDiagnostics(long receiptTick,long acceptedSerial,long controlTick,
                                   long consumedSerial,long consumedReceiptTick,boolean fresh,
                                   boolean heldProof,boolean released,long actionSerial,long releaseSerial) {}
    public static InputDiagnostics inputDiagnostics(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        return c==null?new InputDiagnostics(-1,-1,-1,-1,-1,false,false,false,-1,-1)
                :new InputDiagnostics(c.input.receivedTick(),c.input.acknowledgedSerial(),c.tick,
                        c.input.consumed().serial(),c.input.consumed().receiptTick(),
                        c.input.consumed().fresh(),c.input.heldForStep(c.tick),
                        c.heliReleased,c.heliActionSerial,c.heliReleaseSerial);
    }
}
