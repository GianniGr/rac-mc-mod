package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1HydroContractTest {
    @Test void clientUsesConfiguredHeldKeysNotNativeSprintToggleOrHardcodedControl() throws Exception {
        var path=java.nio.file.Path.of(System.getProperty("racmc.projectDir"),
                "src/main/java/com/github/giannigr/racmc/client/clank/Rac1HydroClient.java");
        String code=java.nio.file.Files.readString(path);
        assertTrue(code.contains("options.keySprint.isDown()"));
        assertTrue(code.contains("options.keyJump.isDown()"));
        assertTrue(code.contains("options.keyShift.isDown()"));
        assertFalse(code.contains("isSprinting()"));assertFalse(code.contains("GLFW_KEY"));
    }
    private static Rac1HydroMotion.Input input(boolean thrust,boolean rise,boolean dive,double left,double forward,double yaw) {
        return new Rac1HydroMotion.Input(thrust,rise,dive,left,forward,yaw);
    }
    private static void step(Rac1HydroMotion motion,Rac1HydroMotion.Input input) {
        var step=motion.step(input,Vector.ZERO,false,false);
        if(step.controlled())motion.resolved(step.displacement());
    }
    @Test void recognizedSurfaceDoesNotRequireSprintAndWadingIsNotSwimming() {
        assertEquals(Rac1HydroWater.Mode.DRY,Rac1HydroWater.classify(false,false,false,false,0,4));
        assertEquals(Rac1HydroWater.Mode.WADING,Rac1HydroWater.classify(true,false,false,true,1,0));
        assertEquals(Rac1HydroWater.Mode.WADING,Rac1HydroWater.classify(true,false,false,false,.3,4));
        assertEquals(Rac1HydroWater.Mode.SURFACE,Rac1HydroWater.classify(true,false,false,false,.9,4));
        assertEquals(Rac1HydroWater.Mode.SURFACE,Rac1HydroWater.classify(true,false,true,false,.3,4));
        assertEquals(Rac1HydroWater.Mode.SUBMERGED,Rac1HydroWater.classify(true,true,false,true,2,0));
    }
    @Test void source60RampWithoutForwardAndLinearReleaseBraking() {
        var m=new Rac1HydroMotion();var thrust=input(true,false,false,0,0,0);
        for(int i=0;i<33;i++)step(m,thrust);
        assertEquals(7,m.velocity().z(),1e-9);assertEquals(0,m.velocity().y(),1e-9);
        step(m,Rac1HydroMotion.Input.RELEASED);
        assertEquals(Rac1HydroMotion.Stage.DRIFT,m.state().stage());
        assertEquals(7-8.0/60,m.velocity().length(),1e-9);
        for(int i=0;i<60;i++)step(m,Rac1HydroMotion.Input.RELEASED);
        assertFalse(m.controlled());
    }
    @Test void source60StrokeThresholdStrictlyGreaterThan20() {
        var m=new Rac1HydroMotion();var swim=input(true,false,false,0,1,0);
        for(int i=1;i<=20;i++)assertFalse(m.step(swim,new Vector(0,0,1),false,false).controlled(),"step "+i);
        var admitted=m.step(swim,new Vector(0,0,1),false,false);
        assertTrue(admitted.controlled());m.resolved(admitted.displacement());
    }
    @Test void riseWinsDiveAndSteeringTurnsRatherThanReverses() {
        var m=new Rac1HydroMotion();
        for(int i=0;i<60;i++)step(m,input(true,true,true,0,0,0));
        assertTrue(m.velocity().y()>6);assertTrue(m.state().pitch()<0);
        for(int i=0;i<33;i++)step(m,input(true,false,false,0,-1,0));
        assertTrue(Math.abs(m.state().yawRate())<=Math.toRadians(5)+1e-9);
        assertTrue(m.state().speed()>=0);
    }
    @Test void collisionResolvedMomentumDriftsAndPendingCollisionCannotSerialize() {
        var m=new Rac1HydroMotion();
        assertTrue(m.step(input(true,false,false,0,0,0),Vector.ZERO,false,false).controlled());
        assertThrows(IllegalStateException.class,m::state);
        m.resolved(new Vector(.001,0,0));
        step(m,Rac1HydroMotion.Input.RELEASED);
        assertFalse(m.controlled()); // .06 units/s brakes below zero, no requested forward residue.
        assertEquals(0,m.velocity().z(),1e-9);
    }
    @Test void freshSerialOnlyAndRateLimitedReplayCannotStartThrust() {
        var stream=new Rac1HydroWire.Stream(9);var held=input(true,false,false,0,0,0);
        assertFalse(stream.offer(new Rac1HydroWire.Input(8,0,held),1));
        assertTrue(stream.offer(new Rac1HydroWire.Input(9,0,held),1));
        assertTrue(stream.consume(2).thrust());assertTrue(stream.consume(2).thrust());
        assertFalse(stream.consume(3).thrust());
        assertFalse(stream.offer(new Rac1HydroWire.Input(9,0,held),3));
        assertTrue(stream.offer(new Rac1HydroWire.Input(9,1,held),3));
        assertTrue(stream.offer(new Rac1HydroWire.Input(9,2,held),3));
        assertFalse(stream.offer(new Rac1HydroWire.Input(9,3,held),3));
        assertFalse(stream.offer(new Rac1HydroWire.Input(9,3,held),4));
    }
    @Test void visualLifecycleIsIndependentFromThrustAndRestoresAfterFoldGap() {
        var v=new Rac1HydroVisual();v.target(true);
        assertEquals(Rac1HydroVisual.Phase.PUT_AWAY,v.phase());
        v.step();v.step();assertEquals(Rac1HydroVisual.Phase.GAP,v.phase());
        v.step();assertEquals(Rac1HydroVisual.Phase.UNFOLD,v.phase());
        for(int i=0;i<14;i++)v.step();
        assertEquals(Rac1HydroVisual.Phase.WET,v.phase());
        v.target(false);for(int i=0;i<6;i++)v.step();
        assertEquals(Rac1HydroVisual.Phase.RESTORE_GAP,v.phase());
        v.step();assertEquals(Rac1HydroVisual.Phase.DRY,v.phase());
    }
    @Test void boundedSemanticWireRoundTripsWithoutAssetsOrEntitlementCodec() throws Exception {
        var m=new Rac1HydroMotion();step(m,input(true,false,true,0,0,0));
        var s=new Rac1HydroWire.Snapshot(UUID.randomUUID(),1,2,3,0,true,true,true,
                Rac1ClankEquipment.Module.THRUSTER,Rac1HydroWater.Mode.SUBMERGED,
                Rac1HydroVisual.Phase.UNFOLD,2,new Vector(1,2,3),m.state());
        assertEquals(s,Rac1HydroWire.snapshot(Rac1HydroWire.encode(s)));
        var i=new Rac1HydroWire.Input(1,2,input(true,true,false,1,1,360));
        assertEquals(i,Rac1HydroWire.input(Rac1HydroWire.encode(i)));
        assertThrows(IOException.class,()->Rac1HydroWire.input(new byte[42]));
        assertThrows(IOException.class,()->Rac1HydroWire.snapshot(new byte[257]));
        assertThrows(IllegalArgumentException.class,()->input(true,false,false,Double.NaN,0,0));
        assertEquals(3,Rac1ClankEquipment.Module.values().length,"Hydro is not durable/selectable equipment");
    }
}
