package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.clank.*;
import com.github.giannigr.racmc.client.effect.Rac1EffectClient;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

/** Client-only cosmetics. Semantic authority is read-only; no input, movement or media IO here. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ClankAvClient {
    private record Capture(long tick,int model,List<Point> outlets,List<Rac1ClankNozzles.Frame> frames) {}
    private static final Map<UUID,Capture> CAPTURES=new HashMap<>();
    private static final Set<UUID> HYDRO_FRAME=new HashSet<>();
    private record HydroTrace(long epoch,Rac1HydroWater.Mode water,Rac1HydroMotion.Stage stage,
                              Rac1HydroVisual.Phase visual,boolean controlled,boolean thrust,
                              int outlets,boolean sprite) {}
    private static final Map<UUID,HydroTrace> HYDRO_TRACE=new HashMap<>();
    private static final Map<UUID,Rac1ClankJets.Request> REQUESTS=new LinkedHashMap<>();
    private record Feet(long tick,List<Point> points) {}
    private static final Map<UUID,Feet> FEET=new HashMap<>();
    private static final Rac1ClankTouchdown TOUCHDOWN=new Rac1ClankTouchdown();
    private static Rac1ClankFootMotes footMotes;
    private static EffectRuntime runtime;
    private static Rac1ClankJets jets;
    private static Object world;
    private static long generation=-1;
    private Rac1ClankAvClient() {}
    static void captureFeet(UUID actor,List<Point> points) {
        var level=Minecraft.getInstance().level;
        if(level!=null&&(FEET.size()<64||FEET.containsKey(actor)))
            FEET.put(actor,new Feet(level.getGameTime(),List.copyOf(points)));
    }
    static void capture(UUID actor,Rac1AnimatedAsset model,float[] chain,Matrix4f mount) {
        var mc=Minecraft.getInstance();if(mc.level==null||CAPTURES.size()>=64&&!CAPTURES.containsKey(actor))return;
        Point camera=point(mc.gameRenderer.getMainCamera().getPosition());
        CAPTURES.put(actor,new Capture(mc.level.getGameTime(),model.classNumber(),
                Rac1ClankNozzles.hydro(model,chain,mount,camera),
                Rac1ClankNozzles.thruster(model,chain,mount,camera)));
        if(model.classNumber()==609)HYDRO_FRAME.add(actor);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();var capability=Rac1EffectClient.localPresentation();
        if(world!=mc.level||capability==null||capability.generation()!=generation) {
            clear();world=mc.level;
            if(capability==null||world==null)return;
            generation=capability.generation();
            runtime=new EffectRuntime(EffectClock.Profile.NTSC,2048); // Explicit source60 host adaptation.
            Map<Integer,com.github.giannigr.racmc.asset.effect.EffectAssetRef> sprites=new HashMap<>();
            for(int type:new int[]{21,23,34}) {
                var asset=capability.particle(type);if(asset!=null)sprites.put(type,asset);
            }
            jets=new Rac1ClankJets(runtime,sprites::get);
            footMotes=new Rac1ClankFootMotes(runtime,capability.profile()==EffectClock.Profile.NTSC
                    ?capability.particle(28):null);
        }
        if(mc.level==null||mc.player==null||jets==null)return;
        long now=mc.level.getGameTime();REQUESTS.clear();
        Set<UUID> actors=new HashSet<>();
        var players=new ArrayList<>(mc.level.players());
        players.sort(Comparator.comparingDouble(p->p.distanceToSqr(mc.player)));
        for(var p:players) {
            if(actors.size()>=16)break;
            var id=p.getUUID();var clank=Rac1ClankClient.VIEWS.get(id);
            if(clank==null||!clank.equipment().worn()||p.distanceToSqr(mc.player)>48*48)continue;
            var h=Rac1HydroClient.view(id);
            boolean fresh=Rac1ClankClient.VIEWS.serverTime(id,now)-clank.serverTick()<=4
                    &&(h==null||now-h.localTick()<=4);
            boolean hydroFresh=h!=null&&Rac1HydroPresentation.fresh(clank,h.state(),h.localTick(),now);
            var selected=Rac1ClankAv.select(clank,h==null?null:h.state(),(fresh||hydroFresh)&&p.isAlive()
                    &&!p.isSpectator()&&!p.isRemoved(),p.getDeltaMovement().y);
            // Only the NTSC flame callback/FX slots were verified. Do not assert PAL equivalence.
            if(selected.flames()&&capability.profile()!=EffectClock.Profile.NTSC)
                selected=new Rac1ClankAv.Selection(selected.loop(),false,false,selected.glide());
            actors.add(id);
            boolean feetValid=capability.profile()==EffectClock.Profile.NTSC&&fresh&&p.isAlive()
                    &&!p.isSpectator()&&!p.isRemoved()&&!p.isInvisible()&&!p.isPassenger()&&!p.isFallFlying()
                    &&!p.isInWaterOrBubble()&&!p.isInLava()&&(h==null||!h.state().wet())
                    &&!p.getBlockStateOn().is(net.minecraft.tags.BlockTags.ICE);
            if(!feetValid||!p.onGround())footMotes.cancel(id);
            int burst=TOUCHDOWN.observe(now,clank,feetValid,p.onGround(),p.getDeltaMovement().y,
                    p.getDeltaMovement().horizontalDistanceSqr(),point(p.position()));
            if(burst>0)footMotes.touchdown(id,clank.epoch()^clank.actionSerial()^now,burst);
            long epoch=Rac1ClankAv.presentationEpoch(clank,h==null?null:h.state());
            var captured=CAPTURES.get(id);
            boolean visible=captured!=null&&now-captured.tick()<=2&&!p.isInvisible();
            if(p==mc.player&&h!=null&&Rac1HydroTrace.LOG.enabled()) {
                String decision=h.state().water()+":"+h.state().motion().stage()+":"+selected.hydroJets()
                        +":"+hydroFresh+":"+visible+":"+(captured==null?0:captured.model());
                final boolean powered=selected.hydroJets();
                Rac1HydroTrace.LOG.transition("client-emitter",decision,()->"water="+h.state().water()
                        +" controlled="+h.state().controlled()+" stage="+h.state().motion().stage()
                        +" aerialFresh="+fresh+" hydroFresh="+hydroFresh+" selected="+powered
                        +" captureVisible="+visible+" model="+(captured==null?0:captured.model())
                        +" outlets="+(captured==null?0:captured.outlets().size())
                        +" sprite34="+(capability.particle(34)!=null)+" pose="+p.getPose());
            }
            var hydro=h==null?null:h.state();
            if(hydro!=null&&hydro.access()) {
                var trace=new HydroTrace(hydro.epoch(),hydro.water(),hydro.motion().stage(),hydro.visual(),
                        hydro.controlled(),selected.hydroJets(),visible&&captured.model()==609
                        ?captured.outlets().size():0,capability.particle(34)!=null);
                if(!trace.equals(HYDRO_TRACE.put(id,trace)))
                    RacMcMod.LOGGER.debug("Clank Hydro presentation {}: {}",id,trace);
            }
            double speed=hydro==null?0:hydro.motion().speed();
            var v=hydro==null?Rac1ClankMotion.Vector.ZERO:hydro.motion().effective();
            REQUESTS.put(id,new Rac1ClankJets.Request(epoch,selected,
                    List.of(), // Hydro geometry is supplied by the current frame, not this tick.
                    visible&&captured.model()==608?captured.frames():List.of(),speed,
                    hydro==null?0:hydro.motion().yaw(),hydro==null?0:hydro.motion().pitch(),
                    new Point(v.x()/60,v.y()/60,v.z()/60),
                    selected.hydroJets()?surface(p.blockPosition()):Double.NaN));
        }
        CAPTURES.entrySet().removeIf(e->now-e.getValue().tick()>2);
        FEET.entrySet().removeIf(e->now-e.getValue().tick()>2||!actors.contains(e.getKey()));
        TOUCHDOWN.retain(actors);footMotes.retain(actors);Rac1ClankAfterimages.retain(actors);
        HYDRO_TRACE.keySet().retainAll(actors);
    }
    private static double surface(BlockPos at) {
        var level=Minecraft.getInstance().level;
        for(int i=0;i<32;i++) {
            var pos=at.above(i);var fluid=level.getFluidState(pos);
            if(!fluid.is(FluidTags.WATER))return Double.NaN;
            if(!level.getFluidState(pos.above()).is(FluidTags.WATER))
                return pos.getY()+fluid.getHeight(level,pos);
        }
        return Double.NaN; // No invented pop plane in an unobserved deep volume.
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        if(runtime==null||mc.level!=world||mc.level==null){HYDRO_FRAME.clear();return;}
        var capability=Rac1EffectClient.localPresentation();
        if(capability==null||capability.generation()!=generation){clear();return;}
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Point camera=point(event.getCamera().getPosition());
        // The current render's entity layers ran before AFTER_PARTICLES. Never reuse a mouth
        // from a previous frame or camera mode. The hidden first-person wearer has no body layer.
        var player=mc.player;
        if(player!=null&&mc.getCameraEntity()==player&&mc.options.getCameraType().isFirstPerson()) {
            var id=player.getUUID();var request=REQUESTS.get(id);
            HYDRO_FRAME.remove(id);
            if(request!=null&&request.state().hydroJets()) {
                float bodyPartial=event.getPartialTick().getGameTimeDeltaPartialTick(
                        !mc.level.tickRateManager().isEntityFrozen(player));
                Rac1HydroAttachmentSampler.capture(player,Rac1ClankClient.VIEWS.get(id),
                        bodyPartial,event.getCamera().getPosition());
            }
        }
        Map<UUID,Rac1ClankJets.Request> frameRequests=HYDRO_FRAME.isEmpty()?REQUESTS:new LinkedHashMap<>(REQUESTS);
        for(var id:HYDRO_FRAME) {
            var capture=CAPTURES.get(id);var request=REQUESTS.get(id);
            if(capture!=null&&capture.model()==609&&request!=null&&request.state().hydroJets())
                frameRequests.put(id,request.withHydro(capture.outlets()));
        }
        HYDRO_FRAME.clear();
        Map<UUID,List<Point>> feet=new HashMap<>();
        FEET.forEach((id,capture)->{
            if(mc.level.getGameTime()-capture.tick()<=2)feet.put(id,capture.points());
        });
        var advance=runtime.advance((mc.level.getGameTime()+(double)partial)/20,mc.isPaused(),()->{
            jets.step(frameRequests,camera);footMotes.step(feet);
        });
        if(advance.discontinuity()){jets.clear();footMotes.clear();TOUCHDOWN.clear();Rac1ClankAfterimages.clear();return;}
        Rac1EffectClient.renderLocal(generation,event.getPoseStack(),runtime.snapshot(camera,advance.interpolation()),
                camera,event.getCamera().rotation());
        Rac1ClankFlameRenderer.draw(event.getPoseStack(),jets.flames(),capability,camera);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();world=null;}
    private static void clear() {
        CAPTURES.clear();HYDRO_FRAME.clear();REQUESTS.clear();HYDRO_TRACE.clear();
        FEET.clear();TOUCHDOWN.clear();Rac1ClankAfterimages.clear();
        if(footMotes!=null)footMotes.clear();footMotes=null;
        if(jets!=null)jets.clear();jets=null;runtime=null;generation=-1;
    }
    private static Point point(net.minecraft.world.phys.Vec3 p){return new Point(p.x,p.y,p.z);}
}
