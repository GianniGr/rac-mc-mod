package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltRestartAssertionTest {
    private static State saved() {
        return Rac1BoltMotion.launch(new V(1000000, 120, 1000000), 0, 3, 3, 6, 36, 12, 0, 1);
    }
    @Test void splitMovementRoundingDoesNotFalselyFailContinuation() {
        State expected = saved();
        State actual = Rac1BoltMotion.relocated(expected, expected.position().add(new V(0, 1.42e-14, 0)));
        assertTrue(Rac1BoltRestartGameTests.sameContinuation(expected, actual));
    }
    @Test void materialPositionChangeStillFailsContinuation() {
        State expected = saved();
        State actual = Rac1BoltMotion.relocated(expected, expected.position().add(new V(.0001, 0, 0)));
        assertFalse(Rac1BoltRestartGameTests.sameContinuation(expected, actual));
    }
    @Test void velocityPhaseAndCountersAreStillExactNotHiddenByPositionTolerance() {
        State expected = saved();
        for (State actual : new State[]{
                new State(expected.position(), V.ZERO, expected.phase(), expected.startY(), expected.rest(), expected.up(),
                        expected.total(), expected.remaining(), expected.settleFrames(), expected.idleFrames(),
                        expected.spin(), expected.secondary(), expected.direction(), expected.flySpeed(), expected.target()),
                new State(expected.position(), expected.velocity(), Phase.IDLE, expected.startY(), expected.rest(), expected.up(),
                        expected.total(), expected.remaining(), expected.settleFrames(), expected.idleFrames(),
                        expected.spin(), expected.secondary(), expected.direction(), expected.flySpeed(), expected.target()),
                new State(expected.position(), expected.velocity(), expected.phase(), expected.startY(), expected.rest(), expected.up(),
                        expected.total(), expected.remaining() + 1, expected.settleFrames(), expected.idleFrames(),
                        expected.spin(), expected.secondary(), expected.direction(), expected.flySpeed(), expected.target())}) {
            assertFalse(Rac1BoltRestartGameTests.sameContinuation(expected, actual));
        }
    }
}
