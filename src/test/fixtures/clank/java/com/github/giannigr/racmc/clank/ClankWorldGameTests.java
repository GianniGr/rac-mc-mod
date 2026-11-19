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
        Rac1ClankMovementWire.Correction lastCorrection;
        Rac1ClankSession.Snapshot lastSnapshot;
        Rac1HydroWire.Snapshot lastHydro;
        private static Connection fixtureConnection() {
            return new Connection(PacketFlow.SERVERBOUND) {
                private final io.netty.channel.embedded.EmbeddedChannel fixtureChannel=
                        new io.netty.channel.embedded.EmbeddedChannel();
                @Override public io.netty.channel.Channel channel(){return fixtureChannel;}
            };
        }
        Sink(ServerLevel level,ServerPlayer player,GameProfile profile) {
            super(level.getServer(),fixtureConnection(),player,
                    CommonListenerCookie.createInitial(profile,false));
        }
        @Override public void send(Packet<?> packet) {
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    &&custom.payload() instanceof Rac1HydroNetwork.Snapshot hydro)
                lastHydro=hydro.value();
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    &&custom.payload() instanceof Rac1ClankNetwork.Correction correction)
                lastCorrection=correction.value();
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    &&custom.payload() instanceof Rac1ClankNetwork.Snapshot snapshot)
                lastSnapshot=snapshot.value();
        }
        @Override public boolean isAcceptingMessages(){return open;}
        @Override public void disconnect(Component reason){open=false;disconnects++;}
    }
    private static Vec3 platform(GameTestHelper h) {
        return platform(h,0);
    }
    private static Vec3 platform(GameTestHelper h,int offset) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                .set(false,h.getLevel().getServer());
        h.getLevel().getGameRules().getRule(GameRules.RULE_FALL_DAMAGE)
                .set(true,h.getLevel().getServer());
        var base=h.absolutePos(new BlockPos(3+offset,2,3+offset));
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
        for(var p:players){Rac1HydroRuntime.forget(p);Rac1ClankRuntime.invalidate(p);p.discard();}
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
        void protectedLanding(GameTestHelper h,int i) {
            h.assertTrue(count[i]==1&&distance[i]==0&&players[i].getHealth()==20,
                    "Expected one protected whole-fall landing index="+i+" distance="+distance[i]
                            +" count="+count[i]+" health="+players[i].getHealth());
        }
        void damaged(GameTestHelper h,int i,double min,double max) {
            h.assertTrue(count[i]==1&&distance[i]>=min&&distance[i]<=max,
                    "Native fall distance/count index="+i+" distance="+distance[i]+" count="+count[i]);
            h.assertTrue(players[i].getHealth()==20-(float)Math.ceil(distance[i]-3)
                    &&players[i].getHealth()<20,"Native health disagrees with fall event index="+i
                    +" health="+players[i].getHealth()+" distance="+distance[i]);
        }
    }

    private static Vec3 pool(GameTestHelper h) {
        // Templates/batches reuse adjacent small structure footprints; wet fixtures extend beyond
        // those bounds. Keep them away from aerial regions so later fluid ticks cannot wet a fall.
        Vec3 base=platform(h,128);var origin=BlockPos.containing(base);
        for(int x=-2;x<=22;x++)for(int z=-2;z<=22;z++)for(int y=0;y<=6;y++)
            // Keep scheduled native fluid ticks from spilling into later aerial test batches.
            h.getLevel().setBlock(origin.offset(x,y,z),(x==-2||x==22||z==-2||z==22?Blocks.STONE:Blocks.WATER)
                    .defaultBlockState(),3);
        return base;
    }
    private static void hydro(GameTestHelper h,ServerPlayer p,boolean on) {
        equip(h,p,"hydro "+(on?"on":"off"));
    }
    private static void hydroFrame(ServerPlayer p,long serial,boolean thrust,boolean rise,boolean dive) {
        var s=Rac1HydroRuntime.snapshot(p);
        Rac1HydroRuntime.input(p,new Rac1HydroWire.Input(s.epoch(),serial,
                new Rac1HydroMotion.Input(thrust,rise,dive,0,0,0)));
        p.doTick();
    }
    @GameTest(template="empty",batch="clank_20_hydro_gate",timeoutTicks=50)
    public static void hydroSurfaceSubmergedWadingAndRestoreDoNotRequireSprint(GameTestHelper h) {
        var base=pool(h);var p=player(h,base.add(5,9,5));
        equip(h,p,"thruster"); // No Hydro command: worn Clank supplies the temporary build default.
        p.setPos(base.add(5,5.9,5));p.setOnGround(false);p.setSprinting(false);
        h.runAtTickTime(1,()->{
            p.setDeltaMovement(Vec3.ZERO);hydroFrame(p,1,false,false,false);
            var s=Rac1HydroRuntime.snapshot(p);
            h.assertTrue(s.wet()&&s.water()==Rac1HydroWater.Mode.SURFACE&&!s.controlled(),
                    "No-Sprint surface failed automatic nonthrust override: "+s);
            h.assertTrue(s.dry()==Rac1ClankEquipment.Module.THRUSTER,"Dry Thruster overwritten");
            p.setPos(base.add(5,2,5));p.setDeltaMovement(Vec3.ZERO);
        });
        h.runAtTickTime(2,()->{
            hydroFrame(p,2,false,false,false);var s=Rac1HydroRuntime.snapshot(p);
            h.assertTrue(s.wet()&&s.water()==Rac1HydroWater.Mode.SUBMERGED&&!s.controlled(),
                    "Submerged model swap required thrust");
            p.setPos(base.add(5,6.1,5));p.setDeltaMovement(Vec3.ZERO);
            var floor=BlockPos.containing(base.add(5,5,5));
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        });
        h.runAtTickTime(3,()->{
            hydroFrame(p,3,true,false,false);var s=Rac1HydroRuntime.snapshot(p);
            h.assertTrue(!s.wet()&&!s.controlled()&&s.water()==Rac1HydroWater.Mode.WADING,
                    "Wading admitted Hydro: "+s);
            p.setPos(base.add(5,9,5));p.setDeltaMovement(Vec3.ZERO);
        });
        h.runAtTickTime(7,()->{
            hydroFrame(p,7,false,false,false);var s=Rac1HydroRuntime.snapshot(p);
            h.assertTrue(!s.wet()&&s.dry()==Rac1ClankEquipment.Module.THRUSTER,
                    "Exit did not preserve selected dry module");
            for(int i=0;i<3;i++)s=Rac1HydroRuntime.snapshot(p);
            hydro(h,p,false);
            h.assertTrue(!Rac1HydroRuntime.snapshot(p).access()
                    &&Rac1ClankServer.session(p).equipment().selected()==Rac1ClankEquipment.Module.THRUSTER,
                    "Off cleared dry selection");
            System.out.println("HYDRO_GATE surface/submerged/wading/restore PASS");done(h,p);
        });
    }
    @GameTest(template="empty",batch="clank_21_hydro_permission",timeoutTicks=30)
    public static void hydroPermissionRemoteSnapshotAndSessionCleanup(GameTestHelper h) {
        var base=pool(h);var p=player(h,base.add(2,9,2));var observer=player(h,base.add(12,9,12));
        h.assertTrue(!Rac1HydroRuntime.configure(p.createCommandSourceStack().withPermission(2),p,true),
                "Unworn Clank granted Hydro");
        equip(h,p,"heli");
        h.assertTrue(!Rac1HydroRuntime.configure(p.createCommandSourceStack().withPermission(0),p,true),
                "Nonoperator granted Hydro");
        hydro(h,p,true);long granted=Rac1HydroRuntime.snapshot(p).epoch();
        hydro(h,p,true);h.assertTrue(granted==Rac1HydroRuntime.snapshot(p).epoch(),"Repeated on renewed epoch");
        p.setPos(base.add(2,2,2));
        h.runAtTickTime(1,()->{
            hydroFrame(p,1,false,false,false);
            Rac1HydroRuntime.tracking(observer,p);
            var remote=((Sink)observer.connection).lastHydro;
            h.assertTrue(remote!=null&&remote.equals(Rac1HydroRuntime.snapshot(p))&&remote.wet(),
                    "Late observer lacks actual authoritative override");
            Rac1ClankRuntime.teleport(p);
            var reset=Rac1HydroRuntime.snapshot(p);
            h.assertTrue(reset.access()&&!reset.controlled()&&!reset.wet()&&reset.epoch()>granted,
                    "Teleport did not invalidate transient Hydro episode");
            Rac1HydroRuntime.input(p,new Rac1HydroWire.Input(granted,999,
                    new Rac1HydroMotion.Input(true,false,false,0,0,0)));
            equip(h,p,"thruster");
            h.assertTrue(Rac1HydroRuntime.snapshot(p).access(),"Wet dry-selection change lost session access");
            hydro(h,p,false);h.assertTrue(!Rac1HydroRuntime.snapshot(p).access(),"Off failed");
            Rac1HydroRuntime.tracking(observer,p);
            h.assertTrue(!((Sink)observer.connection).lastHydro.access()
                    &&!((Sink)observer.connection).lastHydro.wet(),
                    "Late re-tracker retained stale Hydro after off");
            hydro(h,p,true);equip(h,p,"unequip");
            h.assertTrue(!Rac1HydroRuntime.snapshot(p).access(),"Unequip retained Hydro access");
            equip(h,p,"heli");hydro(h,p,true);
            p.kill();
            h.assertTrue(!Rac1HydroRuntime.snapshot(p).access(),"Actual death retained Hydro test access");
            System.out.println("HYDRO_PERMISSION remote/teleport/off/unequip/death PASS");done(h,p,observer);
        });
    }
    @GameTest(template="empty",batch="clank_22_hydro_thrust",timeoutTicks=60)
    public static void hydroUnderwaterPitchNativeAirCollisionAndStaleInput(GameTestHelper h) {
        var base=pool(h);var up=player(h,base.add(3,9,3));var down=player(h,base.add(10,9,10));
        var players=new ServerPlayer[]{up,down};
        for(var p:players){equip(h,p,"heli");hydro(h,p,true);p.setPos(base.add(p==up?3:10,2,p==up?3:10));
            p.setDeltaMovement(Vec3.ZERO);p.setOnGround(false);p.setAirSupply(200);}
        for(int tick=1;tick<=12;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(var p:players) {
                    hydroFrame(p,t,true,p==up,p==down);
                    h.assertTrue(Rac1HydroRuntime.sourceSteps(p)==t*3,"Hydro not exactly three source60 steps/tick");
                    h.assertTrue(h.getLevel().noCollision(p,p.getBoundingBox().deflate(1e-6)),"Hydro penetrated blocks");
                    long count=Rac1HydroRuntime.sourceSteps(p);Rac1HydroRuntime.stepTick(p);
                    h.assertTrue(count==Rac1HydroRuntime.sourceSteps(p),"Duplicate tick advanced Hydro");
                    h.assertTrue(Rac1ClankRuntime.ownsTravel(p),"Native travel owner missing during thrust");
                }
                if(t==12) {
                    h.assertTrue(up.getY()>base.y+2&&down.getY()<base.y+2,"Jump/Sneak did not pitch up/down");
                    h.assertTrue(up.getAirSupply()<200&&down.getAirSupply()<200,"Hydro prevented native air drain");
                }
            });
        }
        h.runAtTickTime(13,()->{
            up.doTick();var s=Rac1HydroRuntime.snapshot(up);
            h.assertTrue(s.motion().stage()!=Rac1HydroMotion.Stage.THRUST,"Stale thrust sustained power");
            up.setPos(base.add(3,9,3));up.setDeltaMovement(Vec3.ZERO);
            down.setPos(base.add(10,9,10));down.setDeltaMovement(Vec3.ZERO);
        });
        h.runAtTickTime(14,()->{
            for(var p:players){hydroFrame(p,14,true,true,false);
                h.assertTrue(!Rac1HydroRuntime.snapshot(p).controlled(),"Sprint powered dry movement");}
            System.out.println("HYDRO_THRUST pitch/air/collision/freshness/dry PASS");done(h,players);
        });
    }
    @GameTest(template="empty",batch="clank_23_hydro_coexist",timeoutTicks=60)
    public static void hydroYieldsToNativeWaterModifiersAndStopsAtWall(GameTestHelper h) {
        var base=pool(h);var p=player(h,base.add(5,9,5));
        equip(h,p,"heli");hydro(h,p,true);p.setPos(base.add(5,2,5));p.setOnGround(false);
        h.assertTrue(Rac1HydroWater.observe(p).propulsion(),"Still deep source water must permit propulsion");
        p.getAbilities().flying=true;
        h.assertTrue(!Rac1HydroWater.observe(p).compatible(),"Flight must yield");p.getAbilities().flying=false;
        p.hurtTime=5;h.assertTrue(!Rac1HydroWater.observe(p).propulsion(),"Hurt must yield");p.hurtTime=0;
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE,100));
        h.assertTrue(!Rac1HydroWater.observe(p).propulsion(),"Dolphin's Grace must yield");
        p.removeAllEffects();
        var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY);
        double original=attribute.getBaseValue();attribute.setBaseValue(.5);
        h.assertTrue(!Rac1HydroWater.observe(p).propulsion(),"Depth Strider attribute must yield");
        attribute.setBaseValue(original);
        var feet=BlockPos.containing(p.position());
        h.getLevel().setBlock(feet,Blocks.BUBBLE_COLUMN.defaultBlockState(),3);
        h.assertTrue(!Rac1HydroWater.observe(p).propulsion(),"Bubble column must yield");
        h.getLevel().setBlock(feet,Blocks.WATER.defaultBlockState(),3);
        h.getLevel().setBlock(feet.east(),Blocks.AIR.defaultBlockState(),3);
        h.getLevel().setBlock(feet,Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,1),3);
        h.assertTrue(h.getLevel().getFluidState(feet).getFlow(h.getLevel(),feet).lengthSqr()>0,
                "Flow fixture did not create a native current");
        h.assertTrue(!Rac1HydroWater.observe(p).propulsion(),"Native current must yield");
        h.getLevel().setBlock(feet.east(),Blocks.WATER.defaultBlockState(),3);
        h.getLevel().setBlock(feet,Blocks.WATER.defaultBlockState(),3);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WATER_BREATHING,100));
        h.assertTrue(Rac1HydroWater.observe(p).propulsion(),"Native breathing effect need not disable Hydro");
        p.removeAllEffects();
        var origin=BlockPos.containing(base);
        for(int x=3;x<=7;x++)for(int y=0;y<=6;y++)
            h.getLevel().setBlock(origin.offset(x,y,7),Blocks.STONE.defaultBlockState(),3);
        for(int tick=1;tick<=22;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                hydroFrame(p,t,true,false,false);
                h.assertTrue(p.getZ()<=base.z+7-.8+1e-5,
                        "Controlled Hydro crossed native wall: "+p.position());
                if(t==22) {
                    h.assertTrue(p.horizontalCollision,"Hydro never reached wall");
                    var state=Rac1HydroRuntime.snapshot(p);
                    h.assertTrue(Math.abs(state.motion().effective().z())<1e-6,"Collision retained requested velocity");
                    Rac1ClankServer.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                    h.assertTrue(!Rac1HydroRuntime.snapshot(p).access(),"Logout retained Hydro");
                    System.out.println("HYDRO_COEXIST modifiers/bubbles/native-wall/logout PASS");done(h,p);
                }
            });
        }
    }
    @GameTest(template="empty",batch="clank_24_hydro_automatic",timeoutTicks=50)
    public static void bothCreativeFixturesAutomaticallyKeepHydroFromDiveToSurfaceAndRestoreDryPack(GameTestHelper h) {
        var base=pool(h);var heli=player(h,base.add(3,9,3));var thruster=player(h,base.add(12,9,12));
        var players=new ServerPlayer[]{heli,thruster};
        for(var p:players) {
            p.setGameMode(GameType.CREATIVE);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(
                    (p==heli?com.github.giannigr.racmc.item.RacMcItems.CLANK_HELI_PACK:
                            com.github.giannigr.racmc.item.RacMcItems.CLANK_THRUSTER_PACK).get()));
            h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getMainHandItem(),
                    net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Native fixture use failed");
            h.assertTrue(Rac1HydroRuntime.snapshot(p).access(),"Creative fixture requires separate Hydro opt-in");
            p.setGameMode(GameType.SURVIVAL);p.setOnGround(false);
        }
        for(int tick=1;tick<=15;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(var p:players) {
                    double offset=p==heli?3:12;
                    p.setSprinting(false);p.setDeltaMovement(Vec3.ZERO);
                    p.setPose(t>=7&&t<=9?net.minecraft.world.entity.Pose.SWIMMING:net.minecraft.world.entity.Pose.STANDING);
                    p.setPos(base.add(offset,t<=6?2:t<=9?6.65:t<=12?5.9:9,offset));
                    // No held thrust, no Hydro on command, no Sprint-dependent native swimming flag.
                    hydroFrame(p,t,false,false,false);
                    var s=Rac1HydroRuntime.snapshot(p);
                    var prior=p==heli?Rac1ClankEquipment.Module.HELI:Rac1ClankEquipment.Module.THRUSTER;
                    h.assertTrue(s.dry()==prior&&!s.controlled(),"Surface altered prior pack or admitted power");
                    if(t<=12) {
                        h.assertTrue(s.wet()&&s.water()==(t<=6?Rac1HydroWater.Mode.SUBMERGED:Rac1HydroWater.Mode.SURFACE),
                                "Dive->qualifying surface lost Hydro without Sprint: "+s);
                        if(t>=6)h.assertTrue(s.visual()==Rac1HydroVisual.Phase.WET,"Surface restarted/folded Hydro");
                    }else h.assertTrue(!s.wet(),"Dry exit retained wet override");
                    if(t==15)h.assertTrue(s.visual()==Rac1HydroVisual.Phase.DRY,"Prior dry pack was not visually restored");
                }
                if(t==12) {
                    Rac1HydroRuntime.tracking(thruster,heli);
                    h.assertTrue(((Sink)thruster.connection).lastHydro.water()==Rac1HydroWater.Mode.SURFACE
                            &&((Sink)thruster.connection).lastHydro.wet(),"Remote surface lost Hydro");
                }
                if(t==15){System.out.println("HYDRO_AUTOMATIC bothCreative/noCommand/diveSurface/restore PASS");done(h,players);}
            });
        }
    }
    @GameTest(template="empty",batch="clank_25_hydro_override",timeoutTicks=30)
    public static void optionalHydroSuppressionSurvivesSwitchAndDimensionButClearsWithWornSession(GameTestHelper h) {
        var base=pool(h);var p=player(h,base.add(4,9,4));equip(h,p,"heli");
        h.assertTrue(Rac1HydroRuntime.snapshot(p).access(),"Command-equipped default unavailable");
        hydro(h,p,false);equip(h,p,"thruster");Rac1ClankRuntime.teleport(p);
        Rac1ClankServer.dimension(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                p,h.getLevel().dimension(),net.minecraft.world.level.Level.NETHER));
        p.setPos(base.add(4,2,4));p.setOnGround(false);
        h.runAtTickTime(1,()->{
            hydroFrame(p,1,true,false,false);
            h.assertTrue(!Rac1HydroRuntime.snapshot(p).access()&&!Rac1HydroRuntime.ownsTravel(p),
                    "Default tick/switch/dimension/teleport defeated explicit off");
            hydro(h,p,true);long epoch=Rac1HydroRuntime.snapshot(p).epoch();hydro(h,p,true);
            h.assertTrue(Rac1HydroRuntime.snapshot(p).epoch()==epoch,"Repeated on was not idempotent");
            hydro(h,p,false);equip(h,p,"unequip");equip(h,p,"heli");
            h.assertTrue(Rac1HydroRuntime.snapshot(p).access(),"Suppression leaked into a newly worn session");
            hydro(h,p,false);
            Rac1ClankServer.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            equip(h,p,"thruster");
            h.assertTrue(Rac1HydroRuntime.snapshot(p).access(),"Logout suppression leaked into new session");
            System.out.println("HYDRO_OVERRIDE optionalOff/on/switch/dimension/unequip/logout PASS");done(h,p);
        });
    }

    @GameTest(template="empty",batch="clank_13_same_glide_repress",timeoutTicks=60)
    public static void shortQueuedRepressRearmsHeliWithoutNewActionOrMotionAndFinalReleaseStillDamages(GameTestHelper h) {
        Vec3 base=platform(h);
        var held=player(h,base.add(0,.8,8));var repress=player(h,base.add(4,.8,8));
        var releaseAgain=player(h,base.add(8,.8,8));var missing=player(h,base.add(12,.8,8));
        var thruster=player(h,base.add(16,.8,8));
        var players=new ServerPlayer[]{held,repress,releaseAgain,missing,thruster};
        for(var p:players){equip(h,p,p==thruster?"thruster":"heli");p.fallDistance=12;}
        var falls=new Falls(players);long[] actions=new long[players.length];
        for(int tick=1;tick<=35;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t>=2) {
                    for(int i=0;i<players.length;i++) {
                        tickWithoutInput(players[i]);
                        var state=Rac1ClankServer.session(players[i]).snapshot(h.getLevel().getGameTime());
                        if(t==2)actions[i]=state.actionSerial();
                        if(t<=5)h.assertTrue(state.action()==Rac1ClankCapability.Action.GLIDE
                                        &&state.actionSerial()==actions[i]&&state.phaseSourceSteps()==(t-1)*3,
                                "Brief release/repress restarted/extended original action/animation clock");
                    }
                    if(t<=8)for(int i=1;i<4;i++)h.assertTrue(
                            Math.abs(players[i].getY()-held.getY())<1e-8,
                            "Damage-policy re-press altered original glide trajectory");
                    if(t==3)for(int i=1;i<4;i++)h.assertTrue(
                            Rac1ClankRuntime.inputDiagnostics(players[i]).released(),
                            "Accepted short release failed to revoke protection");
                    if(t==4)for(int i=1;i<4;i++) {
                        var proof=Rac1ClankRuntime.inputDiagnostics(players[i]);
                        h.assertTrue(!proof.released()&&proof.heldProof()
                                        &&proof.consumedSerial()>proof.releaseSerial(),
                                "Fresh consumed same-glide re-press did not re-arm: "+proof);
                        System.out.println("CLANK_REPRESS_REARM "+proof);
                    }
                    if(t==6)h.assertTrue(Rac1ClankRuntime.inputDiagnostics(releaseAgain).released()
                                    &&!Rac1ClankRuntime.inputDiagnostics(missing).heldProof(),
                            "Final release or missing fresh input retained landing eligibility");
                }
                if(t<20)for(int i=0;i<players.length;i++) {
                    boolean jump=i==0||t!=2;
                    if(i==2&&t>=5)jump=false;
                    queueInput(players[i],i==3&&t>=4?3:t,jump);
                }
                if(t==35) {
                    for(int i=0;i<2;i++)h.assertTrue(falls.count[i]==1&&falls.distance[i]==0
                                    &&players[i].getHealth()==20,
                            "Final held Heli did not negate the full pre-activation fall");
                    falls.protectedLanding(h,2);falls.protectedLanding(h,3);
                    falls.damaged(h,4,12,12.01); // No same-glide Heli policy expansion to Thruster.
                    for(int i=0;i<players.length;i++)h.assertTrue(Rac1ClankServer.session(players[i])
                                    .snapshot(h.getLevel().getGameTime()).actionSerial()==actions[i],
                            "Short re-press created a different action serial");
                    System.out.println("CLANK_REPRESS_HEALTH held="+held.getHealth()+" repress="+repress.getHealth()
                            +" released="+releaseAgain.getHealth()+" missing="+missing.getHealth()
                            +" thruster="+thruster.getHealth());
                    falls.close();done(h,players);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_14_distinct_glides",timeoutTicks=125)
    public static void repeatedDistinctHeliGlidesKeepWholeNativeFallAndFinalHeldVersusReleasePolicy(GameTestHelper h) {
        Vec3 base=platform(h);var held=player(h,base.add(0,8,8));
        var released=player(h,base.add(8,8,8));var players=new ServerPlayer[]{held,released};
        var falls=new Falls(players);int[] admissions={0,0};long[] serials={0,0};
        boolean[] finalRelease={false,false};
        for(var p:players)equip(h,p,"heli");
        for(int tick=1;tick<=95;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(int i=0;i<players.length;i++) {
                    var p=players[i];
                    boolean jump=(t>=6&&t<=16)||(t>=20&&t<=30)||t>=34;
                    if(i==1&&t>=34&&p.getY()<base.y+.9)finalRelease[i]=true;
                    if(finalRelease[i])jump=false;
                    fallFrame(p,t,jump);
                    var state=Rac1ClankServer.session(p).snapshot(h.getLevel().getGameTime());
                    if(state.action()==Rac1ClankCapability.Action.GLIDE&&state.actionSerial()!=serials[i]) {
                        admissions[i]++;serials[i]=state.actionSerial();
                        System.out.println("CLANK_DISTINCT_GLIDE player="+i+" admission="+admissions[i]
                                +" tick="+t+" fall="+p.fallDistance+" "+state);
                    }
                    if(t==18||t==32)h.assertTrue(state.action()==Rac1ClankCapability.Action.IDLE,
                            "Release beyond original minimum did not create a genuine freefall gap");
                }
                if(t==95) {
                    h.assertTrue(admissions[0]==3&&admissions[1]==3&&finalRelease[1],
                            "Did not exercise three distinct admitted glides and final accepted release");
                    h.assertTrue(falls.count[0]==1&&falls.distance[0]==0&&held.getHealth()==20,
                            "Distinct final held glide lost whole-fall protection");
                    falls.protectedLanding(h,1);
                    System.out.println("CLANK_DISTINCT_HEALTH held="+held.getHealth()
                            +" released="+released.getHealth()+" distance="+falls.distance[1]);
                    falls.close();done(h,players);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_15_release_grace_boundary",timeoutTicks=85)
    public static void releaseGraceNativeHandoffHasExactFortyTickBoundaryAndOneShotTouchdown(GameTestHelper h) {
        Vec3 base=platform(h);var inside=player(h,base.add(0,2,8));
        var expired=player(h,base.add(8,2,8));var players=new ServerPlayer[]{inside,expired};
        for(var p:players){equip(h,p,"heli");p.fallDistance=12;}
        var falls=new Falls(players);long[] release={-1};
        for(int tick=1;tick<=65;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                if(t<=12)for(var p:players)fallFrame(p,t,t==1);
                else if(t<41)for(var p:players) {
                    // Keep this isolated fixture airborne and stationary to time the boundary
                    // exactly; no synthetic fall event or production clock/protection mutation.
                    Rac1ClankRuntime.input(p,new Rac1ClankMovementWire.Input(
                            Rac1ClankServer.session(p).epoch(),t,Rac1ClankInput.RELEASED));
                }
                if(t==2)release[0]=Rac1ClankRuntime.graceDiagnostics(inside).releaseTick();
                if(t==14)for(var p:players)h.assertTrue(
                        Rac1ClankServer.session(p).motion().action()==Rac1ClankCapability.Action.IDLE
                                &&!p.onGround(),"Grace did not survive original motion's native handoff");
                if(t>=3&&t<41)for(var p:players) {
                    var proof=Rac1ClankRuntime.graceDiagnostics(p);
                    h.assertTrue(proof.releaseTick()==release[0]&&proof.releaseSerial()==2,
                            "Repeated unheld samples restarted/refreshed grace");
                }
                if(t==41||t==42) {
                    int i=t==41?0:1;var p=players[i];
                    long elapsed=h.getLevel().getGameTime()-release[0];
                    h.assertTrue(elapsed==(i==0?39:40),"Wrong native boundary timing");
                    Vec3 before=p.position();p.move(MoverType.SELF,new Vec3(0,-4,0));
                    Vec3 actual=p.position().subtract(before);
                    h.assertTrue(p.onGround(),"Boundary case did not collide with actual native floor");
                    p.doCheckFallDamage(actual.x,actual.y,actual.z,true);
                    h.assertTrue(Rac1ClankRuntime.graceDiagnostics(p).releaseTick()==-1,
                            "First touchdown did not consume grace");
                    System.out.println("CLANK_GRACE_BOUNDARY elapsed="+elapsed+" distance="
                            +falls.distance[i]+" health="+p.getHealth());
                }
                if(t==43){falls.protectedLanding(h,0);falls.damaged(h,1,13,14.01);}
                if(t==44) {
                    inside.setPos(base.add(0,.8,8));inside.setOnGround(false);
                    inside.setDeltaMovement(Vec3.ZERO);inside.fallDistance=12;
                    inside.connection.resetPosition();
                }
                if(t>=44)fallFrame(inside,t,false);
                if(t==65) {
                    h.assertTrue(falls.count[0]==2&&falls.distance[0]>=12&&inside.getHealth()<20,
                            "Consumed grace protected an unrelated subsequent native fall");
                    falls.close();done(h,players);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_16_release_grace_lifecycle",timeoutTicks=35)
    public static void graceClearsOnNativeTeleportDeathWorldChangeAndEquipmentAndNeverProtectsHazards(GameTestHelper h) {
        Vec3 base=platform(h);
        var tele=player(h,base.add(0,2,8));var dead=player(h,base.add(4,2,8));
        var world=player(h,base.add(8,2,8));var equip=player(h,base.add(12,2,8));
        var hazard=player(h,base.add(16,2,8));var voidDamage=player(h,base.add(20,2,8));
        var unadmitted=player(h,base.add(0,0,16));
        var players=new ServerPlayer[]{tele,dead,world,equip,hazard,voidDamage,unadmitted};
        for(var p:players)equip(h,p,"heli");
        h.runAtTickTime(1,()->{for(var p:players)frame(p,1,true,false,false);});
        h.runAtTickTime(2,()->{
            for(var p:players)frame(p,2,false,false,false);
            for(int i=0;i<6;i++)h.assertTrue(Rac1ClankRuntime.graceDiagnostics(players[i]).covers(),
                    "Genuine released Heli did not start grace");
            h.assertTrue(Rac1ClankRuntime.graceDiagnostics(unadmitted).releaseTick()==-1,
                    "Equipping/unadmitted ordinary Jump release started grace");
        });
        h.runAtTickTime(3,()->{
            long epoch=Rac1ClankServer.session(tele).epoch();
            tele.connection.teleport(tele.getX(),tele.getY(),tele.getZ(),0,0);
            h.assertTrue(Rac1ClankServer.session(tele).epoch()>epoch
                            &&Rac1ClankRuntime.graceDiagnostics(tele).releaseTick()==-1,
                    "Native same-world teleport retained grace/serial namespace");
            h.assertTrue(dead.hurt(dead.damageSources().fellOutOfWorld(),1000)&&!dead.isAlive()
                            &&Rac1ClankRuntime.graceDiagnostics(dead).releaseTick()==-1,
                    "Death/void retained grace");
            var other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
            h.assertTrue(world.teleportTo(other,0,150,0,java.util.Set.of(),0,0)
                            &&world.level()==other&&Rac1ClankRuntime.graceDiagnostics(world).releaseTick()==-1,
                    "Actual world transfer retained release grace");
            equip(h,equip,"unequip");
            h.assertTrue(Rac1ClankRuntime.graceDiagnostics(equip).releaseTick()==-1,
                    "Equipment invalidation retained grace");
            h.assertTrue(hazard.hurt(hazard.damageSources().cactus(),2)&&hazard.getHealth()==18,
                    "Release grace protected cactus damage");
            h.assertTrue(voidDamage.hurt(voidDamage.damageSources().fellOutOfWorld(),2)
                            &&voidDamage.getHealth()==18,"Release grace protected void damage");
            done(h,players);
        });
    }

    @GameTest(template="empty",batch="clank_17_active_without_fresh_input",timeoutTicks=80)
    public static void genuineActiveHeliProtectsWithoutFreshInputButEndedGlideWithoutReleaseDoesNot(GameTestHelper h) {
        Vec3 base=platform(h);var active=player(h,base.add(0,.8,8));
        var ended=player(h,base.add(8,6,8));
        for(var p:new ServerPlayer[]{active,ended}){equip(h,p,"heli");p.fallDistance=12;}
        var falls=new Falls(active,ended);boolean[] activeNoFresh={false},nativeExpired={false};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingFallEvent> before=event->{
            if(event.getEntity()==active)activeNoFresh[0]=
                    Rac1ClankServer.session(active).motion().action()==Rac1ClankCapability.Action.GLIDE
                    &&!Rac1ClankRuntime.inputDiagnostics(active).heldProof()
                    &&Rac1ClankRuntime.graceDiagnostics(active).releaseTick()==-1;
            if(event.getEntity()==ended)nativeExpired[0]=
                    Rac1ClankServer.session(ended).motion().action()==Rac1ClankCapability.Action.IDLE
                    &&Rac1ClankRuntime.graceDiagnostics(ended).releaseTick()==-1;
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGH,before);
        for(int tick=1;tick<=55;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(var p:new ServerPlayer[]{active,ended}) {
                    if(t==1)fallFrame(p,1,true);
                    else tickWithoutInput(p);
                }
                if(t==55) {
                    h.assertTrue(activeNoFresh[0]&&nativeExpired[0],
                            "Did not isolate active vs inactive/no-grace native landing routes");
                    falls.protectedLanding(h,0);falls.damaged(h,1,17,18.01);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(before);
                    falls.close();done(h,active,ended);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_18_momentum_open",timeoutTicks=110)
    public static void landingMomentumAndAuthoritativeCorrectionHandOffToNativeFriction(GameTestHelper h) {
        momentumHandoff(h,false);
    }
    @GameTest(template="empty",batch="clank_19_momentum_wall",timeoutTicks=110)
    public static void landingHandoffCannotRestoreWallClippedMomentum(GameTestHelper h) {
        momentumHandoff(h,true);
    }
    private static void momentumHandoff(GameTestHelper h,boolean wall) {
        Vec3 base=platform(h);
        for(int x=-2;x<=22;x++)for(int z=23;z<=48;z++) {
            var at=BlockPos.containing(base).offset(x,0,z);
            h.getLevel().setBlock(at.below(),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<13;y++)h.getLevel().setBlock(at.above(y),Blocks.AIR.defaultBlockState(),3);
        }
        // Long admission needs a clear forward probe; short high/glide trajectories need a nearer wall.
        if(wall)for(int lane=0;lane<6;lane++)for(int x=lane*4-1;x<=lane*4+1;x++)
            for(int y=0;y<12;y++)h.getLevel().setBlock(
                    BlockPos.containing(base).offset(x,y,lane%3==1?5:2),Blocks.STONE.defaultBlockState(),3);
        var actors=new ServerPlayer[6];var nativeControls=new ServerPlayer[6];
        int[] landing=new int[6];boolean[] checked=new boolean[6],admitted=new boolean[6];
        for(int i=0;i<6;i++) {
            actors[i]=player(h,base.add(i*4,i%3==2?(wall?6:3):0,0));
            nativeControls[i]=player(h,base.add(i*4,0,30));
            equip(h,actors[i],i<3?"heli":"thruster");
        }
        for(int tick=1;tick<=85;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                for(int i=0;i<6;i++) {
                    var p=actors[i];var motion=Rac1ClankServer.session(p).motion();
                    var beforeAction=motion.action();Vec3 before=p.getDeltaMovement();
                    if(landing[i]==0&&i%3==1&&t<=4)p.move(MoverType.SELF,new Vec3(0,0,.12));
                    boolean airMove=landing[i]==0;
                    frame(p,t,airMove&&(i%3==2||t==4||t==5),airMove&&i%3!=2&&t<=5,airMove);
                    admitted[i]|=motion.action()==(i%3==0?Rac1ClankCapability.Action.HIGH_JUMP:
                            i%3==1?Rac1ClankCapability.Action.LONG_JUMP:Rac1ClankCapability.Action.GLIDE);
                    if(landing[i]>0&&landing[i]==t-1) {
                        nativeControls[i].doTick();
                        h.assertTrue(p.getDeltaMovement().distanceToSqr(nativeControls[i].getDeltaMovement())<1e-12,
                                "Landing did not return to ordinary native friction i="+i+" wall="+wall);
                        var correction=((Sink)p.connection).lastCorrection;
                        var expected=Rac1ClankNativeCollision.sourceVelocity(before);
                        h.assertTrue(correction!=null&&correction.state().action()==Rac1ClankCapability.Action.IDLE
                                        &&correction.state().velocity().equals(expected),
                                "Post-landing S2C checkpoint erased native momentum i="+i+" wall="+wall
                                        +" before="+before+" expected="+expected+" checkpoint="+correction);
                        checked[i]=true;
                        System.out.println("CLANK_MOMENTUM_HANDOFF i="+i+" wall="+wall+" before="+before
                                +" afterNative="+p.getDeltaMovement()+" correction="+correction.state().velocity());
                    }
                    if(landing[i]==0&&beforeAction!=Rac1ClankCapability.Action.IDLE
                            &&motion.action()==Rac1ClankCapability.Action.IDLE&&p.onGround()
                            &&Rac1ClankRuntime.diagnostics(p).controlled()) {
                        landing[i]=t;Vec3 delta=p.getDeltaMovement();
                        System.out.println("CLANK_MOMENTUM_LANDING_RAW i="+i+" wall="+wall+" native="+delta
                                +" controller="+motion.velocity()+" checkpoint="+((Sink)p.connection).lastCorrection);
                        h.assertTrue(motion.velocity().y()==0,"Landing retained downward controlled velocity");
                        h.assertTrue(wall?Math.abs(delta.z)<1e-9:delta.horizontalDistance()>.01,
                                "Landing lost open momentum or restored blocked momentum i="+i+" wall="+wall+" "+delta);
                        nativeControls[i].setDeltaMovement(delta);
                        System.out.println("CLANK_MOMENTUM_LANDING i="+i+" wall="+wall+" delta="+delta
                                +" source="+motion.velocity());
                    }
                }
                if(t==85) {
                    for(int i=0;i<6;i++)h.assertTrue(admitted[i]&&landing[i]>0&&checked[i],
                            "Missing high/long/glide native handoff case i="+i+" wall="+wall);
                    for(var p:nativeControls){Rac1ClankRuntime.invalidate(p);p.discard();}
                    done(h,actors);
                }
            });
        }
    }

    @GameTest(template="empty",batch="clank_20_creative_item_security",timeoutTicks=35)
    public static void creativeItemsUseActualHeldPackAndAuthoritativeModeWithoutConsumptionOrOperatorBypass(GameTestHelper h) {
        Vec3 base=platform(h);var p=player(h,base);
        var heli=com.github.giannigr.racmc.item.RacMcItems.CLANK_HELI_PACK.get();
        var thruster=com.github.giannigr.racmc.item.RacMcItems.CLANK_THRUSTER_PACK.get();
        var main=net.minecraft.world.InteractionHand.MAIN_HAND;var off=net.minecraft.world.InteractionHand.OFF_HAND;
        p.setItemInHand(main,heli.getDefaultInstance());
        var initial=Rac1ClankServer.session(p).snapshot(h.getLevel().getGameTime());
        for(var mode:new GameType[]{GameType.SURVIVAL,GameType.ADVENTURE,GameType.SPECTATOR}) {
            p.setGameMode(mode);
            h.assertTrue(!p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(main),main).consumesAction()
                            &&!Rac1ClankServer.equipCreative(p,main)
                            &&Rac1ClankServer.session(p).epoch()==initial.epoch()
                            &&!Rac1ClankServer.session(p).equipment().worn(),
                    "Non-Creative item use granted/mutated session: "+mode);
        }
        p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=true;
        h.assertTrue(!Rac1ClankServer.equipCreative(p,main),"Spoofed ability flag bypassed actual server game mode");
        p.getAbilities().instabuild=false;p.setGameMode(GameType.CREATIVE);
        h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(main),main).consumesAction(),
                "Native Creative Heli item use failed");
        var state=Rac1ClankServer.session(p);long heliEpoch=state.epoch();
        h.assertTrue(state.equipment().worn()&&state.equipment().selected()==Rac1ClankEquipment.Module.HELI
                        &&state.equipment().heliOwned()&&!state.equipment().thrusterOwned()
                        &&p.getItemInHand(main).is(heli)&&p.getItemInHand(main).getCount()==1,
                "Heli item granted wrong module or consumed its stack");
        var sink=(Sink)p.connection;
        h.assertTrue(sink.lastSnapshot!=null&&sink.lastSnapshot.epoch()==heliEpoch
                        &&sink.lastSnapshot.equipment().equals(state.equipment())
                        &&sink.lastCorrection!=null&&sink.lastCorrection.epoch()==heliEpoch,
                "Creative equip did not send authoritative equipment and correction snapshots");
        var forged=new net.minecraft.nbt.CompoundTag();forged.putInt("module",608);forged.putBoolean("worn",true);
        p.getItemInHand(main).set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(forged));
        h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(main),main).consumesAction()
                        &&state.epoch()==heliEpoch&&state.equipment().selected()==Rac1ClankEquipment.Module.HELI,
                "Repeated use restarted session or custom item data changed trusted module");
        p.setItemInHand(off,thruster.getDefaultInstance());
        h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(off),off).consumesAction()
                        &&state.epoch()>heliEpoch&&state.equipment().selected()==Rac1ClankEquipment.Module.THRUSTER
                        &&state.equipment().thrusterOwned()&&state.equipment().heliOwned()
                        &&p.getItemInHand(off).getCount()==1&&p.getItemInHand(main).getCount()==1,
                "Offhand Thruster switch lost prerequisite, authority or unchanged item stacks");
        long thrusterEpoch=state.epoch();p.setItemInHand(main,new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.STICK));
        h.assertTrue(!Rac1ClankServer.equipCreative(p,main)
                        &&!heli.use(h.getLevel(),p,main).getResult().consumesAction()
                        &&state.epoch()==thrusterEpoch,
                "Wrong held item/forged direct use granted equipment");
        p.setGameMode(GameType.SURVIVAL);p.setItemInHand(main,heli.getDefaultInstance());
        h.assertTrue(!Rac1ClankServer.equipCreative(p,main)&&state.epoch()==thrusterEpoch
                        &&state.equipment().selected()==Rac1ClankEquipment.Module.THRUSTER,
                "Creative-granted item could switch equipment from Survival");
        h.assertTrue(!Rac1ClankServer.configureTest(p.createCommandSourceStack().withPermission(0),p,
                        Rac1ClankEquipment.Module.HELI),
                "Creative item route escalated operator command permission");
        System.out.println("CLANK_CREATIVE_SECURITY epoch="+state.epoch()+" equipment="+state.equipment()
                +" mainCount="+p.getItemInHand(main).getCount()+" offCount="+p.getItemInHand(off).getCount());
        done(h,p);
    }

    @GameTest(template="empty",batch="clank_21_creative_item_lifecycle",timeoutTicks=40)
    public static void creativeEquipSwitchKeepsAirBudgetAndExistingTemporaryDeathLifecycle(GameTestHelper h) {
        Vec3 base=platform(h);var p=player(h,base);p.setGameMode(GameType.CREATIVE);
        var main=net.minecraft.world.InteractionHand.MAIN_HAND;
        p.setItemInHand(main,com.github.giannigr.racmc.item.RacMcItems.CLANK_HELI_PACK.get().getDefaultInstance());
        h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(main),main).consumesAction(),
                "Creative session setup did not equip");
        for(int tick=1;tick<=8;tick++) {
            final int t=tick;
            h.runAtTickTime(t,()->{
                frame(p,t,t==4||t==5,t<=5,false);
                if(t==8) {
                    var state=Rac1ClankServer.session(p);long old=state.epoch();
                    h.assertTrue(state.motion().spentAirJump()&&p.getY()>base.y+.1,
                            "No genuine airborne high action before item switch");
                    p.setItemInHand(main,com.github.giannigr.racmc.item.RacMcItems.CLANK_THRUSTER_PACK.get().getDefaultInstance());
                    h.assertTrue(p.gameMode.useItem(p,h.getLevel(),p.getItemInHand(main),main).consumesAction()
                                    &&state.epoch()>old&&state.motion().spentAirJump()
                                    &&state.motion().action()==Rac1ClankCapability.Action.IDLE,
                            "Creative pack switch replenished airborne action budget");
                    Rac1ClankRuntime.input(p,new Rac1ClankMovementWire.Input(old,999,
                            Rac1ClankInput.digital(true,true,true,false,false,true,false,0)));
                    h.assertTrue(state.motion().action()==Rac1ClankCapability.Action.IDLE,
                            "Old-epoch held input resurrected switched equipment action");
                    p.setGameMode(GameType.SURVIVAL);
                    h.assertTrue(state.equipment().worn()&&state.equipment().selected()==Rac1ClankEquipment.Module.THRUSTER,
                            "Mode change invented a new session lifetime policy");
                    h.assertTrue(p.hurt(p.damageSources().fellOutOfWorld(),1000)&&!p.isAlive()
                                    &&!state.equipment().worn(),
                            "Actual death did not clear Creative session companion");
                    System.out.println("CLANK_CREATIVE_LIFECYCLE deathCleared=true oldEpoch="+old+" newEpoch="+state.epoch());
                    done(h,p);
                }
            });
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
                    falls.protectedLanding(h,1);
                    falls.protectedLanding(h,2);
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
                    falls.protectedLanding(h,0);falls.close();
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
                    falls.protectedLanding(h,1);
                    falls.protectedLanding(h,2);
                    falls.damaged(h,3,12.1,12.81);
                    falls.damaged(h,4,12,12.81);
                    falls.damaged(h,5,12,12.81);
                    falls.damaged(h,6,12,12.01); // Thruster retains the existing debt policy.
                    falls.protectedLanding(h,7); // Grace survives ordinary native-travel handoff.
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
                    falls.protectedLanding(h,1);
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
        proof("geometry-initial",debt);
        System.out.println("GEOMETRY_WATER_INITIAL "+Rac1HydroWater.observe(debt)+" base="+base);
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
                if(t==1||t==65) {
                    proof("geometry-t"+t,debt);
                    System.out.println("GEOMETRY_WATER "+Rac1HydroWater.observe(debt));
                }
                h.assertTrue(low.getBoundingBox().maxY<=ceiling.getY()+1e-5,"Low ceiling penetrated");
                var proof=Rac1ClankRuntime.diagnostics(low);
                if(proof.actualY()>=0||low.onGround())h.assertTrue(
                        !Rac1ClankRuntime.floatingCredit(low),"Clipped/zero/grounded motion got credit");
                if(t==65) {
                    h.assertTrue(Math.abs(slab.getY()-(base.y+.5))<.01,"Slab used block-center ground");
                    h.assertTrue(debt.getHealth()==20,"Held Heli landing failed to negate the entire fall");
                    h.assertTrue(landings[0]==1&&distance[0]==0,
                            "Held Heli native fall event was not adjusted exactly once to zero: count="
                                    +landings[0]+" distance="+distance[0]+" pos="+debt.position()
                                    +" diag="+Rac1ClankRuntime.diagnostics(debt));
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
