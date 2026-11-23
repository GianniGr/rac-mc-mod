package com.github.giannigr.racmc.client.bomb;

/** Source-space class 121 appearance, independent of player or projectile simulation. */
final class BombGloveAppearance {
    private BombGloveAppearance() {}

    // OpenRAC 84bd565, mine_bomb_and_debris.c:1529–1534,1575–1576,1906–1908.
    // These are raw source modulation channels, not Minecraft sRGB multipliers.
    record Modulation(int red, int green, int blue) {}

    static Modulation held(long sourceUpdates) {
        int grey = 159 + (int) (96 * Math.sin(sourceUpdates * 0.068));
        return new Modulation(grey, grey, grey);
    }

    /** Fraction of the original scaled 30-update color timer still remaining. */
    static Modulation flight(float remainingColorFraction) {
        if (!Float.isFinite(remainingColorFraction)) {
            throw new IllegalArgumentException("Invalid bomb color timer");
        }
        float t = Math.clamp(remainingColorFraction, 0, 1);
        int greenBlue = (int) (64 + 64 * t);
        return new Modulation((int) (255 - 127 * t), greenBlue, greenBlue);
    }

    /** Rates about the original model axes; renderer applies the verified basis after import. */
    static final double SPIN_X_RADIANS_PER_SECOND = 2.0943952;
    static final double SPIN_Y_RADIANS_PER_SECOND = 7.8539815;
}
