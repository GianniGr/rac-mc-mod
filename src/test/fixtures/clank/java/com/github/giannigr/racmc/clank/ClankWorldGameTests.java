package com.github.giannigr.racmc.clank;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.TickTask;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/**
 * Opt-in real ServerLevel/ServerPlayer/native-travel/packet-mixin tests.
 * Connected packet sink, NOT a genuine network/GPU client. Never in the normal JAR.
 */
@GameTestHolder("racmc_clank_moves")
@PrefixGameTestTemplate(false)
public final class ClankWorldGameTests {
    private ClankWorldGameTests() {}
    private static final class Sink extends ServerGamePacketListenerImpl {
        boolean open=true;
        int disconnects;
        Sink(ServerLevel level,ServerPlayer player,GameProfile profile) {
            super(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player,
                    CommonListenerCookie.createInitial(profile,false));
        }
        @Override public void send(Packet<?> packet) {}
        @Override public boolean isAcceptingMessages(){return open;}
        @Override public void disconnect(Component reason){open=false;disconnects++;}
    }
    private static Vec3 platform(GameTestHelper h) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                .set(false,h.getLevel().getServer());
        h.getLevel().getGameRules().getRule(GameRules.RULE_FALL_DAMAGE)
                .set(true,h.getLevel().getServer());
        var base=h.absolutePos(new BlockPos(3,2,3));
        for(int x=-2;x<=22;x++)for(int z=-2;z<=22;z++) {
            h.getLevel().setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            // Include capsule headroom above the highest spawn, not just its feet.
            for(int y=0;y<13;y++)h.getLevel().setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        return Vec3.atBottomCenterOf(base);
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 at) {
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"clank-probe");
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        player.connection=new Sink(level,player,profile);player.setPos(at);
        player.setGameMode(GameType.SURVIVAL);player.setOnGround(true);
        level.addNewPlayer(player);player.connection.resetPosition();
        // Exercise native age progression; never disable production spawn protection.
        for(int i=0;i<65;i++){player.tick();player.doTick();}
        h.assertTrue(player.isAlive()&&player.getHealth()==20,"Fixture aging damaged player");
        player.setPos(at);player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);
        player.fallDistance=0;player.connection.resetPosition();
        h.assertTrue(level.noCollision(player,player.getBoundingBox().deflate(1e-6)),
                "Fixture spawn capsule intersects template roof/geometry");
        return player;
    }
    private static void equip(GameTestHelper h,ServerPlayer p,String module) {
        try {
            int result=p.server.getCommands().getDispatcher().execute("clanktest @s "+module,
                    p.createCommandSourceStack().withPermission(2));
            h.assertTrue(result==1,"Actual operator command failed");
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException bad){
            throw new AssertionError("Actual command parsing failed",bad);
        }
    }
    private static void frame(ServerPlayer p,long serial,boolean jump,boolean crouch,boolean forward) {
        var state=Rac1ClankServer.session(p);
        Rac1ClankRuntime.input(p,new Rac1ClankMovementWire.Input(state.epoch(),serial,
                Rac1ClankInput.digital(jump,jump,crouch,false,false,forward,false,0)));
        p.setShiftKeyDown(crouch);
        p.doTick(); // real Player.tick -> PlayerTickEvent.Pre -> native travel mixin
    }
    private static void done(GameTestHelper h,ServerPlayer... players) {
        for(var p:players){Rac1ClankRuntime.invalidate(p);p.discard();}
        h.succeed();
    }
    /** Native packet-side fall processing for fixture-owned ordinary travel only. */
    private static void fallFrame(ServerPlayer p,long serial,boolean jump) {
        Vec3 before=p.position();
        frame(p,serial,jump,false,false);
        if(!Rac1ClankRuntime.diagnostics(p).controlled()) {
            Vec3 actual=p.position().subtract(before);
            p.doCheckFallDamage(actual.x,actual.y,actual.z,p.onGround());
        }
    }
    private static void proof(String label,ServerPlayer p) {
        System.out.println("CLANK_PROOF "+label+" owns="+Rac1ClankRuntime.ownsTravel(p)
                +" health="+p.getHealth()+" hurt="+p.hurtTime+" compatible="
                +Rac1ClankNativeCollision.compatible(p)+" teleport="
                +((Rac1ClankConnectionAccess)p.connection).racmc$clankAwaitingTeleport()
                +" diag="+Rac1ClankRuntime.diagnostics(p)+" pos="+p.position());
    }
    private static final class Falls {
        final ServerPlayer[] players;
        final int[] count;
        final float[] distance;
        final java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingFallEvent> listener;
        Falls(ServerPlayer... players) {
            this.players=players;count=new int[players.length];distance=new float[players.length];
            listener=event->{
                for(int i=0;i<players.length;i++)if(event.getEntity()==players[i]) {
                    count[i]++;distance[i]=event.getDistance();
                    System.out.println("CLANK_FALL index="+i+" distance="+distance[i]+" count="+count[i]);
                    System.out.println("CLANK_FALL_INPUT "+Rac1ClankRuntime.inputDiagnostics(players[i]));
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.bus.api.EventPriority.LOWEST,listener);
        }
        void close(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
        void damaged(GameTestHelper h,int i,double min,double max) {
            h.assertTrue(count[i]==1&&distance[i]>=min&&distance[i]<=max,
                    "Native fall distance/count index="+i+" distance="+distance[i]+" count="+count[i]);
            h.assertTrue(players[i].getHealth()==20-(float)Math.ceil(distance[i]-3)
                    &&players[i].getHealth()<20,"Native health disagrees with fall event index="+i
                    +" health="+players[i].getHealth()+" distance="+distance[i]);
        }
    }

    private static void queueInput(ServerPlayer p,long serial,boolean held) {
        var packet=new Rac1ClankMovementWire.Input(Rac1ClankServer.session(p).epoch(),serial,
                Rac1ClankInput.digital(held,held,false,false,false,false,false,0));
        p.server.tell(new TickTask(p.server.getTickCount(),()->{
            Rac1ClankRuntime.input(p,packet);
            System.out.println("CLANK_QUEUE_RECEIPT player="+p.getUUID()+" offered="+serial
                    +" held="+held+" "+Rac1ClankRuntime.inputDiagnostics(p));
        }));
    }
    private static void tickWithoutInput(ServerPlayer p) {
        Vec3 before=p.position();p.doTick();
        if(!Rac1ClankRuntime.diagnostics(p).controlled()) {
            Vec3 actual=p.position().subtract(before);
            p.doCheckFallDamage(actual.x,actual.y,actual.z,p.onGround());
        }
    }
    @GameTest(template="empty",batch="clank_11_queued_boundary",timeoutTicks=60)
    public static void queuedPriorWorldTickInputMustBelongToExactlyOneAuthoritativeLandingStep(GameTestHelper h) {
        Vec3 base=platform(h);
        var held=player(h,base.add(0,.8,8));var released=player(h,base.add(8,.8,8));
        var stale=player(h,base.add(16,.8,8));
        var players=new ServerPlayer[]{held,released,stale};
        for(var p:players){equip(h,p,"heli");p.fallDistance=12;}
        var falls=new Falls(players);
        for(int tick=1;tick<=35;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t==1)for(var p:players)queueInput(p,1,true);
                else {
                    if(t==2)for(var p:players) {
                        var input=Rac1ClankRuntime.inputDiagnostics(p);
                        h.assertTrue(input.acceptedSerial()==1&&input.receiptTick()
                                        ==h.getLevel().getGameTime()-1,
                                "Native task queue did not exercise the prior-world-tick receipt boundary: "+input);
                    }
                    for(var p:players) {
                        System.out.println("CLANK_QUEUE_CONSUME_BEFORE world="+h.getLevel().getGameTime()
                                +" player="+p.getUUID()+" "+Rac1ClankRuntime.inputDiagnostics(p));
                        tickWithoutInput(p);
                        System.out.println("CLANK_QUEUE_CONSUME_AFTER "+Rac1ClankRuntime.inputDiagnostics(p));
                    }
                    if(t==5)h.assertTrue(Rac1ClankServer.session(released).motion().action()
                            ==Rac1ClankCapability.Action.GLIDE,"Accepted release changed minimum motion timer");
                    if(t<20) {
                        queueInput(held,t,true);
                        queueInput(released,t,t<3);
                        queueInput(stale,1,true); // replay never owns another consumed step
                    }
                }
                if(t==35) {
                    h.assertTrue(falls.count[0]==1&&falls.distance[0]==0&&held.getHealth()==20,
                            "Queued fresh held Heli landing lost temporal proof");
                    falls.damaged(h,1,12.79,12.81);
                    falls.damaged(h,2,12.7,12.81);
                    falls.close();done(h,players);
                }
            });
        }
    }
    @GameTest(template="empty",batch="clank_12_release_at_landing",timeoutTicks=60)
    public static void acceptedReleaseAfterConsumptionRevokesLandingEvenIfNewerHeldArrives(GameTestHelper h) {
        Vec3 base=platform(h);var p=player(h,base.add(0,.8,8));equip(h,p,"heli");
        p.fallDistance=12;var falls=new Falls(p);boolean[] revoked={false};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingFallEvent> release=event->{
            if(event.getEntity()!=p)return;
            h.assertTrue(Rac1ClankRuntime.inputDiagnostics(p).heldProof(),
                    "No consumed held proof to revoke at landing");
            long epoch=Rac1ClankServer.session(p).epoch();
            Rac1ClankRuntime.input(p,new Rac1ClankMovementWire.Input(epoch,10000,Rac1ClankInput.RELEASED));
            Rac1ClankRuntime.input(p,new Rac1ClankMovementWire.Input(epoch,10001,
                    Rac1ClankInput.digital(true,true,false,false,false,false,false,0)));
            var proof=Rac1ClankRuntime.inputDiagnostics(p);
            h.assertTrue(proof.acceptedSerial()==10001&&proof.released()&&!proof.heldProof(),
                    "Accepted release was overwritten by a later unconsumed held sample: "+proof);
            revoked[0]=true;
            System.out.println("CLANK_LATE_RELEASE "+proof);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGH,release);
        for(int tick=1;tick<=35;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t==1)queueInput(p,1,true);
                else {tickWithoutInput(p);if(t<20)queueInput(p,t,true);}
                if(t==35) {
                    h.assertTrue(revoked[0],"Release did not run inside the native landing boundary");
                    falls.damaged(h,0,12.79,12.81);falls.close();
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(release);
                    done(h,p);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_07_heli_policy",timeoutTicks=80)
    public static void heliLandingRequiresHeldSameTickAndSameEpisodeWithNativeDamageControls(GameTestHelper h) {
        Vec3 base=platform(h);
        var held=player(h,base.add(0,.8,8));
        var released=player(h,base.add(4,.8,8));
        var stale=player(h,base.add(8,.8,8));
        var toggled=player(h,base.add(12,.8,8));
        var naked=player(h,base.add(16,.8,8));
        var equipped=player(h,base.add(20,.8,8));
        var thruster=player(h,base.add(0,.8,16));
        var interrupted=player(h,base.add(4,2,16));
        var actors=new ServerPlayer[]{held,released,stale,toggled,naked,equipped,thruster,interrupted};
        for(var p:actors) {
            if(p!=naked)equip(h,p,p==thruster?"thruster":"heli");
            p.fallDistance=12;
        }
        var falls=new Falls(actors);
        long oldEpoch=Rac1ClankServer.session(toggled).epoch();
        for(int tick=1;tick<=55;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t==2) {
                    h.assertTrue(Rac1ClankServer.session(released).motion().action()
                            ==Rac1ClankCapability.Action.GLIDE,"Release case never admitted glide");
                    equip(h,toggled,"unequip");equip(h,toggled,"heli");
                }
                fallFrame(held,t,true);
                fallFrame(released,t,t==1);
                if(t==1)fallFrame(stale,t,true);
                else {
                    // Replays cannot refresh a same-tick hold, even while motion uses its grace.
                    var state=Rac1ClankServer.session(stale);
                    Rac1ClankRuntime.input(stale,new Rac1ClankMovementWire.Input(state.epoch(),1,
                            Rac1ClankInput.digital(true,true,false,false,false,false,false,0)));
                    Vec3 before=stale.position();stale.doTick();
                    if(!Rac1ClankRuntime.diagnostics(stale).controlled()) {
                        Vec3 actual=stale.position().subtract(before);
                        stale.doCheckFallDamage(actual.x,actual.y,actual.z,stale.onGround());
                    }
                }
                Rac1ClankRuntime.input(toggled,new Rac1ClankMovementWire.Input(oldEpoch,999+t,
                        Rac1ClankInput.digital(true,true,false,false,false,false,false,0)));
                fallFrame(toggled,t,t==1);
                fallFrame(naked,t,false);fallFrame(equipped,t,false);
                fallFrame(thruster,t,true);
                fallFrame(interrupted,t,t==1);
                if(t==3)h.assertTrue(Rac1ClankServer.session(released).motion().action()
                        ==Rac1ClankCapability.Action.GLIDE,
                        "Damage release changed original minimum glide motion timer");
                if(t==55) {
                    h.assertTrue(falls.count[0]==1&&falls.distance[0]==0&&held.getHealth()==20,
                            "Late held Heli did not protect the whole native fall");
                    falls.damaged(h,1,12.79,12.81);
                    falls.damaged(h,2,12.7,12.81);
                    falls.damaged(h,3,12.1,12.81);
                    falls.damaged(h,4,12,12.81);
                    falls.damaged(h,5,12,12.81);
                    falls.damaged(h,6,12,12.01); // Thruster retains the existing debt policy.
                    falls.damaged(h,7,13.5,14.01);
                    falls.close();
                    System.out.println("CLANK_POLICY health held="+held.getHealth()
                            +" released="+released.getHealth()+" interrupted="+interrupted.getHealth());
                    done(h,actors);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_08_native_after_glide",timeoutTicks=130)
    public static void heldLandingCannotProtectNextFallNativeJumpOrUnrelatedDamage(GameTestHelper h) {
        Vec3 base=platform(h);var p=player(h,base.add(0,.8,0));equip(h,p,"heli");
        p.fallDistance=12;var falls=new Falls(p);
        for(int tick=1;tick<=100;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t<=12)fallFrame(p,t,true);
                if(t==13) {
                    h.assertTrue(falls.count[0]==1&&falls.distance[0]==0&&p.getHealth()==20,
                            "No protected first landing");
                    h.assertTrue(p.hurt(p.damageSources().generic(),2)&&p.getHealth()==18,
                            "Glide protected unrelated generic damage");
                }
                if(t==35) {
                    p.setPos(base.add(0,6,0));p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance=0;p.connection.resetPosition();
                }
                if(t>=35&&t<65)fallFrame(p,t,false);
                if(t==65) {
                    h.assertTrue(falls.count[0]==2&&falls.distance[0]>4&&p.getHealth()<18,
                            "Old glide protected an unrelated native fall");
                    p.jumpFromGround();
                }
                if(t>=65)fallFrame(p,t,false);
                if(t==100) {
                    h.assertTrue(p.onGround()&&falls.count[0]==3&&falls.distance[0]<3,
                            "Ordinary native jump did not retain normal fall handling");
                    falls.close();done(h,p);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_09_damage_scope",timeoutTicks=25)
    public static void launchingHighLongOrHoldingGlideNeverProtectsUnrelatedHazardOrVoidDamage(GameTestHelper h) {
        Vec3 base=platform(h);
        var high=player(h,base);var longer=player(h,base.add(8,0,0));
        var hazard=player(h,base.add(16,2,8));var voidDamage=player(h,base.add(20,2,8));
        for(var p:new ServerPlayer[]{high,longer,hazard,voidDamage})equip(h,p,"heli");
        for(int tick=1;tick<=6;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t<=4)longer.move(MoverType.SELF,new Vec3(0,0,.12));
                frame(high,t,t==4||t==5,t<=5,false);
                frame(longer,t,t==4||t==5,t<=5,true);
                frame(hazard,t,true,false,false);frame(voidDamage,t,true,false,false);
                if(t==6) {
                    h.assertTrue(Rac1ClankServer.session(high).motion().action()
                            ==Rac1ClankCapability.Action.HIGH_JUMP,"High was not genuinely admitted");
                    h.assertTrue(Rac1ClankServer.session(longer).motion().action()
                            ==Rac1ClankCapability.Action.LONG_JUMP,"Long was not genuinely admitted");
                    for(var p:new ServerPlayer[]{hazard,voidDamage})h.assertTrue(
                            Rac1ClankServer.session(p).motion().action()==Rac1ClankCapability.Action.GLIDE,
                            "Damage case did not sustain an admitted glide");
                    h.assertTrue(high.hurt(high.damageSources().generic(),2)&&high.getHealth()==18,
                            "Launching high granted unrelated immunity");
                    h.assertTrue(longer.hurt(longer.damageSources().generic(),2)&&longer.getHealth()==18,
                            "Launching long granted unrelated immunity");
                    h.assertTrue(hazard.hurt(hazard.damageSources().cactus(),2)&&hazard.getHealth()==18,
                            "Held glide granted hazard immunity");
                    h.assertTrue(voidDamage.hurt(voidDamage.damageSources().fellOutOfWorld(),2)
                            &&voidDamage.getHealth()==18,"Held glide granted void immunity");
                    done(h,high,longer,hazard,voidDamage);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_10_real_late_fall",timeoutTicks=105)
    public static void realNativePreactivationDescentIsNegatedOnlyBySuccessfulHeldHeliLanding(GameTestHelper h) {
        Vec3 base=platform(h);
        var held=player(h,base.add(0,8,8));var released=player(h,base.add(8,8,8));
        var nativeFall=player(h,base.add(16,8,8));
        equip(h,held,"heli");equip(h,released,"heli");
        var falls=new Falls(held,released,nativeFall);
        boolean[] admitted={false,false};double[] beforeDistance={0,0};long[] serial={-1,-1};
        for(int tick=1;tick<=85;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                var gliders=new ServerPlayer[]{held,released};
                for(int i=0;i<gliders.length;i++) {
                    var p=gliders[i];boolean activate=!admitted[i]&&p.getY()<=base.y+2;
                    if(activate)beforeDistance[i]=p.fallDistance;
                    fallFrame(p,t,activate||(i==0&&admitted[i]));
                    if(activate) {
                        h.assertTrue(Rac1ClankServer.session(p).motion().action()
                                ==Rac1ClankCapability.Action.GLIDE,"Real late fall did not admit glide");
                        admitted[i]=true;
                        serial[i]=Rac1ClankServer.session(p).snapshot(h.getLevel().getGameTime()).actionSerial();
                    }
                }
                fallFrame(nativeFall,t,false);
                if(t==85) {
                    h.assertTrue(admitted[0]&&admitted[1]&&beforeDistance[0]>5&&beforeDistance[1]>5,
                            "Pre-activation distance did not come from actual native descent");
                    h.assertTrue(falls.count[0]==1&&falls.distance[0]==0&&held.getHealth()==20,
                            "Real late held Heli fall was not fully protected");
                    falls.damaged(h,1,7.5,8.01);
                    falls.damaged(h,2,7,8.01);
                    for(int i=0;i<2;i++)h.assertTrue(Rac1ClankServer.session(gliders[i])
                            .snapshot(h.getLevel().getGameTime()).actionSerial()==serial[i],
                            "A different admitted episode supplied late-fall protection");
                    System.out.println("CLANK_REAL_LATE pre="+beforeDistance[0]+","+beforeDistance[1]
                            +" releasedDistance="+falls.distance[1]+" nativeDistance="+falls.distance[2]
                            +" health="+held.getHealth()+","+released.getHealth()+","+nativeFall.getHealth());
                    falls.close();done(h,held,released,nativeFall);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_01_high",timeoutTicks=85)
    public static void originalHighsUseActualServerPlayerTravelAndLand(GameTestHelper h) {
        Vec3 base=platform(h);
        var heli=player(h,base);var thruster=player(h,base.add(8,0,0));
        equip(h,heli,"heli");equip(h,thruster,"thruster");
        double[] apex={base.y,base.y};boolean[] begun={false,false};
        for(int tick=1;tick<=65;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                var players=new ServerPlayer[]{heli,thruster};
                for(int i=0;i<players.length;i++) {
                    var p=players[i];
                    frame(p,t,t==4||t==5,t<=5,false);
                    begun[i]|=Rac1ClankServer.session(p).snapshot(h.getLevel().getGameTime()).action()
                            ==Rac1ClankCapability.Action.HIGH_JUMP;
                    apex[i]=Math.max(apex[i],p.getY());
                    long steps=Rac1ClankRuntime.diagnostics(p).sourceSteps();
                    var proof=Rac1ClankRuntime.diagnostics(p);
                    if(proof.actualY()>=0||p.onGround())h.assertTrue(
                            !Rac1ClankRuntime.floatingCredit(p),"Upward/zero/grounded motion got descent credit");
                    Rac1ClankRuntime.stepTick(p);Rac1ClankRuntime.stepTick(p);
                    h.assertTrue(steps==Rac1ClankRuntime.diagnostics(p).sourceSteps(),
                            "Repeated same-tick calls added source steps");
                    h.assertTrue(h.getLevel().noCollision(p,p.getBoundingBox().deflate(1e-6)),
                            "Player penetrated native geometry");
                }
                if(t==65) {
                    h.assertTrue(begun[0]&&begun[1],"High action not admitted through actual player tick");
                    h.assertTrue(apex[0]>base.y+1&&apex[1]>base.y+1,"Staged highs did not lift");
                    h.assertTrue(Math.abs(apex[0]-apex[1])>.1,"Packs collapsed into one generic jump");
                    h.assertTrue(Math.abs(heli.getY()-base.y)<.01&&Math.abs(thruster.getY()-base.y)<.01,
                            "Actual high landing did not return to floor");
                    done(h,heli,thruster);
                }
            });
        }
    }
    @GameTest(template="empty",batch="clank_02_long",timeoutTicks=70)
    public static void movingCrouchLongsUseProjectedNativeMovement(GameTestHelper h) {
        Vec3 base=platform(h);
        var heli=player(h,base);var thruster=player(h,base.add(8,0,0));
        equip(h,heli,"heli");equip(h,thruster,"thruster");
        boolean[] began={false,false};
        var wall=BlockPos.containing(base).offset(0,0,5);
        for(int x=-1;x<=10;x++)for(int y=0;y<4;y++)
            h.getLevel().setBlock(wall.offset(x,y,0),Blocks.STONE.defaultBlockState(),3);
        for(int tick=1;tick<=50;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                var players=new ServerPlayer[]{heli,thruster};
                for(int i=0;i<2;i++) {
                    var p=players[i];
                    if(t<=4)p.move(MoverType.SELF,new Vec3(0,0,.12)); // ordinary validated native motion
                    frame(p,t,t==4||t==5,t<=5,true);
                    began[i]|=Rac1ClankServer.session(p).snapshot(h.getLevel().getGameTime()).action()
                            ==Rac1ClankCapability.Action.LONG_JUMP;
                }
                if(t==50) {
                    h.assertTrue(began[0]&&began[1],"Moving case did not classify long");
                    h.assertTrue(heli.getZ()>base.z+2&&thruster.getZ()>base.z+2,"No actual long distance");
                    h.assertTrue(heli.getBoundingBox().maxZ<=wall.getZ()+1e-5
                            &&thruster.getBoundingBox().maxZ<=wall.getZ()+1e-5,"Native wall penetrated");
                    h.assertTrue(Math.abs(heli.getY()-base.y)<.01&&Math.abs(thruster.getY()-base.y)<.01,
                            "Long did not land natively");
                    done(h,heli,thruster);
                }
            });
        }
    }
    @GameTest(template="empty",batch="clank_03_geometry",timeoutTicks=80)
    public static void slabCeilingWallAndExistingFallDebtAreNative(GameTestHelper h) {
        Vec3 base=platform(h);
        var low=player(h,base);equip(h,low,"heli");
        BlockPos ceiling=BlockPos.containing(base).above(2);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            h.getLevel().setBlock(ceiling.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        var slabAt=BlockPos.containing(base.add(8,0,0));
        h.getLevel().setBlock(slabAt,Blocks.STONE_SLAB.defaultBlockState(),3);
        var slab=player(h,Vec3.atBottomCenterOf(slabAt).add(0,.5,0));equip(h,slab,"thruster");
        var debt=player(h,base.add(16,2,0));equip(h,debt,"heli");
        debt.fallDistance=12;
        double[] distance={-1};int[] landings={0};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingFallEvent> watch=event->{
            if(event.getEntity()==debt){distance[0]=event.getDistance();landings[0]++;}
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(watch);
        for(int tick=1;tick<=65;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(var p:new ServerPlayer[]{low,slab})
                    frame(p,t,t==4||t==5,t<=5,false);
                frame(debt,t,true,false,false);
                h.assertTrue(low.getBoundingBox().maxY<=ceiling.getY()+1e-5,"Low ceiling penetrated");
                var proof=Rac1ClankRuntime.diagnostics(low);
                if(proof.actualY()>=0||low.onGround())h.assertTrue(
                        !Rac1ClankRuntime.floatingCredit(low),"Clipped/zero/grounded motion got credit");
                if(t==65) {
                    h.assertTrue(Math.abs(slab.getY()-(base.y+.5))<.01,"Slab used block-center ground");
                    h.assertTrue(debt.getHealth()==20,"Held Heli landing failed to negate the entire fall");
                    h.assertTrue(landings[0]==1&&distance[0]==0,
                            "Held Heli native fall event was not adjusted exactly once to zero");
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(watch);
                    done(h,low,slab,debt);
                }
            });
        }
    }
    @GameTest(template="empty",batch="clank_04_packets",timeoutTicks=35)
    public static void forgedPacketsNeverStepAndOnlyActualDescentGetsCredit(GameTestHelper h) {
        Vec3 base=platform(h);var p=player(h,base.add(0,8,0));equip(h,p,"heli");
        h.runAtTickTime(1,()->{
            frame(p,1,true,false,true);
            var before=Rac1ClankRuntime.diagnostics(p);Vec3 at=p.position();
            proof("before-forgery",p);
            h.assertTrue(before.controlled()&&before.actualY()<=-.03125,"No actual glide descent proof");
            for(int i=0;i<50;i++)p.connection.handleMovePlayer(
                    new ServerboundMovePlayerPacket.PosRot(at.x+500,at.y+500,at.z+500,0,0,false));
            h.assertTrue(p.position().distanceToSqr(at)<1e-12,"Forged client coordinate became authoritative");
            var after=Rac1ClankRuntime.diagnostics(p);
            h.assertTrue(before.sourceSteps()==after.sourceSteps(),"Packet caused additional physics");
            h.assertTrue(after.credits()>0,"Valid source glide got no scoped descent credit");
            var water=BlockPos.containing(p.position());
            h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
            h.assertTrue(!Rac1ClankRuntime.floatingCredit(p),"Water retained aerial floating credit");
            h.getLevel().setBlock(water,Blocks.AIR.defaultBlockState(),3);
            p.connection.teleport(at.x,at.y,at.z,0,0);
            h.assertTrue(!Rac1ClankRuntime.floatingCredit(p),"Awaiting teleport retained floating credit");
            var raw=new ServerboundMovePlayerPacket.PosRot(at.x+1,at.y,at.z,0,0,false);
            h.assertTrue(Rac1ClankRuntime.authoritativePacket(p,raw)==raw,"Teleport packet path was replaced");
            done(h,p);
        });
    }
    @GameTest(template="empty",batch="clank_05_invalid",timeoutTicks=35)
    public static void rawNaNInfinityAndKnockbackFailClosed(GameTestHelper h) {
        Vec3 base=platform(h);
        var nan=player(h,base.add(0,8,0));var infinity=player(h,base.add(8,8,0));
        var kb=player(h,base.add(16,8,0));
        for(var p:new ServerPlayer[]{nan,infinity,kb})equip(h,p,"heli");
        h.runAtTickTime(1,()->{
            for(var p:new ServerPlayer[]{nan,infinity,kb})frame(p,1,true,false,false);
            Vec3 a=nan.position(),b=infinity.position();
            proof("before-infinity",infinity);
            nan.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(Double.NaN,a.y,a.z,0,0,false));
            infinity.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                    Double.POSITIVE_INFINITY,b.y,b.z,0,0,false));
            h.assertTrue(((Sink)nan.connection).disconnects==1,"Raw NaN did not hit native disconnect");
            h.assertTrue(((Sink)infinity.connection).disconnects==1,"Controlled infinity was sanitized instead of rejected");
            h.assertTrue(nan.position().equals(a)&&infinity.position().equals(b),"Invalid coordinates moved a player");
            h.assertTrue(!Rac1ClankRuntime.floatingCredit(nan)&&!Rac1ClankRuntime.floatingCredit(infinity),
                    "Disconnected invalid player retained credit");
            long epoch=Rac1ClankServer.session(kb).epoch();
            kb.fallDistance=12;
            kb.knockback(1,1,0); // actual native knockback event path
            h.assertTrue(Rac1ClankServer.session(kb).epoch()>epoch,"Knockback did not invalidate episode epoch");
            h.assertTrue(!Rac1ClankRuntime.ownsTravel(kb)&&!Rac1ClankRuntime.floatingCredit(kb),
                    "Knockback retained movement ownership/credit");
            h.assertTrue(kb.fallDistance>=12,"Knockback cleared unrelated debt");
            done(h,nan,infinity,kb);
        });
    }
    @GameTest(template="empty",batch="clank_06_inactive",timeoutTicks=45)
    public static void deniedOrdinaryTravelAndNativeJumpStayUntouchedAndEquipmentCannotRefill(GameTestHelper h) {
        Vec3 base=platform(h);
        var nativePlayer=player(h,base);var denied=player(h,base.add(8,0,0));
        try {
            int result=denied.server.getCommands().getDispatcher().execute("clanktest @s heli",
                    denied.createCommandSourceStack().withPermission(0));
            h.assertTrue(result==0,"Nonoperator command granted equipment");
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
        h.assertTrue(!Rac1ClankServer.session(denied).equipment().worn(),"Nonoperator acquired Clank");
        equip(h,denied,"heli");
        nativePlayer.jumpFromGround();denied.jumpFromGround();
        nativePlayer.travel(Vec3.ZERO);denied.travel(Vec3.ZERO);
        h.assertTrue(nativePlayer.getDeltaMovement().distanceToSqr(denied.getDeltaMovement())<1e-12,
                "Denied/waiting Clank replaced ordinary native jump");
        h.assertTrue(!Rac1ClankTravel.replace(nativePlayer)&&!Rac1ClankTravel.replace(denied),
                "Inactive player travel was claimed");
        var actor=player(h,base.add(16,0,0));equip(h,actor,"heli");
        for(int tick=1;tick<=10;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                frame(actor,t,t>=4,true,false);
                if(t==10) {
                    h.assertTrue(Rac1ClankServer.session(actor).motion().spentAirJump()
                            &&actor.getY()>base.y+.1,"No native airborne episode to test equipment change");
                    long old=Rac1ClankServer.session(actor).epoch();
                    equip(h,actor,"unequip");equip(h,actor,"thruster");
                    var state=Rac1ClankServer.session(actor);
                    h.assertTrue(state.epoch()>old&&state.motion().spentAirJump(),
                            "Equipment toggle replenished airborne jump budget");
                    Rac1ClankRuntime.input(actor,new Rac1ClankMovementWire.Input(old,999,
                            new Rac1ClankInput(true,true,true,0,1,0)));
                    h.assertTrue(state.motion().action()==Rac1ClankCapability.Action.IDLE,
                            "Stale epoch resurrected a move");
                    done(h,nativePlayer,denied,actor);
                }
            });
        }
    }
}
