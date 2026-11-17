package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WrenchContactLifetimeTest {
    @Test void finalCatchQuerySurvivesCompletionExactlyOnceWithoutAnIdleDamageFrame() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, WrenchActionState.Intent.THROW, 0,
                new WrenchActionState.Eligibility(true, true, true, true, 0, 0, false, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)));
        boolean caught = false;
        for (int tick = 1; tick < 100; tick++) {
            state.tick(tick, true, false, ActionVector.ZERO,
                    new WrenchActionState.ClipFrame(0.5, true, false, true), (a, b) -> b);
            var steps = state.beginFlightContacts(tick);
            if (state.presentation().phase() == WrenchActionPresentation.Phase.IDLE) {
                assertFalse(steps.isEmpty(), "Completion retains this update's final catch query");
                var catchStep = steps.getLast();
                assertEquals(ActionVector.ZERO, catchStep.to());
                assertEquals(1, catchStep.sequence());
                assertEquals(1, catchStep.blend());
                assertEquals(0, catchStep.toSpin());
                assertTrue(state.beginFlightContacts(tick).isEmpty());
                state.endFlightContacts();
                assertTrue(state.beginFlightContacts(tick + 1).isEmpty());
                caught = true;
                break;
            }
            state.endFlightContacts();
        }
        assertTrue(caught);
    }

    @Test void landingDoesNotDeleteTheOriginalWrenchWindowButAddsNoRadialLandingPulse() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, WrenchActionState.Intent.SLAM, 0,
                new WrenchActionState.Eligibility(true, true, true, false, 4, 1, true, true),
                ActionVector.ZERO, new ActionVector(0, 0, 1)));
        var active = new WrenchActionState.ClipFrame(0.5, false, true, false);
        state.tick(1, true, false, ActionVector.ZERO, active, (a, b) -> null);
        state.tick(2, true, true, ActionVector.ZERO, active, (a, b) -> null);
        assertEquals(WrenchActionPresentation.Phase.RECOVERY, state.presentation().phase());
        assertFalse(state.claimTarget(UUID.randomUUID())); // No blanket ground pulse.
        UUID target = UUID.randomUUID();
        assertTrue(state.claimTarget(target, true)); // Actual source wrench contact through key28.
        assertFalse(state.claimTarget(target, true));
        assertTrue(state.claimBlock(123, true));
        assertFalse(state.claimBlock(123, true));
    }

    @Test void serverFlightSamplesAreConsumedOnceAndExternalCancelCannotLeaveAnIdleHurtAura() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, WrenchActionState.Intent.THROW, 0,
                new WrenchActionState.Eligibility(true, true, true, true, 0, 0, false, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)));
        int identity = state.cometIdentity();
        state.tick(1, true, false, ActionVector.ZERO,
                new WrenchActionState.ClipFrame(0.1, true, false, false), (a, b) -> null);
        assertEquals(3, state.beginFlightContacts(1).size());
        assertTrue(state.beginFlightContacts(1).isEmpty());
        Object lifecycle = new Object();
        assertTrue(state.claimThrownCrate(lifecycle));
        assertFalse(state.claimThrownCrate(lifecycle));
        state.endFlightContacts();
        state.cancel();
        assertTrue(state.beginFlightContacts(2).isEmpty());
        assertFalse(state.claimThrownCrate(new Object()));
        assertTrue(state.start(2, WrenchActionState.Intent.THROW, 10,
                new WrenchActionState.Eligibility(true, true, true, true, 0, 0, false, true),
                ActionVector.ZERO, new ActionVector(1, 0, 0)));
        assertNotEquals(identity, state.cometIdentity());
    }
}
