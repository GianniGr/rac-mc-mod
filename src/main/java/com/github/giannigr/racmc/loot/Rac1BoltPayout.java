package com.github.giannigr.racmc.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Original retail ordinary/fresh-instance rules, measured by R05 (both supported revisions).
 * Mission, revisit, challenge and low-pool approximation are deliberately not simulated. */
public final class Rac1BoltPayout {
    private Rac1BoltPayout() {}

    // {authored amount, number of measured placed instances}. Seven sampled original levels.
    private static final int[][] WOOD = {
        {10,73},{15,331},{31,16},{20,117},{9,49},{30,62},{29,83},{44,63}
    };
    private static final int[][] EXPLOSIVE = {
        {31,1},{30,42},{5,1},{40,17},{18,10},{39,2},{58,10},{89,5}
    };
    public static int selectBase(int classNumber, RandomGenerator random) {
        int[][] profiles = switch (classNumber) {
            case 500 -> WOOD;
            case 505 -> EXPLOSIVE;
            default -> new int[0][];
        };
        int count = 0;
        for (int[] profile : profiles) count += profile[1];
        if (count == 0) return 0;
        int choice = random.nextInt(count);
        for (int[] profile : profiles) {
            choice -= profile[1];
            if (choice < 0) return profile[0];
        }
        throw new AssertionError();
    }

    public static int roll(int base, RandomGenerator random) {
        if (base <= 0) return 0;
        int spread = base >= 8 ? base / 4 : base >= 2 ? 1 : 0;
        return random.nextInt(base - spread, base + spread + 1);
    }

    /** Not greedy: retail thresholds intentionally leave several ones. */
    public static List<Integer> split(int total) {
        if (total < 0) throw new IllegalArgumentException("Negative payout");
        int fifties = 0, twenties = 0, fives = 0;
        while (total >= 300) { fifties++; total -= 50; }
        while (total >= 200) { twenties++; total -= 20; }
        while (total >= 30) { twenties++; fives++; total -= 25; }
        while (total >= 7) { fives++; total -= 5; }
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < fifties; i++) values.add(50);
        for (int i = 0; i < twenties; i++) values.add(20);
        for (int i = 0; i < fives; i++) values.add(5);
        for (int i = 0; i < total; i++) values.add(1);
        return List.copyOf(values);
    }
}
