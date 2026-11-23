package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1SourceTiming;
import com.github.giannigr.racmc.serverassets.Rac1WrenchData;
import com.github.giannigr.racmc.actions.WrenchActionPresentation.Phase;

/**
 * One accepted 60Hz source clock. Original key indices, header key-time and pose seconds remain
 * distinct. All trajectories are sampled from operator-local assets, never embedded/networked.
 */
public final class WrenchOriginalClock {
    private final boolean slam;
    private final Rac1Animation body;
    private final Rac1Animation child;
    private final Timeline timeline;
    private final double bodyStart;
    private final double childStart;
    private double seconds;
    private double childSeconds;
    private int updates;
    private int lastTick;
    private boolean released;
    private boolean caught;
    private int launchUpdate = -1;
    private java.util.List<Result> samples = java.util.List.of();
    private boolean observationKnown;
    private boolean previousGrounded;
    private double previousVelocity;
    private double previousClearance;
    private Result last;

    public WrenchOriginalClock(Rac1WrenchData data, boolean slam) {
        this.slam = slam;
        body = data.heroSequence(slam ? 43 : 26).orElseThrow();
        child = data.wrenchSequence(slam ? 10 : 6).orElseThrow();
        timeline = new Timeline(body.sourceTiming());
        bodyStart = timeline.indexSeconds(slam ? 4 : 0);
        childStart = slam ? new Timeline(child.sourceTiming()).indexSeconds(4) : 0;
        seconds = bodyStart;
        childSeconds = childStart;
        last = result(false, false, false, false);
    }

    public Result advance(int tick, Phase phase, boolean grounded, double verticalBlocksPerTick,
            double clearance) {
        return advance(tick, phase, grounded, verticalBlocksPerTick, clearance, phase == Phase.CATCH);
    }

    public Result advance(int tick, Phase phase, boolean grounded, double verticalBlocksPerTick,
            double clearance, boolean prepareCatch) {
        if (tick <= lastTick) return last;
        if (tick - lastTick > 100) throw new IllegalArgumentException("Unbounded source clock gap");
        boolean release = false;
        boolean active = false;
        boolean bodyContact = false;
        var sampled = new java.util.ArrayList<Result>();
        int firstStep = lastTick * 3;
        int stepCount = (tick - lastTick) * 3;
        if (!observationKnown) {
            previousGrounded = grounded;
            previousVelocity = verticalBlocksPerTick;
            previousClearance = clearance;
            observationKnown = true;
        }
        for (int step = lastTick * 3; step < tick * 3; step++) {
            double fraction = (step - firstStep + 1.0) / stepCount;
            boolean onGround = grounded && (previousGrounded || fraction == 1);
            double velocity = previousVelocity + (verticalBlocksPerTick - previousVelocity) * fraction;
            double height = previousClearance + (clearance - previousClearance) * fraction;
            boolean sampleActive = false, sampleBody = false, sampleRelease = false;
            boolean sampleSlamImpact = false;
            updates++;
            double previous = timeline.keyTime(seconds);
            if (!slam && prepareCatch && !caught) {
                seconds = timeline.indexSeconds(26);
                caught = true;
            }
            int blend = slam ? 11 : 10;
            if (updates > blend) {
                double speed = 1;
                if (slam) {
                    if (onGround || phase == Phase.RECOVERY) {
                        speed = previous < 26 ? 2 : 1;
                    } else if (updates <= 14 || velocity >= 0) {
                        speed = 0.33;
                    } else if (previous < 25) {
                        // Retiming to the landing estimate, not a second independent child clock.
                        double eta = Math.max(1.0 / 60,
                                height / Math.max(0.1, -velocity * 20));
                        speed = Math.clamp((timeline.secondsAt(26) - seconds) / eta, 0.5, 3);
                        if (height < 0.7) speed = Math.max(0.75, speed);
                    }
                }
                double delta = speed / 60;
                seconds = Math.min(timeline.lastSeconds(), seconds + delta);
                if (slam) childSeconds = Math.min(lastPoseSeconds(child), childSeconds + delta);
                double key = timeline.keyTime(seconds);
                if (!slam && !released && previous < 33 && key >= 33) {
                    release = true;
                    sampleRelease = true;
                    released = true;
                    launchUpdate = updates;
                }
                if (!slam && !caught && seconds >= timeline.indexSeconds(21)) {
                    seconds = timeline.indexSeconds(6)
                            + (seconds - timeline.indexSeconds(21));
                }
                if (slam) {
                    sampleSlamImpact = crossesSlamImpact(previous, key);
                    sampleActive = updates > 13 && previous <= 28 && key >= 1;
                    sampleBody = key > 23.5 && velocity < 0 && !onGround;
                    active |= sampleActive;
                    bodyContact |= sampleBody;
                }
            }
            boolean done = slam ? (onGround || phase == Phase.RECOVERY)
                    && timeline.keyTime(seconds) >= 30
                    : caught && seconds >= timeline.lastSeconds() - 1.0e-5;
            sampled.add(result(sampleRelease, sampleActive, sampleBody, done, onGround,
                    sampleSlamImpact));
        }
        samples = java.util.List.copyOf(sampled);
        previousGrounded = grounded;
        previousVelocity = verticalBlocksPerTick;
        previousClearance = clearance;
        lastTick = tick;
        boolean finished = slam ? (grounded || phase == Phase.RECOVERY)
                && timeline.keyTime(seconds) >= 30
                : caught && seconds >= timeline.lastSeconds() - 1.0e-5;
        // Impact is intentionally substep-only: its position comes from that exact pose.
        last = result(release, active, bodyContact, finished, grounded, false);
        return last;
    }

