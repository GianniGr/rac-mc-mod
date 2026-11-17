package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.github.giannigr.racmc.actions.WrenchActionPresentation.Phase;
import com.github.giannigr.racmc.actions.WrenchActionState.Eligibility;
import com.github.giannigr.racmc.actions.WrenchActionState.Intent;
import com.github.giannigr.racmc.actions.WrenchActionState.ClipFrame;

class WrenchActionStateTest {
    private static final ActionVector HAND = new ActionVector(0, 1, 0);
    private static final ActionVector AIM = new ActionVector(1, 0, 0);
    private static final Eligibility THROW = new Eligibility(true, true, true, true, 0, 0,
            false, true);
    private static final Eligibility SLAM = new Eligibility(true, true, true, false, 4, 1,
            true, true);
    private static final WrenchActionState.Surface CLEAR = (from, to) -> null;

    @Test void throwIsAcceptedOnceAndUsesSeparateAssets() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, Intent.THROW, 0, THROW, HAND, AIM));
        assertFalse(state.start(2, Intent.THROW, 10, THROW, HAND, AIM));
        var view = state.presentation();
        assertEquals(26, view.bodySequence());
        assertEquals(1, view.wrenchSequence());
        assertTrue(view.heldVisible());
        assertEquals(Phase.THROW_WINDUP, view.phase());
    }

    @Test void releaseRequiresImportedEventNotGuessedElapsedTicks() {
        var state = throwing();
        state.tick(40, true, false, HAND, new ClipFrame(2, false, false, false), CLEAR);
        assertEquals(Phase.THROW_WINDUP, state.presentation().phase());
        state.tick(41, true, false, HAND, new ClipFrame(2.1, true, false, false), CLEAR);
        assertEquals(Phase.DETACHED_OUT, state.presentation().phase());
        assertEquals(6, state.presentation().wrenchSequence());
        assertFalse(state.presentation().heldVisible());
    }

    @Test void duplicateTickCannotAdvanceOrReleaseTwice() {
        var state = throwing();
        state.tick(1, true, false, HAND, new ClipFrame(0.1, true, false, false), CLEAR);
        var view = state.presentation();
        state.tick(1, true, false, HAND, new ClipFrame(9, true, false, false), CLEAR);
        assertEquals(view, state.presentation());
        assertEquals(view, state.presentation()); // repeated render read
    }

    @Test void bodyAndChildKeepSeparateSampleTimesAndBlendWeightsFromOneAcceptedAction() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, Intent.SLAM, 0, SLAM, HAND, AIM));
        state.tick(1, true, false, HAND,
                new ClipFrame(0.12, 0.09, 0.6, 0.4, false, false, false), CLEAR);
        assertEquals(0.12, state.presentation().clipSeconds());
        assertEquals(0.09, state.presentation().wrenchClipSeconds());
        assertEquals(0.6, state.presentation().bodyBlendWeight());
        assertEquals(0.4, state.presentation().wrenchBlendWeight());
    }

    @Test void idealizedFlightMatchesResearchArithmeticAndCatchesMovingHand() {
        var state = throwing();
        double max = 0;
        int catchTick = -1;
        ActionVector hand = HAND;
        for (int tick = 1; tick <= 45; tick++) {
            if (tick == 14) hand = new ActionVector(-1, 1.5, 0);
            state.tick(tick, true, false, hand,
                    new ClipFrame(tick / 20.0, tick == 1, false, false), CLEAR);
            max = Math.max(max, state.presentation().detachedPosition().x());
            if (state.presentation().phase() == Phase.CATCH) {
                catchTick = tick;
                break;
            }
        }
        assertEquals(7.9796296, max, 0.03);
        assertTrue(catchTick >= 18 && catchTick <= 24, "moving-hand catch, not launch-origin catch");
        assertEquals(hand, state.presentation().detachedPosition());
        assertTrue(state.presentation().heldVisible());
        assertEquals(1, state.presentation().wrenchSequence());
        assertFalse(state.claimTarget(UUID.randomUUID()), "throw has no damage capability");
    }

    @Test void wallStartsReturnWithoutRicochetOrDamage() {
        var state = throwing();
        state.tick(1, true, false, HAND, new ClipFrame(0.1, true, false, false),
                (from, to) -> to.x() >= 0.3 ? new ActionVector(0.3, 1, 0) : null);
        assertTrue(state.presentation().phase() == Phase.DETACHED_BACK
                || state.presentation().phase() == Phase.CATCH);
        assertFalse(state.claimTarget(UUID.randomUUID()));
    }

    @Test void lowFlightApproachesOriginalFloorClearanceEvenBelowWorldZero() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, Intent.THROW, 0, THROW,
                new ActionVector(0, -62.9, 0), AIM));
        state.tick(1, true, false, new ActionVector(0, -62.9, 0),
                new ClipFrame(0.1, true, false, false), new WrenchActionState.Surface() {
                    public ActionVector firstContact(ActionVector from, ActionVector to) { return null; }
                    public double groundHeight(ActionVector position) { return -63; }
                });
        assertTrue(state.presentation().detachedPosition().y() > -62.9);
        assertTrue(state.presentation().detachedPosition().y() <= -62.45);
    }

    @Test void detachedRotationIsCapturedAtReleaseNotChangedByMovingOwner() {
        var state = throwing();
        var q = new ActionRotation(0, 1, 0, 0);
        state.tick(1, true, false, HAND, q, new ClipFrame(0.1, true, false, false), CLEAR);
        state.tick(2, true, false, HAND, ActionRotation.IDENTITY,
                new ClipFrame(0.2, false, false, false), CLEAR);
        assertEquals(q, state.presentation().detachedRotation());
    }

    @Test void cancellationAlwaysRestoresHeldVisibilityAndDoesNotReturnAnItem() {
        var state = throwing();
        state.tick(1, true, false, HAND, new ClipFrame(0.1, true, false, false), CLEAR);
        state.tick(2, false, false, HAND, new ClipFrame(0.2, false, false, false), CLEAR);
        assertEquals(WrenchActionPresentation.idle(), state.presentation());
        assertFalse(state.start(1, Intent.THROW, 20, THROW, HAND, AIM), "replay remains rejected");
    }

    @Test void cooldownSurvivesCancellation() {
        var state = throwing();
        state.cancel();
        assertFalse(state.start(2, Intent.THROW, 9, THROW, HAND, AIM));
        assertFalse(state.start(2, Intent.THROW, 10, THROW, HAND, AIM));
        assertTrue(state.start(3, Intent.THROW, 10, THROW, HAND, AIM));
    }

    @Test void acceptedNormalStartSharesTheTenTickActionGate() {
        var state = new WrenchActionState();
        state.normalAttackAccepted(20);
        assertFalse(state.canStartAt(29));
        assertFalse(state.start(1, Intent.SLAM, 29, SLAM, HAND, AIM));
        assertTrue(state.start(2, Intent.SLAM, 30, SLAM, HAND, AIM));
    }

    @Test void slamUses43And10ThenLandingRecoveryNoGroundBlast() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, Intent.SLAM, 0, SLAM, HAND, AIM));
        assertEquals(43, state.presentation().bodySequence());
        assertEquals(10, state.presentation().wrenchSequence());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        state.tick(1, true, false, HAND, new ClipFrame(0.4, false, true, false), CLEAR);
        assertTrue(state.claimTarget(a));
        assertFalse(state.claimTarget(a), "sweep + tip + body do not stack");
        assertTrue(state.claimTarget(b), "not a single-target cap");
        state.tick(2, true, true, HAND, new ClipFrame(0.5, false, true, false), CLEAR);
        assertEquals(Phase.RECOVERY, state.presentation().phase());
        assertFalse(state.claimTarget(UUID.randomUUID()), "landing is not a radial damage event");
        state.tick(3, true, true, HAND, new ClipFrame(0.8, false, false, true), CLEAR);
        assertEquals(Phase.IDLE, state.presentation().phase());
    }

    @Test void validatesObservedModeItemCrouchAirAgeClearanceAndSampler() {
        Eligibility[] invalid = {
            new Eligibility(false, true, true, false, 4, 1, true, true),
            new Eligibility(true, false, true, false, 4, 1, true, true),
            new Eligibility(true, true, false, false, 4, 1, true, true),
            new Eligibility(true, true, true, true, 4, 1, true, true),
            new Eligibility(true, true, true, false, 3, 1, true, true),
            new Eligibility(true, true, true, false, 4, 0.56, true, true),
            new Eligibility(true, true, true, false, 4, 1, true, false)
        };
        for (Eligibility e : invalid) {
            assertFalse(new WrenchActionState().start(1, Intent.SLAM, 0, e, HAND, AIM), e.toString());
        }
        assertFalse(new WrenchActionState().start(-1, Intent.SLAM, 0, SLAM, HAND, AIM));
        assertFalse(new WrenchActionState().start(1, Intent.THROW, 0, SLAM, HAND, AIM));
        assertFalse(new WrenchActionState().start(1, Intent.THROW, 0, THROW, HAND, ActionVector.ZERO));
    }

    @Test void timeoutCannotLeaveWrenchHiddenForever() {
        var state = throwing();
        state.tick(1, true, false, HAND, new ClipFrame(0.1, true, false, false), CLEAR);
        state.tick(101, true, false, HAND, new ClipFrame(9, false, false, false), CLEAR);
        assertEquals(WrenchActionPresentation.idle(), state.presentation());
    }

    @Test void rejectsInvalidMathInputs() {
        assertThrows(IllegalArgumentException.class, () -> new ActionVector(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClipFrame(-1, false, false, false));
    }

    private static WrenchActionState throwing() {
        var state = new WrenchActionState();
        assertTrue(state.start(1, Intent.THROW, 0, THROW, HAND, AIM));
        return state;
    }
}
