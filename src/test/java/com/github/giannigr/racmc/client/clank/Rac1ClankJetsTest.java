package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.clank.ClankFixtures;
import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import com.github.giannigr.racmc.clank.Rac1ClankAv;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.effect.EffectSignal.Point;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Rac1ClankJetsTest {
    private static final UUID ID=UUID.randomUUID();
    private static final Point ZERO=new Point(0,0,0);
    private static EffectRuntime runtime(){return new EffectRuntime(EffectClock.Profile.NTSC,2048);}
    private static Rac1ClankJets jets(EffectRuntime r) {
        return new Rac1ClankJets(r,type->new EffectAssetRef("a".repeat(40),1,EffectAssetRef.Bank.PARTICLE,type));
    }
    private static Rac1ClankJets.Request hydro(double speed) {
        return new Rac1ClankJets.Request(1,new Rac1ClankAv.Selection(Rac1ClankAv.Loop.HYDRO,true,false,false),
                List.of(new Point(-1,0,0),new Point(1,0,0)),List.of(),speed,0,0,ZERO,10);
    }
    @Test void fullThrustEmitsFourBubblesFromAlternatingOriginalOutletsNotMouthHandsOrSurface() {
        var r=runtime();var fx=jets(r);fx.step(Map.of(ID,hydro(7)),ZERO);assertEquals(4,fx.emitted(34));
        var draw=r.snapshot(ZERO,1);assertEquals(4,draw.size());
        for(var mouth:hydro(7).hydro())
            assertEquals(2,draw.stream().filter(d->d.position().equals(mouth)).count());
        fx.step(Map.of(ID,hydro(7)),ZERO);assertEquals(4,fx.emitted(34));
        fx.step(Map.of(),ZERO);assertEquals(0,fx.emitted(34));assertTrue(fx.particles()>0,"Old bubbles age normally");
        for(int i=0;i<150;i++)fx.step(Map.of(),ZERO);
        assertEquals(0,fx.particles());assertEquals(0,r.size());
    }
    @Test void deepSubmergedThrustDoesNotRequireAnObservedSurfacePlane() {
        var r=runtime();var fx=jets(r);var known=hydro(7);
        var deep=new Rac1ClankJets.Request(known.epoch(),known.state(),known.hydro(),known.thruster(),
                known.speed(),known.yaw(),known.pitch(),known.displacement(),Double.NaN);
        fx.step(Map.of(ID,deep),ZERO);
        assertEquals(4,fx.emitted(34),"Unknown surface must not silence real submerged thrust");
        for(int i=0;i<20;i++)fx.step(Map.of(),ZERO);
        assertEquals(4,fx.particles(),"Unknown surface must not invent a premature surface pop");
        assertTrue(r.snapshot(ZERO,1).stream().allMatch(d->Double.isFinite(d.position().y())));
        for(int i=0;i<150;i++)fx.step(Map.of(),ZERO);
        assertEquals(0,r.size(),"Deep bubbles must still expire within the original lifetime");
    }
    @Test void speedCadencePersistsAcrossExitReentryAndMissingAssetsDoNotSubstituteSprites() {
        var r=runtime();var fx=jets(r);
        fx.step(Map.of(ID,hydro(0)),ZERO);assertEquals(4,fx.emitted(34));
        fx.step(Map.of(ID,new Rac1ClankJets.Request(1,Rac1ClankAv.Selection.NONE,List.of(),List.of(),
                0,0,0,ZERO,Double.NaN)),ZERO);
        fx.step(Map.of(ID,hydro(0)),ZERO);assertEquals(0,fx.emitted(34),"Reentry reset original timer");
        for(int i=0;i<4;i++)fx.step(Map.of(ID,hydro(0)),ZERO);
        assertEquals(4,fx.emitted(34));
        var missing=new Rac1ClankJets(runtime(),type->null);
        missing.step(Map.of(ID,hydro(7)),ZERO);assertEquals(0,missing.particles());
    }
    @Test void twoFlamesGrowBurnThenStopNewSmokeAndShrinkWithoutInstantRepressRestart() {
        var r=runtime();var fx=jets(r);
        var frame=new Rac1ClankNozzles.Frame(ZERO,new Point(1,0,0),new Point(0,1,0),new Point(0,0,1));
        var on=new Rac1ClankJets.Request(1,new Rac1ClankAv.Selection(null,false,true,false),List.of(),
                List.of(frame,frame),0,0,0,ZERO,Double.NaN);
        var off=new Rac1ClankJets.Request(1,Rac1ClankAv.Selection.NONE,List.of(),List.of(frame,frame),
                0,0,0,ZERO,Double.NaN);
        fx.step(Map.of(ID,on),ZERO);assertTrue(fx.flames().isEmpty());
        fx.step(Map.of(ID,on),ZERO);assertEquals(.5,fx.flames().getFirst().scale());
        fx.step(Map.of(ID,on),ZERO);assertEquals(1,fx.flames().getFirst().scale());
        fx.step(Map.of(ID,on),ZERO);assertEquals(2,fx.emitted(23));assertEquals(2,fx.emitted(21));
        fx.step(Map.of(ID,on),ZERO);assertEquals(8,fx.emitted(23));
        fx.step(Map.of(ID,off),ZERO);assertEquals(0,fx.emitted(23));
        for(int i=0;i<8;i++){fx.step(Map.of(ID,on),ZERO);assertEquals(0,fx.emitted(23));}
        assertTrue(fx.flames().isEmpty());
    }
    @Test void animatedChainEndpointsUseMountOnceAndThrusterPairsNeverGuessMissingAttachments() {
        var base=ClankFixtures.model(609);
        var model=new Rac1AnimatedAsset(609,"test",base.mesh(),base.skeleton(),base.animations(),base.textures(),
                new int[][]{{0,1,2,3,4},{0,5,6,7,8}});
        float[] chain=new float[160];
        for(int i=0;i<10;i++)new Matrix4f().translation(i,0,0).get(chain,i*16);
        var outlets=Rac1ClankNozzles.hydro(model,chain,new Matrix4f().translation(10,20,30),new Point(100,0,0));
        assertEquals(new Point(114,20,30),outlets.getFirst());
        assertEquals(new Point(118,20,30),outlets.get(1));
        chain[4*16+13]=2;
        assertEquals(22,Rac1ClankNozzles.hydro(model,chain,new Matrix4f().translation(10,20,30),ZERO).getFirst().y());
        assertTrue(Rac1ClankNozzles.hydro(base,chain,new Matrix4f(),ZERO).isEmpty());
        var thruster=ClankFixtures.model(608);
        var pack=new Rac1AnimatedAsset(608,"pair",thruster.mesh(),thruster.skeleton(),thruster.animations(),
                thruster.textures(),new int[][]{{10},{11},{13},{14}});
        float[] pose=new float[240];
        for(int i=0;i<15;i++)new Matrix4f().get(pose,i*16);
        pose[10*16+14]=1;pose[13*16+14]=1;pose[14*16+12]=2;pose[13*16+12]=2;
        var frames=Rac1ClankNozzles.thruster(pack,pose,new Matrix4f(),ZERO);
        assertEquals(2,frames.size());assertEquals(new Point(2,0,0),frames.getFirst().at());
        assertEquals(new Point(0,0,1),frames.getFirst().into());
    }
    @Test void angledNativeBodyMouthsAgreeAcrossVisibleAndFirstPersonCameraFrames() {
        var base=ClankFixtures.model(609);
        var model=new Rac1AnimatedAsset(609,"outlets",base.mesh(),base.skeleton(),base.animations(),base.textures(),
                new int[][]{{0,1,2,3,4},{0,5,6,7,8}});
        var left=new Vector3f(.18f,-.06f,-.26f);
        var right=new Vector3f(.20f,-.07f,.26f);
        float[] chain=new float[160];
        new Matrix4f().translation(left).rotateY(.4f).get(chain,4*16);
        new Matrix4f().translation(right).rotateY(-.4f).get(chain,8*16);
        var body=new ModelPart(List.of(),Map.of());
        body.y=3.2f;body.xRot=.5f;
        var root=new Matrix4f().rotationY(.63f).rotateX(-1.15f).translate(0,-1,.3f)
                .scale(-1,-1,1).scale(.9375f).translate(0,-1.501f,0);
        Point wearer=new Point(210.5,35.25,-110.125);
        var expected=List.of(left,right).stream().map(joint->{
            // Independent pack axes, torso anchor and native body part transform.
            var local=new Vector3f(-joint.z,-joint.y,-joint.x).mul(16f/15)
                    .add(0,6f/16,2f/16).rotateX(.5f).add(0,3.2f/16,0);
            root.transformPosition(local);
            return wearer.add(local.x,local.y,local.z);
        }).toList();
        var request=hydro(7).withHydro(List.of());
        var runtime=runtime();var jets=jets(runtime);
        jets.step(Map.of(ID,request),ZERO);
        assertEquals(0,jets.emitted(34),"Missing frame cannot emit from an old mouth");
        var thirdPersonCamera=wearer.add(2,1,-3);
        var firstPersonCamera=wearer.add(.125,.58,.375);
        for(Point camera:List.of(thirdPersonCamera,firstPersonCamera)) {
            var pose=new PoseStack();
            pose.translate(wearer.x()-camera.x(),wearer.y()-camera.y(),wearer.z()-camera.z());
            pose.mulPose(root);
            Rac1ClankSteveMount.attach(pose,body); // Used by both the visible layer and hidden sampler.
            var mouths=Rac1ClankNozzles.hydro(model,chain,pose.last().pose(),camera);
            assertEquals(2,mouths.size());
            for(int i=0;i<2;i++)assertEquals(0,expected.get(i).distanceSquared(mouths.get(i)),1e-12);
            jets.step(Map.of(ID,request.withHydro(mouths)),camera);
            assertEquals(4,jets.emitted(34));
            var draw=runtime.snapshot(camera,1);
            for(var mouth:mouths)assertEquals(2,draw.stream().filter(d->d.position().equals(mouth)).count());
            jets.clear();
        }
    }
    @Test void poolIsBoundedAndClearRemovesAllPresentationOnlyState() {
        var r=new EffectRuntime(EffectClock.Profile.NTSC,32);var fx=jets(r);
        for(int i=0;i<200;i++)fx.step(Map.of(ID,hydro(7)),ZERO);
        assertTrue(r.size()<=32);assertTrue(fx.particles()<=32);
        fx.clear();assertEquals(0,r.size());assertEquals(0,fx.particles());assertTrue(fx.flames().isEmpty());
    }
    @Test void source60CadenceDoesNotDependOnTwentySixtyOrHighRenderFps() {
        for(int fps:new int[]{20,60,144}) {
            var r=runtime();var fx=jets(r);int[] bubbles={0};r.advance(0,false);
            for(int frame=1;frame<=fps*2;frame++)r.advance(frame/(double)fps,false,()->{
                fx.step(Map.of(ID,hydro(7)),ZERO);bubbles[0]+=fx.emitted(34);
            });
            assertEquals(480,bubbles[0],"Render FPS changed source60 bubble dispatch");
        }
    }
}
