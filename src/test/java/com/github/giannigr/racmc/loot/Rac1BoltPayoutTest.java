package com.github.giannigr.racmc.loot;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1BoltPayoutTest {
    @Test void retailGoldenNonGreedySplits() {
        assertEquals(List.of(5,5,1,1,1,1,1), Rac1BoltPayout.split(15));
        assertEquals(List.of(20,5,1,1,1,1,1,1), Rac1BoltPayout.split(31));
        assertEquals(11, Rac1BoltPayout.split(50).size());
        assertEquals(19, Rac1BoltPayout.split(150).size());
        assertEquals(25, Rac1BoltPayout.split(300).size());
        assertEquals(1, Collections.frequency(Rac1BoltPayout.split(300), 50));
    }
    @Test void allBoundariesPreserveExactValue() {
        for (int n = 0; n <= 2000; n++) {
            var split = Rac1BoltPayout.split(n);
            assertEquals(n, split.stream().mapToInt(Integer::intValue).sum());
            assertTrue(split.stream().allMatch(v -> Set.of(1,5,20,50).contains(v)));
            if (n < 300) assertFalse(split.contains(50));
        }
        assertThrows(IllegalArgumentException.class, () -> Rac1BoltPayout.split(-1));
    }
    @Test void measuredProfilesOnlyAndExcludedCratesZero() {
        Random random = new Random(42);
        Set<Integer> wood = new HashSet<>(), explosive = new HashSet<>();
        for (int i = 0; i < 50000; i++) {
            wood.add(Rac1BoltPayout.selectBase(500, random));
            explosive.add(Rac1BoltPayout.selectBase(505, random));
            assertEquals(0, Rac1BoltPayout.selectBase(501, random));
            assertEquals(0, Rac1BoltPayout.selectBase(511, random));
        }
        assertEquals(Set.of(9,10,15,20,29,30,31,44), wood);
        assertEquals(Set.of(5,18,30,31,39,40,58,89), explosive);
    }
    @Test void originalInclusiveSpreadNoInvented150Cap() {
        Random random = new Random(47);
        Set<Integer> rolls = new HashSet<>();
        for (int i = 0; i < 10000; i++) rolls.add(Rac1BoltPayout.roll(133, random));
        assertEquals(100, Collections.min(rolls));
        assertEquals(166, Collections.max(rolls));
        assertEquals(67, rolls.size());
        assertEquals(0, Rac1BoltPayout.roll(0, random));
        assertEquals(1, Rac1BoltPayout.roll(1, random));
    }
}
