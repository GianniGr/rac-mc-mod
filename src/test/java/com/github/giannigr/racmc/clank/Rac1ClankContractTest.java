package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankCapability.*;
import com.github.giannigr.racmc.clank.Rac1ClankClock.Profile;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.asset.*;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Rac1ClankContractTest {
    private static Rac1ClankEquipment heli(boolean owned) {
        return new Rac1ClankEquipment(true, owned, false, true, Module.HELI);
    }
    @Test void defaultNeverGrantsAndVisibilityDoesNotMeanOwnership() {
        var ready = new Context(true, true, 2, 2);
        for (Action action : Action.values()) {
            assertFalse(Rac1ClankCapability.permits(Rac1ClankEquipment.EMPTY, ready, action));
            assertFalse(Rac1ClankCapability.permits(heli(false), ready, action));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new Rac1ClankEquipment(true, false, false, true, Module.THRUSTER));
    }
    @Test void perActionReadinessSentinelAndHydroExclusion() {
        for (int slot : new int[] {0, 1, 3}) {
            var swapping = new Context(true, true, slot, 2);
            assertEquals(-1, swapping.readyModule());
            assertTrue(Rac1ClankCapability.permits(heli(true), swapping, Action.GLIDE));
            assertFalse(Rac1ClankCapability.permits(heli(true), swapping, Action.LONG_JUMP));
            assertFalse(Rac1ClankCapability.permits(heli(true), swapping, Action.HIGH_JUMP));
        }
        for (Action action : Action.values()) {
            assertFalse(Rac1ClankCapability.permits(heli(true), new Context(true, true, 2, 4), action));
            assertFalse(Rac1ClankCapability.permits(heli(true), new Context(false, true, 2, 2), action));
            assertFalse(Rac1ClankCapability.permits(heli(true), new Context(true, false, 2, 2), action));
        }
        var thruster = new Rac1ClankEquipment(true, false, true, true, Module.THRUSTER);
        assertTrue(Rac1ClankCapability.permits(thruster, new Context(true, true, 2, 3), Action.HIGH_JUMP));
        assertFalse(Rac1ClankCapability.permits(thruster, new Context(true, true, 2, 3), Action.GLIDE));
    }
    @Test void durableScopeRoundTripAndMalformedSaveFailClosed() throws Exception {
        var equipment = new Rac1ClankEquipment(true, true, true, true, Module.THRUSTER);
        assertEquals(equipment, Rac1ClankEquipment.load(equipment.save()));
        for (int i = 0; i < 7; i++) {
            byte[] bad = equipment.save();
            bad[i] = (byte) 255;
            assertThrows(IOException.class, () -> Rac1ClankEquipment.load(bad));
        }
        assertThrows(IOException.class, () -> Rac1ClankEquipment.load(new byte[8]));
    }
    @Test void heldPolicyRejectsForgedEquipmentActionsReplayAndOldLifecycleEpochs() {
        var session = new Rac1ClankSession(UUID.randomUUID(), 10, heli(true));
        for (var kind : Rac1ClankSession.IntentKind.values())
            assertFalse(session.intent(new Rac1ClankSession.Intent(10, kind.ordinal(), kind, 2, true)));
        assertEquals(3, session.lastSerial());
        assertFalse(session.intent(new Rac1ClankSession.Intent(10, 1,
                Rac1ClankSession.IntentKind.GLIDE, 2, true)));
        assertEquals(3, session.lastSerial());
        session.invalidate();
        assertFalse(session.intent(new Rac1ClankSession.Intent(10, 99,
                Rac1ClankSession.IntentKind.GLIDE, 2, true)));
        assertEquals(-1, session.lastSerial());
        assertEquals(Action.IDLE, session.snapshot(100).action());
        assertEquals(heli(true), session.snapshot(100).equipment()); // not a clone/death grant rule
    }
    @Test void boundedSemanticWireRoundTripAndMalformedEnumsTimesFlags() throws Exception {
        var session = new Rac1ClankSession(UUID.randomUUID(), 5, heli(true));
        var s = session.snapshot(100);
        byte[] bytes = Rac1ClankWire.encode(s);
        assertEquals(68, bytes.length);
        assertEquals(s, Rac1ClankWire.snapshot(bytes));
        for (int offset : new int[] {16, 24, 32, 40, 44, 45, 46, 47, 48, 56, 64}) {
            var bad = bytes.clone(); bad[offset] = (byte) 255;
            assertThrows(IOException.class, () -> Rac1ClankWire.snapshot(bad), "offset " + offset);
        }
        var intent = new Rac1ClankSession.Intent(5, 1, Rac1ClankSession.IntentKind.GLIDE, 2, true);
        assertEquals(intent, Rac1ClankWire.intent(Rac1ClankWire.encode(intent)));
        assertThrows(IOException.class, () -> Rac1ClankWire.intent(new byte[20]));
    }
    @Test void exactProfilesAndStrictReleaseBothHeldViews() {
        assertEquals(0x3ca3d70b, Float.floatToRawIntBits(Profile.ORIGINAL_50.dt()));
        assertEquals(3, Rac1ClankClock.SOURCE_SUBSTEPS_PER_MC_TICK);
        assertEquals(28, Profile.ORIGINAL_50.heliLiftStart());
        assertEquals(58, Profile.ORIGINAL_50.heliLiftEnd());
        for (Profile p : Profile.values()) {
            assertFalse(Rac1ClankClock.glideReleased(p, p.ticks(30), false, false));
            assertTrue(Rac1ClankClock.glideReleased(p, p.ticks(30) + 1, false, false));
            assertFalse(Rac1ClankClock.glideReleased(p, p.ticks(30) + 1, true, false));
            assertFalse(Rac1ClankClock.glideReleased(p, p.ticks(30) + 1, false, true));
            assertEquals(.5, Rac1ClankClock.sourceDisplacementToMcDelta(10.0 / p.hz(), p), 1e-12);
        }
    }
    @Test void stagedCurveWindowSentinelDurationAndNoBudgetReset() {
        assertEquals(99, Rac1ClankLiftCurve.THRUSTER.get(4).duration60());
        assertEquals(-999999, Rac1ClankLiftCurve.THRUSTER.get(3).acceleration());
        var sentinel = Rac1ClankLiftCurve.THRUSTER.get(3);
        assertEquals(32, Rac1ClankLiftCurve.nextAcceleration(32, sentinel, true));
        assertEquals(29, Rac1ClankLiftCurve.nextAcceleration(32, sentinel, false));
        for (Profile p : Profile.values()) {
            var curve = new Rac1ClankLiftCurve(p, true);
            for (int time = 0; time < 100; time++) {
                double increment = curve.step(time);
                boolean active = time >= p.thrusterLiftStart() && time < p.thrusterLiftEnd();
                if (!active) assertEquals(0, increment);
                if (time == p.thrusterLiftStart()) assertEquals(151 * p.dtSquared(), increment);
            }
            assertTrue(curve.row() < 3); // state window stops lift before sentinel/later rows
            assertThrows(IllegalArgumentException.class, () -> curve.step(0));
        }
        var heli = new Rac1ClankLiftCurve(Profile.ORIGINAL_60, false);
        for (int time = 0; time < 100; time++)
            assertEquals(time >= 35 && time < 71 ? 48 * Profile.ORIGINAL_60.dtSquared() : 0,
                    heli.step(time));
    }
    @Test void componentClockKeepsAuthoredFramesDistinctAndCrossesVariableRateKeys() {
        var timing = new Rac1SourceTiming(0, new int[] {160, 192},
                new int[] {Float.floatToIntBits(.5f), Float.floatToIntBits(.25f)},
                new float[] {0, 2f / 60}, new int[] {0, 1, -1});
        var clip = new Rac1Animation("synthetic", new float[] {0, 2f / 60, 6f / 60}, true,
                new float[] {0,0,0,1, 0,0,0,1, 0,0,0,1}, new int[0], new int[0], new float[0],
                new int[0], new int[0], new float[0], new boolean[0], timing);
        var clock = new Rac1ClankComponentClock(clip);
        assertEquals(10, clock.current().authoredFrame());
        assertEquals(11, clock.step(1).authoredFrame());
        assertTrue(clock.step(1).crossedKey());
        assertEquals(3f / 60, clock.step(1).convertedSeconds(), 1e-7);
        clock.step(1); clock.step(1);
        assertTrue(clock.step(1).wrapped());
        assertEquals(1, Rac1ClankComponentClock.blendWeight(10, 10));
        assertEquals(.5, Rac1ClankComponentClock.blendWeight(5, 10));
    }
    @Test void nonloopClockHoldsItsLastKeyWithoutReadingZeroTerminalRate() {
        var clip = new Rac1Animation("synthetic", new float[] {0, 1f / 60}, false,
                new float[] {0,0,0,1, 0,0,0,1}, new int[0], new int[0], new float[0],
                new int[0], new int[0], new float[0], new boolean[0],
                new Rac1SourceTiming(0, new int[] {0, 16},
                        new int[] {Float.floatToIntBits(1), 0}, new float[] {0, 1f / 60},
                        new int[] {0, 1}));
        var clock = new Rac1ClankComponentClock(clip);
        assertTrue(clock.step(1).crossedKey());
        assertFalse(clock.step(1).crossedKey());
        assertEquals(1f / 60, clock.current().convertedSeconds());
    }
}
