package com.github.giannigr.racmc.block;

/** Client-local proximity state. No shop state, server authority or saved/networked data. */
public final class Rac1VendorVisualState {
    /** Measured closed footprint; thresholds use the same one-block scale as the kiosk. */
    public static final float REFERENCE_SCALE = 1.0f / 2.303f;
    public static final double ENTER_RADIUS = 16.0 * REFERENCE_SCALE;
    public static final double EXIT_RADIUS = 18.0 * REFERENCE_SCALE;
    public static final double ENTER_HEIGHT = 8.0 * REFERENCE_SCALE;
    public static final double EXIT_HEIGHT = 10.0 * REFERENCE_SCALE;
    private boolean near;
    private int framesUntilPoll;
    private float previous;
    private float growth;

    public boolean near() {
        return near;
    }

    public boolean contains(double horizontalSquared, double height) {
        double radius = near ? EXIT_RADIUS : ENTER_RADIUS;
        double limit = near ? EXIT_HEIGHT : ENTER_HEIGHT;
        return Double.isFinite(horizontalSquared) && horizontalSquared >= 0
                && Double.isFinite(height) && Math.abs(height) <= limit
                && horizontalSquared <= radius * radius;
    }

    /** Coordinates relative to the kiosk's ground origin, in blocks; no line-of-sight rule. */
    public double playerDistanceSquared(boolean alive, boolean spectator,
                                         double x, double y, double z) {
        double height = near ? EXIT_HEIGHT : ENTER_HEIGHT;
        if (!alive || spectator || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || Math.abs(y) > height) {
            return Double.POSITIVE_INFINITY;
        }
        return x * x + z * z;
    }

    /**
     * ReRAC interaction.md §9.1: poll every 8 retail frames, grow by .1 per frame.
     * Retail runs at 60 Hz; one Minecraft tick advances three retail frames.
     */
    public void tick(boolean playerInRange) {
        previous = growth;
        if (framesUntilPoll <= 0) {
            near = playerInRange;
            framesUntilPoll += 8;
        }
        framesUntilPoll -= 3;
        growth = Math.max(0.0f, Math.min(1.0f, growth + (near ? 0.3f : -0.3f)));
    }

    public float growth(float partialTick) {
        float partial = Math.max(0.0f, Math.min(1.0f, partialTick));
        return previous + (growth - previous) * partial;
    }
}