    private Result result(boolean release, boolean active, boolean bodyContact, boolean finished) {
        return result(release, active, bodyContact, finished, false, false);
    }

    private Result result(boolean release, boolean active, boolean bodyContact, boolean finished,
            boolean grounded, boolean slamImpact) {
        return new Result(new WrenchActionState.ClipFrame(seconds, childSeconds,
                Math.min(1, updates / (slam ? 11.0 : 10)),
                slam ? Math.min(1, updates / 13.0) : launchUpdate < 0 ? 1
                        : Math.min(1, (updates - launchUpdate) / 5.0), release,
                slam && updates > 14, finished, slamImpact), active, bodyContact, grounded);
    }

    static boolean crossesSlamImpact(double previousKey, double nextKey) {
        // Retail's window is [end - delta, end): key 27 must be strictly below end.
        return previousKey <= 27 && nextKey > 27;
    }

    public Result initial() { return last; }
    public java.util.List<Result> samples() { return samples; }

    private static double lastPoseSeconds(Rac1Animation clip) {
        return clip.sourceTiming().sourceSeconds()[clip.sourceTiming().sourceSeconds().length - 1];
    }

    public record Result(WrenchActionState.ClipFrame frame, boolean active, boolean bodyContact,
            boolean grounded) {}

    /** Immutable copied metadata; no asset IO in simulation or rendering. */
    public static final class Timeline {
        private final float[] seconds;
        private final int[] header;

        public Timeline(Rac1SourceTiming timing) {
            if (!timing.available()) throw new IllegalArgumentException("Original clock unavailable");
            seconds = timing.sourceSeconds();
            header = timing.headerTimes16();
            for (int i = 1; i < header.length; i++) {
                if (header[i] < header[i - 1]) {
                    throw new IllegalArgumentException("Non-monotonic original event clock");
                }
            }
        }

        public double indexSeconds(int key) {
            if (key < 0 || key >= seconds.length) {
                throw new IllegalArgumentException("Missing original key " + key);
            }
            return seconds[key];
        }

        public double lastSeconds() { return seconds[seconds.length - 1]; }

        public double keyTime(double time) {
            if (time < seconds[0]) return header[0] / 16.0;
            for (int i = 1; i < seconds.length; i++) {
                if (time < seconds[i] && seconds[i] > seconds[i - 1]) {
                    double fraction = (time - seconds[i - 1]) / (seconds[i] - seconds[i - 1]);
                    return (header[i - 1] + fraction * (header[i] - header[i - 1])) / 16;
                }
            }
            return header[header.length - 1] / 16.0;
        }

        public double secondsAt(double keyTime) {
            double target = keyTime * 16;
            if (target <= header[0]) return seconds[0];
            for (int i = 1; i < header.length; i++) {
                if (target <= header[i] && header[i] > header[i - 1]) {
                    return seconds[i - 1] + (seconds[i] - seconds[i - 1])
                            * (target - header[i - 1]) / (header[i] - header[i - 1]);
                }
            }
            return lastSeconds();
        }
    }
}
