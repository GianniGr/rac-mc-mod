package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.loot.Rac1BoltMotion.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltGrabberTest {
    private static final Collision AIR = (p, v, r) -> new Movement(p.add(v), List.of());
    private static final UUID FIRST = UUID.fromString("7fffffff-0000-0000-0000-000000000000");
    private static final UUID SECOND = UUID.fromString("80000000-0000-0000-0000-000000000000");

    private static Hero hero(UUID id, double x, double y, double z, boolean equipped) {
        V feet = new V(x, y, z);
        return new Hero(id, feet, feet.add(V.UP), equipped);
    }

    private static Step step(State s, int value, Collision collision, Hero... heroes) {
        return Rac1BoltMotion.stepCandidates(s, value, collision, () -> List.of(heroes), () -> .5);
    }

    @Test void eachDenominationUsesStrictHorizontalAndVerticalCylinder() {
        State idle = Rac1BoltMotion.legacy(V.ZERO);
        for (int value : new int[]{1, 5, 20, 50}) {
            for (boolean equipped : new boolean[]{false, true}) {
                double planar = equipped ? 12 : 3;
                double vertical = equipped ? 4.5 : 1.75;
                assertNull(step(idle, value, AIR, hero(FIRST, planar, 0, 0, equipped)).state().target());
                assertNull(step(idle, value, AIR, hero(FIRST, 0, -vertical, 0, equipped)).state().target());
                assertNull(step(idle, value, AIR, hero(FIRST, 0, Math.nextUp(vertical), 0, equipped)).state().target());
                var inside = step(idle, value, AIR,
                        hero(FIRST, Math.nextDown(planar), Math.nextDown(vertical), 0, equipped));
                assertEquals(FIRST, inside.state().target());
                assertFalse(inside.collect());
                assertEquals(idle.position(), inside.state().position());
            }
        }
    }

    @Test void mixedRangesPreferNearestAdmissibleThenCanonicalUuidRegardlessOfOrder() {
        State idle = Rac1BoltMotion.legacy(V.ZERO);
        Hero plainOutside = hero(FIRST, 4, 0, 0, false);
        Hero boosted = hero(SECOND, 8, 0, 0, true);
        Hero plainInside = hero(FIRST, 2, 0, 0, false);
        assertEquals(SECOND, step(idle, 1, AIR, plainOutside, boosted).state().target());
        assertEquals(FIRST, step(idle, 1, AIR, boosted, plainInside).state().target());
        Hero tied = hero(SECOND, -2, 0, 0, true);
        assertEquals(FIRST, step(idle, 1, AIR, tied, plainInside).state().target());
        assertEquals(FIRST, step(idle, 1, AIR, plainInside, tied).state().target());
    }

    @Test void fallingRanksAtTheSingleResolvedContactAndUsesItsReflectedTrajectory() {
        State falling = Rac1BoltMotion.launch(V.ZERO, 0, 3, 3, 6, 36, 12, 0, 1);
        // Nearer plain player started inside 3 but ends outside after contact correction.
        // Grabber started outside 12 and ends inside after the same correction.
        Hero plain = hero(FIRST, -2.45, .5, 0, false);
        Hero boosted = hero(SECOND, 12.55, .5, 0, true);
        V normal = new V(-1, 0, 0);
        V corrected = new V(.6, .6, 0);
        AtomicInteger moves = new AtomicInteger(), draws = new AtomicInteger();
        Collision wall = (p, v, r) -> {
            moves.incrementAndGet();
            return new Movement(corrected, List.of(new Contact(corrected, normal, normal)));
        };
        var admitted = Rac1BoltMotion.stepCandidates(falling, 20, wall, () -> List.of(plain, boosted), () -> {
            draws.incrementAndGet();
            return .5;
        });
        assertEquals(1, moves.get());
        assertEquals(1, draws.get());
        assertEquals(SECOND, admitted.state().target());
        assertEquals(corrected, admitted.state().position());
        assertTrue(admitted.state().velocity().x() < 0);
        // An ordinary in-range candidate takes exactly the same flight path, including its first hop.
        var baseline = step(falling, 20, wall, hero(SECOND, 1, .5, 0, false));
        assertEquals(baseline, admitted);
        assertFalse(admitted.collect());
    }

    @Test void airAndSettlingIgnoreProximityButLowFallRemainsIndependent() {
        State falling = Rac1BoltMotion.launch(V.ZERO, 0, 3, 3, 6, 3, 12, 0, 1);
        var boosted = hero(FIRST, 8, 0, 0, true);
        assertEquals(Phase.FALLING, step(falling, 1, AIR, boosted).state().phase());
        Collision ground = (p, v, r) -> new Movement(p, List.of(new Contact(V.ZERO, V.UP, V.UP)));
        State settling = step(falling, 1, ground).state();
        for (int i = 0; i < 3; i++) {
            assertEquals(Phase.SETTLING, settling.phase());
            settling = step(settling, 1, AIR, boosted).state();
            assertNull(settling.target());
        }
        assertEquals(Phase.IDLE, settling.phase());
        assertEquals(FIRST, step(settling, 1, AIR, boosted).state().target());
        State below = Rac1BoltMotion.relocated(falling, new V(0, -3, 0));
        Hero nearButBelow = hero(FIRST, 0, -2, 0, true);
        Hero farButHigh = hero(SECOND, 24, 0, 0, false);
        var bypass = Rac1BoltMotion.stepCandidates(below, 1, AIR, () -> List.of(nearButBelow, farButHigh),
                () -> { throw new AssertionError("Low fall must preserve velocity"); });
        assertEquals(SECOND, bypass.state().target());
        assertFalse(bypass.collect());
        assertNull(step(below, 1, AIR, nearButBelow).state().target());
        assertEquals(FIRST, step(below, 1, AIR, farButHigh,
                hero(FIRST, 4, 0, 0, false)).state().target());
        var edge = hero(FIRST, 0, -1, 0, true);
        Collision exactlyTwoBelowPlayer = (p, v, r) -> new Movement(new V(0, -3, 0), List.of());
        assertNull(step(below, 1, exactlyTwoBelowPlayer, edge).state().target());
        Collision exactlyTwoBelowLaunch = (p, v, r) -> new Movement(new V(0, -1.5, 0), List.of());
        assertNull(step(below, 1, exactlyTwoBelowLaunch, farButHigh).state().target());
    }

    @Test void unequipChangesOnlyNewAdmissionsAndCannotStealOrdinaryOrWrenchLocks() {
        var idle = Rac1BoltMotion.legacy(V.ZERO);
        var worn = hero(FIRST, 8, 0, 0, true);
        var removedAndMoved = hero(FIRST, -8, 0, 0, false);
        var rival = hero(SECOND, .1, 0, 0, true);
        assertNull(step(idle, 1, AIR, removedAndMoved).state().target());
        var ordinary = step(idle, 1, AIR, worn).state();
        assertSame(ordinary, Rac1BoltMotion.beginRemoteAttraction(ordinary, SECOND, () -> .5));
        var wrench = Rac1BoltMotion.beginRemoteAttraction(idle, FIRST, () -> .5);
        for (State lock : List.of(ordinary, wrench)) {
            var flight = step(lock, 1, AIR, rival, removedAndMoved);
            assertEquals(FIRST, flight.state().target());
            assertTrue(flight.state().position().x() < 0);
            assertFalse(flight.collect());
            var lost = step(flight.state(), 1, AIR, rival);
            assertEquals(Phase.IDLE, lost.state().phase());
            assertNull(lost.state().target());
            assertFalse(lost.collect());
            assertNull(step(lost.state(), 1, AIR, removedAndMoved).state().target());
            assertEquals(SECOND, step(lost.state(), 1, AIR, rival).state().target());
        }
    }
}
