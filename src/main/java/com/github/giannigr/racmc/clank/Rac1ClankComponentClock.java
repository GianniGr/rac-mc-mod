package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.asset.Rac1Animation;

/** Independent generic-moby interval clock; authored frame numbers are a separate axis. */
public final class Rac1ClankComponentClock {
    public record Sample(float convertedSeconds, double authoredFrame, boolean crossedKey,
                         boolean wrapped) {}
    private final Rac1Animation clip;
    private final float[] times;
    private final int[] mapping, headers, rates;
    private final float override;
    private int key;
    private double fraction;

    public Rac1ClankComponentClock(Rac1Animation clip) {
        if (!clip.sourceTiming().available() || clip.keyCount() < 1)
            throw new IllegalArgumentException("Original timing is required");
        this.clip = clip;
        times = clip.times().clone();
        var source = clip.sourceTiming();
        mapping = source.sampledSourceKeys();
        headers = source.headerTimes16();
        rates = source.keyRateBits();
        override = Float.intBitsToFloat(source.sequenceRateBits());
    }

    public Sample current() { return sample(false, false); }

    /** One original60-equivalent step; speed does not change blend-transition duration. */
    public Sample step(double speed) {
        if (!Double.isFinite(speed) || speed < 0 || speed > 16)
            throw new IllegalArgumentException("Invalid component speed");
        if (times.length == 1 || (!clip.loop() && key == times.length - 1)) return current();
        int sourceKey = mapping[key];
        double rate = override != 0 ? override : Float.intBitsToFloat(rates[sourceKey]);
        if (!(rate > 0) || !Double.isFinite(rate))
            throw new IllegalArgumentException("Unsupported original component rate");
        double remaining = speed;
        boolean crossed = false, wrapped = false;
        // Consume the fraction of the source step, then use the next interval's own rate.
        for (int guard = 0; remaining > 1e-12 && guard < 4096; guard++) {
            double until = (1 - fraction) / rate;
            if (remaining < until) { fraction += remaining * rate; remaining = 0; }
            else {
                remaining -= until;
                fraction = 0;
                key++;
                crossed = true;
                if (key >= times.length - 1) {
                    if (clip.loop()) { key = 0; wrapped = true; }
                    else { key = times.length - 1; remaining = 0; break; }
                }
                sourceKey = mapping[key];
                rate = override != 0 ? override : Float.intBitsToFloat(rates[sourceKey]);
                if (!(rate > 0) || !Double.isFinite(rate))
                    throw new IllegalArgumentException("Unsupported original component rate");
            }
        }
        if (remaining > 1e-12) throw new IllegalStateException("Unbounded component advance");
        return sample(crossed, wrapped);
    }

    private Sample sample(boolean crossed, boolean wrapped) {
        int next = Math.min(key + 1, times.length - 1);
        int sourceA = mapping[key], sourceB = mapping[next];
        double a = sourceA < 0 ? headers[0] / 16.0 : headers[sourceA] / 16.0;
        double b = sourceB < 0 ? a : headers[sourceB] / 16.0;
        return new Sample((float) (times[key] + (times[next] - times[key]) * fraction),
                a + (b - a) * fraction, crossed, wrapped);
    }

    public static double blendWeight(int elapsedSteps, int blendSteps) {
        if (elapsedSteps < 0 || blendSteps < 1) throw new IllegalArgumentException("Invalid blend");
        return Math.min(1, (double) elapsedSteps / blendSteps);
    }
}
