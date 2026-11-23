package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Hydro is a session-only test capability; never writes durable equipment/entitlement. */
public final class Rac1HydroRuntime {
    private static final Map<UUID,Control> CONTROLS=new HashMap<>();
    private static long nextEpoch;
    private Rac1HydroRuntime() {}
    private static long epoch(){return nextEpoch=Math.incrementExact(nextEpoch);}
    private static final class Control {
        final ServerPlayer player;
        Object world;
        long epoch,clankEpoch,tick=-1,steps;
        boolean wet,controlled,handoff,disabled;
        Rac1HydroWater.Mode water=Rac1HydroWater.Mode.DRY;
        Rac1HydroWire.Stream input;
        Rac1HydroBoost boost=Rac1HydroBoost.BLOCKED;
        final Rac1HydroMotion motion=new Rac1HydroMotion();
        final Rac1HydroVisual visual=new Rac1HydroVisual();
        Vec3 expected;
        Control(ServerPlayer player){this.player=player;reset();}
        void reset() {
            world=player.level();clankEpoch=Rac1ClankServer.session(player).epoch();epoch=epoch();
            tick=-1;wet=false;controlled=false;handoff=false;water=Rac1HydroWater.Mode.DRY;expected=null;
            boost=Rac1HydroBoost.BLOCKED;
            input=new Rac1HydroWire.Stream(epoch);motion.restore(new Rac1HydroMotion().state());visual.clear();
        }
    }
    private static boolean testAccess(ServerPlayer p) {
        return p.isAlive()&&!p.isRemoved()&&p.connection.isAcceptingMessages()
                &&Rac1ClankServer.session(p).hydroAvailable(p.isCreative());
    }
    private static boolean accessValid(Control c) {return !c.disabled&&testAccess(c.player);}
    private static Control control(ServerPlayer player) {
        if(!testAccess(player)){forget(player);return null;}
        var c=CONTROLS.get(player.getUUID());
        if(c==null||c.player!=player) {
            c=new Control(player);CONTROLS.put(player.getUUID(),c);
        }
        return c;
    }
    private static boolean teleporting(ServerPlayer p) {
        return !(p.connection instanceof Rac1ClankConnectionAccess access)||access.racmc$clankAwaitingTeleport();
    }
    public static boolean configure(CommandSourceStack source,ServerPlayer player,boolean on) {
        if(!source.hasPermission(2)||!player.isAlive()||player.isRemoved())return false;
        var c=control(player);if(c==null)return false;
        if(c.disabled==on){c.disabled=!on;c.reset();}
        broadcast(player);
        return true;
    }
    /** Equipment/teleport/dimension cancels the transient episode, not session test access. */
    public static void invalidate(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        if(c!=null&&c.player==player){c.reset();broadcast(player);}
    }
    public static void equipmentChanged(ServerPlayer player) {
        var c=control(player);if(c!=null){c.reset();broadcast(player);}
    }
    public static void forget(ServerPlayer player) {
        var removed=CONTROLS.remove(player.getUUID());
        if(removed!=null)PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Rac1HydroNetwork.Snapshot(off(player)));
    }
    public static void clear(){CONTROLS.clear();}
    public static void input(ServerPlayer player,Rac1HydroWire.Input sample) {
        var c=CONTROLS.get(player.getUUID());
        if(c!=null&&c.player==player&&accessValid(c)&&c.world==player.level()
                &&c.clankEpoch==Rac1ClankServer.session(player).epoch()&&!teleporting(player))
            c.input.offer(sample,player.serverLevel().getGameTime());
    }
    public static void stepTick(ServerPlayer player) {
        var c=control(player);if(c==null||c.disabled)return;
        if(c.world!=player.level()||c.clankEpoch!=Rac1ClankServer.session(player).epoch())c.reset();
        long now=player.serverLevel().getGameTime();if(c.tick==now)return;c.tick=now;c.controlled=false;c.handoff=false;
        var water=Rac1HydroWater.observe(player);
        boolean wet=water.compatible()&&!teleporting(player)
                &&(water.mode()==Rac1HydroWater.Mode.SURFACE||water.mode()==Rac1HydroWater.Mode.SUBMERGED);
        boolean diveEntry=c.water==Rac1HydroWater.Mode.SURFACE&&water.mode()==Rac1HydroWater.Mode.SUBMERGED;
        c.wet=wet;c.water=water.mode();c.visual.target(wet);
        boolean eligible=wet&&water.propulsion();
        var samples=c.input.consume(now);
        if(!eligible||!c.input.live(now))c.boost=Rac1HydroBoost.BLOCKED;
        else for(var sample:samples)c.boost=c.boost.sample(sample,true);
        var keys=samples.isEmpty()?Rac1HydroBoost.Controls.INACTIVE:samples.getLast();
        var frame=c.boost.drive(keys);
        if(Rac1HydroTrace.LOG.enabled()) {
            String decision=c.epoch+":"+c.boost+":"+keys.sprintDown()+":"+frame.thrust()+":"+keys.forward()+":"+frame.rise()+":"+frame.dive()
                    +":"+water.mode()+":"+water.propulsion()+":"+water.lowAir()+":"+player.getPose()+":"+player.isSwimming();
            Rac1HydroTrace.LOG.transition("server-input:"+player.getUUID(),decision,()->"tick="+now
                    +" boost="+c.boost+" physicalSprint="+keys.sprintDown()+" acceptedThrust="+frame.thrust()
                    +" forward="+keys.forward()+" rise="+frame.rise()+" dive="+frame.dive()
                    +" water="+water.mode()+" propulsion="+water.propulsion()+" lowAir="+water.lowAir()
                    +" air="+player.getAirSupply()+" lookPitch="+player.getXRot()+" pose="+player.getPose()
                    +" swimming="+player.isSwimming()+" vanillaSprint="+player.isSprinting());
        }
        if(!eligible) {
            c.motion.cancel();c.expected=null;
        }else if(c.expected!=null&&player.getDeltaMovement().distanceToSqr(c.expected)>.0025) {
            c.motion.cancel();c.expected=null; // Native external force takes priority for this tick.
        }else {
            Vector nativeVelocity=vector(player.getDeltaMovement().scale(20));
            if(nativeVelocity.length()>32){c.motion.cancel();c.expected=null;}
            else for(int i=0;i<3;i++) {
                long episode=c.epoch;
                var current=Rac1HydroWater.observe(player);
                if(!current.propulsion()){
                    c.boost=Rac1HydroBoost.BLOCKED;c.motion.cancel();c.handoff=c.controlled&&current.compatible();break;
                }
                var step=c.motion.step(frame,nativeVelocity,diveEntry&&frame.dive(),current.lowAir(),player.getXRot());
                c.steps++;
                if(!step.controlled())continue;
                if(step.displacement().length()>.54){c.motion.cancel();break;}
                Vec3 before=player.position();
                player.move(MoverType.SELF,vec(step.displacement()));
                Vector actual=vector(player.position().subtract(before));
                if(!sameEpisode(c,episode))return;
                if(actual.length()>.54+player.maxUpStep()){c.motion.cancel();break;}
                c.motion.resolved(actual);
                player.doCheckFallDamage(actual.x(),actual.y(),actual.z(),player.onGround());
                if(!sameEpisode(c,episode))return;
                c.controlled=true;
                diveEntry=false;
            }
            if(c.controlled) {
                player.setDeltaMovement(vec(c.motion.velocity()).scale(1.0/20));
                c.expected=player.getDeltaMovement();player.connection.resetPosition();
                // Crossing a surface/current boundary on the final substep still consumed travel
                // this tick. Never run a second native move after that collision-checked handoff.
                if(!Rac1HydroWater.observe(player).propulsion()) {
                    c.handoff=true;c.boost=Rac1HydroBoost.BLOCKED;c.motion.cancel();
                }
            }else c.expected=null;
        }
        if(!c.motion.controlled()) {
            var nativeVelocity=vector(player.getDeltaMovement().scale(20));
            if(nativeVelocity.length()<=32)c.motion.nativeVelocity(nativeVelocity);
        }
        for(int i=0;i<3;i++)c.visual.step();
        broadcast(player);
    }
    /** Native block/fall callbacks can invalidate equipment, teleport or kill during a substep. */
    private static boolean sameEpisode(Control c,long episode) {
        if(CONTROLS.get(c.player.getUUID())==c&&c.epoch==episode&&c.world==c.player.level()
                &&c.clankEpoch==Rac1ClankServer.session(c.player).epoch()&&accessValid(c))return true;
        c.motion.cancel();c.controlled=false;c.expected=null;
        return false;
    }
    public static boolean ownsTravel(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());
        return c!=null&&c.player==player&&c.controlled&&c.tick==player.serverLevel().getGameTime()
                &&c.world==player.level()&&c.clankEpoch==Rac1ClankServer.session(player).epoch()
                &&accessValid(c)&&!teleporting(player)&&Rac1HydroWater.observe(player).compatible()
                &&(c.handoff||Rac1HydroWater.observe(player).propulsion());
    }
    public static ServerboundMovePlayerPacket authoritativePacket(ServerPlayer p,ServerboundMovePlayerPacket raw) {
        if(!ownsTravel(p))return raw;
        return new ServerboundMovePlayerPacket.PosRot(p.getX(),p.getY(),p.getZ(),
                raw.getYRot(p.getYRot()),raw.getXRot(p.getXRot()),p.onGround());
    }
    public static Rac1HydroWire.Snapshot snapshot(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());if(c==null||c.player!=player||c.disabled)return off(player);
        return new Rac1HydroWire.Snapshot(player.getUUID(),c.epoch,c.clankEpoch,player.serverLevel().getGameTime(),
                c.input.acknowledged(),true,c.wet,c.controlled,Rac1ClankServer.session(player).equipment().selected(),
                c.water,c.visual.phase(),c.visual.time(),vector(player.position()),c.motion.state(),c.boost);
    }
    private static Rac1HydroWire.Snapshot off(ServerPlayer player) {
        return new Rac1HydroWire.Snapshot(player.getUUID(),epoch(),Rac1ClankServer.session(player).epoch(),
                player.serverLevel().getGameTime(),-1,false,false,false,
                Rac1ClankServer.session(player).equipment().selected(),Rac1HydroWater.Mode.DRY,
                Rac1HydroVisual.Phase.DRY,0,vector(player.position()),new Rac1HydroMotion().state(),Rac1HydroBoost.BLOCKED);
    }
    private static void broadcast(ServerPlayer player) {
        var state=snapshot(player);
        Rac1HydroTrace.snapshot("server-state:"+player.getUUID(),state,player.getPose()+"/swimming="+player.isSwimming()
                +"/vanillaSprint="+player.isSprinting());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new Rac1HydroNetwork.Snapshot(state));
    }
    public static void tracking(ServerPlayer observer,ServerPlayer target) {
        // Include an off tombstone: an observer may have stopped tracking during off/cleanup.
        PacketDistributor.sendToPlayer(observer,new Rac1HydroNetwork.Snapshot(snapshot(target)));
    }
    public static long sourceSteps(ServerPlayer player) {
        var c=CONTROLS.get(player.getUUID());return c==null?0:c.steps;
    }
    public static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}
    public static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
}
