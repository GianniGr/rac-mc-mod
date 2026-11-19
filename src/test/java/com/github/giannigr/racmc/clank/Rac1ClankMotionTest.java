package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankClock.Profile;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.*;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/** Native voxel-geometry stepping, NOT a substitute for a real ServerLevel/player test. */
class Rac1ClankMotionTest {
    private static Rac1ClankEquipment equipment(Module module) {
        return new Rac1ClankEquipment(true, true, module == Module.THRUSTER, true, module);
    }
    private static Rac1ClankInput input(boolean jump, boolean crouch, boolean forward) {
        return Rac1ClankInput.digital(jump, jump, crouch, false, false, forward, false, 0);
    }
    private static final class Scene {
        final Rac1ClankMotion motion;
        final List<VoxelShape> solids;
        AABB body;
        double apex, travelled;
        Vector initialVelocity = Vector.ZERO;
        boolean landed, began, blockedForward;
        Scene(Profile profile, List<VoxelShape> solids, double y) {
            motion = new Rac1ClankMotion(profile);
            this.solids = solids;
            body = new AABB(-.3, y, -.3, .3, y + 1.8, .3);
            apex = y;
        }
        Observation observation() {
            double floor = -Shapes.collide(Direction.Axis.Y, body, solids, -32);
            return new Observation(floor < 1e-6, floor, blockedForward, true, false, 2,
                    motion.action() == Action.IDLE ? initialVelocity : motion.velocity());
        }
        void tick(Rac1ClankEquipment equipment, Rac1ClankInput sample) {
            motion.input(sample);
            for (int substep = 0; substep < 3; substep++) {
                Step step = motion.step(equipment, observation());
                began |= step.started();
                if (!step.controlled()) continue;
                Vector v = step.displacement();
                double y = Shapes.collide(Direction.Axis.Y, body, solids, v.y());
                AABB next = body.move(0, y, 0);
                double x = Shapes.collide(Direction.Axis.X, next, solids, v.x());
                next = next.move(x, 0, 0);
                double z = Shapes.collide(Direction.Axis.Z, next, solids, v.z());
                body = next.move(0, 0, z);
                travelled += Math.hypot(x, z);
                apex = Math.max(apex, body.minY);
                boolean grounded = -Shapes.collide(Direction.Axis.Y, body, solids, -.01) < 1e-6;
                landed |= motion.resolved(new Vector(x, y, z), grounded);
                for (var solid : solids)
                    assertFalse(Shapes.joinIsNotEmpty(Shapes.create(body.deflate(1e-7)), solid,
                            net.minecraft.world.phys.shapes.BooleanOp.AND), "Native voxel penetration");
            }
        }
        void crouch(Rac1ClankEquipment equipment) {
            for (int i = 0; i < 3; i++) tick(equipment, input(false, true, false));
        }
    }
    private static VoxelShape floor() { return Shapes.box(-100, -1, -100, 100, 0, 100); }

