package com.github.giannigr.racmc.loot;

import java.util.*;
import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltMotionTest {
    private static final Collision AIR = (p, v, r) -> new Movement(p.add(v), List.of());
    private static State east() { return Rac1BoltMotion.launch(V.ZERO, 0, 3, 3, 6, 36, 12, 0, 1); }
    private static Step frame(State s, int value, Collision collision, Hero hero) {
        return Rac1BoltMotion.step(s, value, collision, hero, () -> .5);
    }
    private static void vector(V expected, V actual) {
        assertEquals(expected.x(), actual.x(), 1e-9);
        assertEquals(expected.y(), actual.y(), 1e-9);
        assertEquals(expected.z(), actual.z(), 1e-9);
    }
    @Test void independentHorizontalDrawsAndZeroNormalization() {
        State diagonal = Rac1BoltMotion.launch(V.ZERO, Math.PI / 4, 3, 1, 3.7, 36, 12, 0, 1);
        vector(new V(.4743416490252569, .5, -.15811388300841897), diagonal.position());
        vector(new V(.10606601717798213, .185, -.035355339059327376), diagonal.velocity().mul(3));
        var zero = Rac1BoltMotion.launch(V.ZERO, 1, 0, 0, 4, 36, 12, 0, 1);
        vector(new V(0, .5, 0), zero.position());
        vector(new V(0, .2, 0), zero.velocity().mul(3));
    }
    @Test void retainedR05GoldensAt60HzAndGrouped20TPS() {
        int[] frames = {1, 3, 6, 15, 30, 33, 60};
        double[] ys = {.597, .782, 1.037, 1.640, 2.105, 2.117, 1.010};
        State state = east();
        int golden = 0;
        for (int n = 1; n <= 60; n++) {
            state = frame(state, 1, AIR, null).state();
            if (n == frames[golden]) {
                vector(new V(.5 + n * .05, ys[golden], 0), state.position());
                assertEquals(.1 - n * .003, state.velocity().y(), 1e-12);
                if (golden < frames.length - 1) golden++;
            }
        }
        State grouped = east();
        double y = .5, tickVelocity = .3;
        for (int tick = 1; tick <= 20; tick++) {
            for (int sub = 0; sub < 3; sub++) grouped = frame(grouped, 1, AIR, null).state();
            y += tickVelocity - (2.0 / 3) * .027;
            tickVelocity -= .027;
            assertEquals(y, grouped.position().y(), 1e-12);
            assertEquals(tickVelocity, grouped.velocity().y() * 3, 1e-12);
        }
    }
    @Test void fixedSeedDoesNotProduceAllOverlappingPickupsAndKeepsRingRadius() {
        var random = new Random(261116);
        Set<V> positions = new HashSet<>(), velocities = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            State s = Rac1BoltMotion.launch(new V(4.5, 10, 7.5), random);
            positions.add(s.position()); velocities.add(s.velocity());
            V d = s.position().sub(new V(4.5, 10, 7.5));
            assertEquals(.5, Math.hypot(d.x(), d.z()), 1e-12);
            assertEquals(.5, d.y(), 1e-12);
            assertTrue(Math.hypot(s.velocity().x(), s.velocity().z()) * 60 <= 3);
            assertTrue(s.velocity().y() * 60 >= 3.7 && s.velocity().y() * 60 <= 6);
        }
        assertEquals(100, positions.size()); assertEquals(100, velocities.size());
    }
    @Test void allDenominationsShareAirborneTranslationButNotRadius() {
        for (int value : new int[]{1, 5, 20, 50}) {
            State state = east();
            for (int i = 0; i < 60; i++) state = frame(state, value, AIR, null).state();
            vector(new V(3.5, 1.010, 0), state.position());
        }
        assertEquals(.15, Rac1BoltMotion.radius(1));
        assertEquals(.23, Rac1BoltMotion.radius(5));
        assertEquals(.23, Rac1BoltMotion.radius(20));
        assertEquals(.25, Rac1BoltMotion.radius(50));
    }
    @Test void syntheticPlaneMatchesR05FirstContactAndEntersSettle() {
        Collision plane = (p, v, r) -> {
            V next = p.add(v);
            return next.y() > r ? new Movement(next, List.of()) : new Movement(next,
                    List.of(new Contact(new V(next.x(), 0, next.z()), V.UP, V.UP)));
        };
        for (int value : new int[]{1, 5, 20, 50}) {
            State s = east();
            int n = 0;
            while (s.phase() == Phase.FALLING && n < 100) { s = frame(s, value, plane, null).state(); n++; }
            assertEquals(value == 1 ? 70 : 69, n);
            assertEquals(Phase.SETTLING, s.phase());
            assertEquals(36, s.remaining());
        }
    }
    @Test void wallReflectsWithoutDragAndSlopeTestsAreNotReversed() {
        V incoming = new V(2, -3, 1), wall = new V(-1, 0, 0);
        V reflected = Rac1BoltMotion.reflect(incoming, wall);
        vector(new V(-2, -3, 1), reflected);
        assertEquals(incoming.length(), reflected.length(), 1e-12);
        vector(reflected, Rac1BoltMotion.reflect(reflected, wall));
        assertFalse(Rac1BoltMotion.ground(wall, wall));
        for (double angle : new double[]{44, 46}) {
            V normal = new V(Math.sin(Math.toRadians(angle)), Math.cos(Math.toRadians(angle)), 0);
            assertEquals(angle == 44, Rac1BoltMotion.ground(normal, normal));
            assertEquals(angle == 44, Rac1BoltMotion.ground(V.UP, normal));
            assertEquals(angle == 44, Rac1BoltMotion.ground(normal, V.UP));
        }
    }
    @Test void proximityDoesNotCancelAirborneScatterOrSettleButIdleHasNoMandatoryDelay() {
        Hero hero = new Hero(UUID.randomUUID(), new V(.5, 0, 0), new V(.5, 1, 0));
        State s = frame(east(), 1, AIR, hero).state();
        assertEquals(Phase.FALLING, s.phase());
        Collision floor = (p, v, r) -> new Movement(p, List.of(new Contact(V.ZERO, V.UP, V.UP)));
        s = frame(east(), 1, floor, null).state();
        for (int i = 0; i < 36; i++) {
            assertEquals(Phase.SETTLING, s.phase());
            s = frame(s, 1, AIR, hero).state();
        }
        assertEquals(Phase.IDLE, s.phase());
        assertEquals(12, s.remaining());
        s = frame(s, 1, AIR, hero).state();
        assertEquals(Phase.ATTRACTING, s.phase());
        assertEquals(hero.id(), s.target());
        assertEquals(12, s.remaining()); // idle hop is not a lockout
        assertEquals(Phase.ATTRACTING, frame(east(), 1, floor, hero).state().phase());
        assertEquals(.225, Rac1BoltMotion.hop(36, 18, .025), 1e-12);
        assertEquals(.0375, Rac1BoltMotion.hop(12, 6, .0125), 1e-12);
    }
    @Test void independentRestAnchorsAndNoCurrencyDecisionOnInterpolation() {
        State a = Rac1BoltMotion.legacy(new V(1, 2, 3));
        State b = Rac1BoltMotion.legacy(new V(7, 8, 9));
        State updated = frame(a, 1, AIR, null).state();
        vector(new V(7, 8, 9), b.rest());
        assertNotEquals(updated.rest(), b.rest());
        V current = V.ZERO, target = new V(3, 6, 9);
        for (int remaining = 3; remaining > 0; remaining--) {
            current = Rac1BoltMotion.interpolate(current, target, remaining);
            vector(target.mul((4.0 - remaining) / 3), current);
        }
        vector(target, current);
    }
    @Test void attractingRetainsLaunchVelocityAndDisconnectRecoversEligibility() {
        Hero hero = new Hero(UUID.randomUUID(), V.ZERO, new V(1, 1, 0));
        Collision wall = (p, v, r) -> new Movement(p.add(v),
                List.of(new Contact(V.ZERO, new V(-1, 0, 0), new V(-1, 0, 0))));
        State s = frame(east(), 1, wall, hero).state();
        assertEquals(Phase.ATTRACTING, s.phase());
        assertTrue(s.velocity().length() > 0);
        State next = frame(s, 1, AIR, hero).state();
        assertTrue(next.velocity().length() < s.velocity().length());
        State disconnected = frame(next, 1, AIR, null).state();
        assertEquals(Phase.IDLE, disconnected.phase());
        assertNull(disconnected.target());
    }
    @Test void remoteStartAcceptsFallingSettlingAndIdleWithoutResettingExistingCollector() {
        UUID owner = UUID.randomUUID(), rival = UUID.randomUUID();
        Collision floor = (p, v, r) -> new Movement(p, List.of(new Contact(V.ZERO, V.UP, V.UP)));
        State falling = east();
        State settling = frame(falling, 1, floor, null).state();
        assertEquals(Phase.SETTLING, settling.phase());
        State idle = Rac1BoltMotion.legacy(V.ZERO);
        for (var before : List.of(falling, settling, idle)) {
            var started = Rac1BoltMotion.beginRemoteAttraction(before, owner, () -> .5);
            assertEquals(Phase.ATTRACTING, started.phase());
            assertEquals(owner, started.target());
            assertEquals(before.position(), started.position());
            assertEquals(0, started.flySpeed());
            vector(before == idle ? V.UP.mul(5.0 / 60) : before.velocity(), started.velocity());
            assertSame(started, Rac1BoltMotion.beginRemoteAttraction(started, rival,
                    () -> { throw new AssertionError("Existing attraction must not restart"); }));
        }
    }

    @Test void remoteFlightUsesMovingBodyAndDoesNotSwitchOnTargetLoss() {
        UUID owner = UUID.randomUUID();
        State started = Rac1BoltMotion.beginRemoteAttraction(Rac1BoltMotion.legacy(V.ZERO), owner, () -> 0);
        Hero first = new Hero(owner, V.ZERO, new V(4, 1, 0));
        Step beforeMove = frame(started, 20, AIR, first);
        assertFalse(beforeMove.collect());
        assertTrue(beforeMove.state().position().x() > 0);
        Hero moved = new Hero(owner, V.ZERO, new V(-4, 1, 0));
        Step afterMove = frame(beforeMove.state(), 20, AIR, moved);
        assertTrue(afterMove.state().position().x() < beforeMove.state().position().x());
        Step lost = frame(afterMove.state(), 20, AIR, new Hero(UUID.randomUUID(), V.ZERO, V.ZERO));
        assertFalse(lost.collect());
        assertEquals(Phase.IDLE, lost.state().phase());
        assertNull(lost.state().target());
        assertFalse(frame(afterMove.state(), 20, AIR, null).collect());
    }
    @Test void fallingBelowBothLaunchAndHeroCanAttractWithoutContact() {
        State s = Rac1BoltMotion.relocated(east(), new V(.5, -2, 0));
        Hero hero = new Hero(UUID.randomUUID(), new V(10, 1, 0), new V(10, 2, 0));
        assertEquals(Phase.ATTRACTING, frame(s, 1, AIR, hero).state().phase());
        assertEquals(Phase.FALLING, frame(s, 1, AIR, null).state().phase());
    }
}
