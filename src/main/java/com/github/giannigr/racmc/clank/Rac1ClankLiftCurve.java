package com.github.giannigr.racmc.clank;

import java.util.List;

/**
 * Independently implemented staged data controller from R13's inspected source facts.
 * Returns source-step displacement increments, not MC velocity/impulses. Gravity/contact
 * resolution are intentionally outside this pure lift primitive; it never moves a player.
 */
public final class Rac1ClankLiftCurve {
    public static final float RETAIN_ACCELERATION = -999999f;
    public record Row(float acceleration, float incrementPerStep, int duration60) {}
    public static final List<Row> THRUSTER = List.of(new Row(151, 0, 5), new Row(0, 0, 27),
            new Row(22, 2, 6), new Row(RETAIN_ACCELERATION, -3, 6), new Row(0, 2, 99));
    public static final List<Row> HELI = List.of(new Row(48, 0, 1000));

    private final Rac1ClankClock.Profile profile;
    private final List<Row> rows;
    private final int start, end;
    private int lastTime = -1, row = -1, inRow;
    private float acceleration;

    public Rac1ClankLiftCurve(Rac1ClankClock.Profile profile, boolean thruster) {
        this.profile = profile;
        rows = thruster ? THRUSTER : HELI;
        start = thruster ? profile.thrusterLiftStart() : profile.heliLiftStart();
        end = thruster ? profile.thrusterLiftEnd() : profile.heliLiftEnd();
    }

    /** Sequential state-time only. A collision cannot rewind or replenish this budget. */
    public double step(int stateTime) {
        if (stateTime != lastTime + 1) throw new IllegalArgumentException("Nonsequential source step");
        lastTime = stateTime;
        if (stateTime < start || stateTime >= end) return 0;
        if (row >= rows.size()) return 0;
        if (row == -1 || inRow >= profile.ticks(rows.get(row).duration60())) {
            row++;
            inRow = 0;
            if (row >= rows.size()) return 0;
            acceleration = nextAcceleration(acceleration, rows.get(row), true);
        } else {
            acceleration = nextAcceleration(acceleration, rows.get(row), false);
        }
        inRow++;
        return acceleration * profile.dtSquared();
    }
    public int row() { return row; }
    public float acceleration() { return acceleration; }
    public static float nextAcceleration(float preceding, Row row, boolean firstStepInRow) {
        if (firstStepInRow)
            return row.acceleration() == RETAIN_ACCELERATION ? preceding : row.acceleration();
        return preceding + row.incrementPerStep();
    }
}
