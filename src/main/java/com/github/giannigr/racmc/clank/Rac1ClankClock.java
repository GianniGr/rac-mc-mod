package com.github.giannigr.racmc.clank;

/** Original-time units. No client ISO selects the authoritative rate. No player motion here. */
public final class Rac1ClankClock {
    public static final int SOURCE_SUBSTEPS_PER_MC_TICK = 3;
    public enum Profile {
        ORIGINAL_60(60, 0x3c888889, 0x3991a2b4, 1f, 1f, 1f),
        ORIGINAL_50(50, 0x3ca3d70b, 0x39d1b718, 1.2f, 1.44f, Float.intBitsToFloat(0x3f555555));
        private final int hz, dtBits, dtSquaredBits;
        private final float speedScale, squaredScale, timerScale;
        Profile(int hz, int dtBits, int dtSquaredBits, float speedScale,
                float squaredScale, float timerScale) {
            this.hz = hz; this.dtBits = dtBits; this.dtSquaredBits = dtSquaredBits;
            this.speedScale = speedScale; this.squaredScale = squaredScale; this.timerScale = timerScale;
        }
        public int hz() { return hz; }
        public float dt() { return Float.intBitsToFloat(dtBits); }
        public float dtSquared() { return Float.intBitsToFloat(dtSquaredBits); }
        public float speedScale() { return speedScale; }
        public float squaredScale() { return squaredScale; }
        public int ticks(int source60Ticks) {
            if (source60Ticks < 0 || source60Ticks > 1_000_000)
                throw new IllegalArgumentException("Invalid source duration");
            return (int) (source60Ticks * timerScale + .5f);
        }
        public int heliLiftStart() { return ticks(this == ORIGINAL_50 ? 34 : 35); }
        public int heliLiftEnd() { return heliLiftStart() + ticks(36); }
        public int thrusterLiftStart() { return ticks(9); }
        public int thrusterLiftEnd() { return thrusterLiftStart() + ticks(35); }
    }
    private Rac1ClankClock() {}
    public static boolean glideReleased(Profile profile, int elapsedSourceSteps,
            boolean heldViewA, boolean heldViewB) {
        return elapsedSourceSteps > profile.ticks(30) && !heldViewA && !heldViewB;
    }
    public static double sourceDisplacementToMcDelta(double unitsPerSourceStep, Profile profile) {
        if (!Double.isFinite(unitsPerSourceStep)) throw new IllegalArgumentException("Invalid velocity");
        return unitsPerSourceStep * profile.hz() / 20;
    }
}
