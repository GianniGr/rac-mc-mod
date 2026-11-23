package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.giannigr.racmc.actions.WrenchActionPresentation.Phase.*;

import com.github.giannigr.racmc.actions.WrenchActionState.AudioEdge;
import com.github.giannigr.racmc.actions.WrenchActionState.AudioKind;
import com.github.giannigr.racmc.actions.WrenchActionState.ClipFrame;
import com.github.giannigr.racmc.actions.WrenchActionState.Eligibility;
import com.github.giannigr.racmc.actions.WrenchActionState.Intent;
import com.github.giannigr.racmc.actions.WrenchActionState.SourceFrame;
import com.github.giannigr.racmc.sound.WrenchAudioSignal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WrenchActionAudioTest {
    private static final ActionVector HAND = new ActionVector(0, 1, 0);
    private static final ActionVector HEAD = new ActionVector(0, 0.2, 1);
    private static final ActionVector AIM = new ActionVector(1, 0, 0);
    private static final WrenchActionState.Surface CLEAR = (a, b) -> null;
    private static final Eligibility SLAM = new Eligibility(true, true, true, false, 4, 1,
            true, true);
    private static final Eligibility THROW = new Eligibility(true, true, true, true, 0, 0,
            false, true);

    @Test void meleeRequiresMatchingAdmittedTargetAndAcceptedDamageInSameTick() {
        var gate = new WrenchActionAudio.MeleeGate();
        var target = UUID.randomUUID();
        var other = UUID.randomUUID();
        assertFalse(gate.claim(1, target));
        gate.arm(1, target); // start is silent; the successful native damage callback claims it.
        assertFalse(gate.claim(1, other));
        assertFalse(gate.claim(2, target));
        gate.arm(11, target);
        assertTrue(gate.claim(11, target));
        assertFalse(gate.claim(11, target));
        gate.arm(21, target);
        gate.clear();
        assertFalse(gate.claim(21, target));
    }

    @Test void slamIsAnImpactKeyCrossingAtTheSampledHeadNotStartupOrGroundFlag() {
        assertFalse(WrenchOriginalClock.crossesSlamImpact(25, 26.9));
        assertFalse(WrenchOriginalClock.crossesSlamImpact(26.9, 27));
        assertTrue(WrenchOriginalClock.crossesSlamImpact(27, 28));
        assertTrue(WrenchOriginalClock.crossesSlamImpact(26, 28));
        assertFalse(WrenchOriginalClock.crossesSlamImpact(27.1, 28));
        var state = start(Intent.SLAM, SLAM);
        var prep = frame(false, true, false, false);
        var landed = frame(false, true, true, false);
        var impact = frame(false, true, true, true);
        assertNull(state.tickSources(1, true, List.of(prep, prep, landed), CLEAR));
        assertEquals(new AudioEdge(AudioKind.SLAM_IMPACT, HEAD),
                state.tickSources(2, true, List.of(landed, impact, landed), CLEAR));
        assertNull(state.tickSources(2, true, List.of(impact, impact, impact), CLEAR));
        assertNull(state.tickSources(3, true, List.of(impact, impact, impact), CLEAR));
    }

    @Test void throwOnlyReportsTheAcceptedReleaseEvenIfItReturnsAndCatchesInSameBatch() {
        var state = start(Intent.THROW, THROW);
        var windup = frame(false, false, false, false);
        var release = frame(true, false, false, false);
        assertNull(state.tickSources(1, true, List.of(windup, windup, windup), CLEAR));
        assertEquals(new AudioEdge(AudioKind.COMET_RELEASE, HAND),
                state.tickSources(2, true, List.of(release, release, release), (a, b) -> b));
        assertEquals(WrenchActionPresentation.Phase.CATCH, state.presentation().phase());
        assertNull(state.tickSources(2, true, List.of(release, release, release), CLEAR));
        assertNull(state.tickSources(3, true, List.of(release, release, release), CLEAR));
    }

    @Test void rejectedExpiredAndCancelledActionsHaveNoAudio() {
        var release = frame(true, false, false, false);
        var frames = List.of(release, release, release);
        var rejected = new WrenchActionState();
        assertFalse(rejected.start(1, Intent.THROW, 0, SLAM, HAND, AIM));
        assertNull(rejected.tickSources(1, true, frames, CLEAR));
        assertNull(start(Intent.THROW, THROW).tickSources(101, true, frames, CLEAR));
        assertNull(start(Intent.THROW, THROW).tickSources(1, false, frames, CLEAR));
    }

    @Test void bindingUsesHeadForSlamAndOwnerForTheOnlyCometReleaseEdge() {
        assertEquals(WrenchAudioSignal.Kind.SLAM_IMPACT,
                WrenchActionAudio.binding(AudioKind.SLAM_IMPACT, RECOVERY));
        assertEquals(HEAD, WrenchActionAudio.emitter(new AudioEdge(AudioKind.SLAM_IMPACT, HEAD), AIM));
        assertEquals(WrenchAudioSignal.Kind.COMET_RELEASE,
                WrenchActionAudio.binding(AudioKind.COMET_RELEASE, DETACHED_OUT));
        assertEquals(WrenchAudioSignal.Kind.COMET_RELEASE,
                WrenchActionAudio.binding(AudioKind.COMET_RELEASE, DETACHED_BACK),
                "a real release can encounter an early wall in the same batch");
        assertNull(WrenchActionAudio.binding(AudioKind.COMET_RELEASE, CATCH));
        assertNull(WrenchActionAudio.binding(AudioKind.COMET_RELEASE, IDLE));
        assertNull(WrenchActionAudio.binding(AudioKind.COMET_RELEASE, THROW_WINDUP));
        assertEquals(AIM, WrenchActionAudio.emitter(new AudioEdge(AudioKind.COMET_RELEASE, HAND), AIM));
    }

    private static WrenchActionState start(Intent intent, Eligibility eligibility) {
        var state = new WrenchActionState();
        assertTrue(state.start(1, intent, 0, eligibility, HAND, AIM));
        return state;
    }

    private static SourceFrame frame(boolean release, boolean descending, boolean grounded,
            boolean slamImpact) {
        return new SourceFrame(new ClipFrame(.5, .5, 1, 1, release, descending, false, slamImpact),
                HAND, ActionRotation.IDENTITY, grounded, HEAD);
    }
}