    @Test void digitalMappingNormalizesDiagonalsAndUsesNativeRemapSemanticsNotSprint() {
        var digital = Rac1ClankInput.digital(true, false, true, true, false, true, false, 90);
        assertEquals(1, digital.magnitude(), 1e-12);
        assertEquals(Math.sqrt(.5), digital.forward(), 1e-12);
        assertEquals(-Math.sqrt(.5), digital.worldX(), 1e-12);
        assertEquals(Math.sqrt(.5), digital.worldZ(), 1e-12);
        assertTrue(digital.jumpHeld());
        assertEquals(0, Rac1ClankInput.digital(false,false,false,true,true,true,true,0).magnitude());
        assertThrows(IllegalArgumentException.class, () -> new Rac1ClankInput(false,false,false,2,0,0));
    }
    @Test void streamRejectsSpoofReplayExcessAndExpiresLostHeldKeys() {
        var stream = new Rac1ClankInput.Stream(7);
        assertFalse(stream.offer(6, 1, 100, input(true,true,false)));
        assertTrue(stream.offer(7, 1, 100, input(true,true,false)));
        assertFalse(stream.offer(7, 1, 100, input(false,false,false)));
        assertTrue(stream.offer(7, 2, 100, input(true,true,true)));
        assertFalse(stream.offer(7, 3, 100, input(true,true,true)));
        assertEquals(2, stream.acknowledgedSerial()); // rejected input is NOT acknowledged as applied
        assertFalse(stream.offer(7, 3, 101, input(true,true,true)));
        assertTrue(stream.at(104).jumpHeld());
        assertFalse(stream.at(105).jumpHeld());
        assertFalse(stream.offer(7, 4, 99, input(true,true,false)));
    }
    @Test void explicitSessionFixtureSelectsOnlyRequestedPackAndLegacyClientCannotGrant() {
        var session=new Rac1ClankSession(java.util.UUID.randomUUID(),1,Rac1ClankEquipment.EMPTY);
        session.testEquipment(Module.HELI);
        assertEquals(equipment(Module.HELI),session.snapshot(10).equipment());
        assertFalse(session.snapshot(10).equipment().thrusterOwned());
        assertFalse(session.intent(new Rac1ClankSession.Intent(1,1,
                Rac1ClankSession.IntentKind.EQUIPMENT,3,true)));
        assertEquals(Module.HELI,session.snapshot(10).equipment().selected());
        session.testEquipment(Module.THRUSTER);
        assertEquals(equipment(Module.THRUSTER),session.snapshot(10).equipment());
        session.testEquipment(Module.NONE);
        assertEquals(Rac1ClankEquipment.EMPTY,session.snapshot(10).equipment());
    }
    @Test void crouchThresholdAndJumpBufferAreSourceStepsNotMinecraftTicks() {
        var motion = new Rac1ClankMotion(Profile.ORIGINAL_60);
        var obs = new Observation(true,0,false,true,false,2,Vector.ZERO);
        motion.input(input(true,true,false));
        for (int i = 0; i < 6; i++) assertFalse(motion.step(equipment(Module.HELI), obs).controlled());
        var accepted = motion.step(equipment(Module.HELI), obs);
        assertTrue(accepted.started());
        assertEquals(Action.HIGH_JUMP, accepted.action());
        motion.resolved(Vector.ZERO, true);
        // Repeating the same held sample must not produce a second edge.
        motion.input(input(true,true,false));
        motion.cancel();
        assertEquals(Action.GLIDE, motion.step(equipment(Module.HELI),
                new Observation(false,2,false,true,false,2,Vector.ZERO)).action(),
                "Held Cross can still admit source glide; it must not repeat the high jump");
    }
    @Test void projectedForwardVelocityAndObstacleChooseLongNotSprintBoolean() {
        for (boolean blocked : new boolean[] {false,true}) {
            var scene = new Scene(Profile.ORIGINAL_60,List.of(floor()),0);
            scene.crouch(equipment(Module.HELI));
            scene.initialVelocity = new Vector(0,0,2.0 / 60);
            scene.blockedForward = blocked;
            scene.tick(equipment(Module.HELI), input(true,true,true));
            assertEquals(blocked ? Action.HIGH_JUMP : Action.LONG_JUMP, scene.motion.action());
        }
        var side = new Scene(Profile.ORIGINAL_60,List.of(floor()),0);
        side.crouch(equipment(Module.HELI));
        side.tick(equipment(Module.HELI),
                Rac1ClankInput.digital(true,true,true,true,false,false,false,0));
        assertFalse(side.began, "Side/back flip priority is not replaced by a pack jump");
    }
    @Test void absentUnownedWrongReadyHurtAndUnsupportedHostNeverStart() {
        for (Rac1ClankEquipment equipment : List.of(Rac1ClankEquipment.EMPTY,
                new Rac1ClankEquipment(true,false,false,true,Module.HELI))) {
            var scene = new Scene(Profile.ORIGINAL_60,List.of(floor()),0);
            scene.crouch(equipment);
            scene.tick(equipment,input(true,true,false));
            assertFalse(scene.began);
        }
        for (Observation obs : List.of(
                new Observation(true,0,false,true,false,3,Vector.ZERO),
                new Observation(true,0,false,false,false,2,Vector.ZERO),
                new Observation(true,0,false,true,true,2,Vector.ZERO))) {
            var motion = new Rac1ClankMotion(Profile.ORIGINAL_60);
            motion.input(input(true,true,false));
            for (int i=0;i<9;i++) assertFalse(motion.step(equipment(Module.HELI),obs).controlled());
        }
    }
    @Test void highGoldenFirstTakeoffAndHeliLiftBoundaryNotAnInstantGenericBoost() {
        for (Module module : List.of(Module.HELI,Module.THRUSTER)) {
            var motion = new Rac1ClankMotion(Profile.ORIGINAL_60);
            var obs = new Observation(true,0,false,true,false,2,Vector.ZERO);
            motion.input(input(false,true,false));
            for(int i=0;i<9;i++) motion.step(equipment(module),obs);
            motion.input(input(true,true,false));
            double previousY=0;
            for(int phase=0;phase<=35;phase++) {
                var step=motion.step(equipment(module),obs);
                if(phase<9) assertEquals(0,step.displacement().y());
                if(phase==9) {
                    // Independent source arithmetic: height ramp, sqrt(2*h*29.7)/60,
                    // minus29.7/3600, plus Thruster first151/3600. No controller helper oracle.
                    double h=module==Module.HELI ? 1.95 : .1 + 10*(.1/14);
                    double expected=Math.sqrt(2*h*29.7)/60 - 29.7/3600
                            + (module==Module.THRUSTER ? 151.0/3600 : 0);
                    assertEquals(expected,step.displacement().y(),2e-8);
                }
                if(module==Module.HELI && phase==35)
                    assertEquals((48-29.7)/3600, step.displacement().y()-previousY,2e-8);
                previousY=step.displacement().y();
                motion.resolved(step.displacement(),false);
                obs=new Observation(false,2,false,true,false,2,motion.velocity());
            }
        }
    }
    @Test void nativeVoxelHighAndLongLandWithDistinctPackTrajectoriesNoPenetration() {
        for (Profile profile:Profile.values()) for(Module module:List.of(Module.HELI,Module.THRUSTER)) {
            var high=new Scene(profile,List.of(floor()),0);
            high.crouch(equipment(module));
            high.tick(equipment(module),input(true,true,false));
            for(int i=0;i<50 && !high.landed;i++)
                high.tick(equipment(module),input(false,false,false));
            assertTrue(high.began);
            assertTrue(high.landed);
            assertTrue(high.apex>1, "Scripted high lift must actually leave the floor");
            assertEquals(0,high.body.minY,1e-6);
            var longJump=new Scene(profile,List.of(floor()),0);
            longJump.crouch(equipment(module));
            longJump.initialVelocity=new Vector(0,0,2.0/profile.hz());
            longJump.tick(equipment(module),input(true,true,true));
            for(int i=0;i<50 && !longJump.landed;i++)
                longJump.tick(equipment(module),input(false,false,true));
            assertTrue(longJump.landed);
            assertTrue(longJump.travelled>2, "Distance comes from the source long-jump path");
            assertTrue(high.apex>longJump.apex);
        }
    }
    @Test void ceilingConsumesLiftBudgetAndWallSlabGeometryCannotAddEnergy() {
        for(Module module:List.of(Module.HELI,Module.THRUSTER)) {
            var ceiling=new Scene(Profile.ORIGINAL_60,
                    List.of(floor(),Shapes.box(-10,2.1,-10,10,3,10)),0);
            ceiling.crouch(equipment(module));
            ceiling.tick(equipment(module),input(true,true,false));
            for(int i=0;i<50 && !ceiling.landed;i++)
                ceiling.tick(equipment(module),input(false,false,false));
            assertTrue(ceiling.apex<=.300001);
            assertTrue(ceiling.landed);
            var slab=new Scene(Profile.ORIGINAL_60,
                    List.of(floor(),Shapes.box(-2,0,-2,2,.5,2),Shapes.box(-2,.5,2,2,4,3)),.5);
            slab.crouch(equipment(module));
            slab.initialVelocity=new Vector(0,0,2.0/60);
            slab.tick(equipment(module),input(true,true,true));
            for(int i=0;i<50 && !slab.landed;i++) slab.tick(equipment(module),input(false,false,true));
            assertTrue(slab.body.maxZ<=2.000001);
            assertTrue(slab.landed);
            assertEquals(.5,slab.body.minY,1e-6);
        }
    }
    @Test void strictGlideReleaseChecksBothHeldViewsAndSourceProfileThreshold() {
        for(Profile profile:Profile.values()) {
            var motion=new Rac1ClankMotion(profile);
            var obs=new Observation(false,10,false,true,false,3,new Vector(0,-5.0/profile.hz(),0));
            motion.input(new Rac1ClankInput(false,true,false,0,1,0));
            var first=motion.step(equipment(Module.HELI),obs);
            assertEquals(Action.GLIDE,first.action());
            assertEquals(-2.16/profile.hz(),first.displacement().y(),2e-8);
            motion.resolved(first.displacement(),false);
            motion.input(Rac1ClankInput.RELEASED);
            for(int i=1;i<=profile.ticks(30);i++) {
                var step=motion.step(equipment(Module.HELI),obs);
                assertEquals(Action.GLIDE,step.action());
                motion.resolved(step.displacement(),false);
            }
            assertFalse(motion.step(equipment(Module.HELI),obs).controlled());
        }
    }
    @Test void cancellationAndEquipmentTogglesCannotCreateAirJumpOrRefillUnresolvedBudget() {
        var motion=new Rac1ClankMotion(Profile.ORIGINAL_60);
        var ground=new Observation(true,0,false,true,false,2,Vector.ZERO);
        motion.input(input(true,true,false));
        for(int i=0;i<6;i++) motion.step(equipment(Module.HELI),ground);
        var step=motion.step(equipment(Module.HELI),ground);
        assertThrows(IllegalStateException.class,()->motion.step(equipment(Module.HELI),ground));
        motion.resolved(step.displacement(),false);
        motion.cancel();
        motion.input(input(false,true,false));
        motion.input(input(true,true,false));
        var air=new Observation(false,1,false,true,false,2,new Vector(0,.01,0));
        assertFalse(motion.step(equipment(Module.THRUSTER),air).controlled());
        assertTrue(motion.spentAirJump());
    }
    @Test void episodeFallLedgerPreservesOldDebtAndCancellationCannotGiveToggleImmunity() {
        var ledger=new Rac1ClankFallEpisode();
        ledger.begin(12);
        ledger.moved(-2);
        ledger.moved(3);
        ledger.moved(-4);
        assertThrows(IllegalStateException.class,()->ledger.begin(0));
        assertEquals(12,ledger.landingDistance(18));
        assertFalse(ledger.active());
        assertEquals(9,ledger.landingDistance(9)); // unrelated next fall stays vanilla
        ledger.begin(12);
        ledger.moved(-6);
        assertEquals(18,ledger.cancelDistance(0)); // native ascent reset must not erase the debt
        assertEquals(18,ledger.landingDistance(18));
    }
    @Test void heliHeldLandingProtectsWholeFallButReleaseAndCancelRestoreWithoutDoubleCharging() {
        var ledger=new Rac1ClankFallEpisode();
        ledger.begin(12);ledger.moved(-2);
        assertEquals(0,ledger.heliLandingDistance(14,true));
        assertEquals(7,ledger.heliLandingDistance(7,true)); // no stale second landing
        ledger.begin(12);ledger.moved(-2);
        assertEquals(14,ledger.heliLandingDistance(14,false));
        ledger.begin(12);ledger.moved(-2);
        assertEquals(14,ledger.cancelDistance(14)); // native already includes glide descent
        assertEquals(14,ledger.cancelDistance(14));
    }
}
