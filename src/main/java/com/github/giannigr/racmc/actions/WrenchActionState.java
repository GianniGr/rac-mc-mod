package com.github.giannigr.racmc.actions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.github.giannigr.racmc.actions.WrenchActionPresentation.Phase;

/**
 * Server-owned action lifecycle. No inventory transfer occurs: the detached wrench is presentation,
 * never a second ItemStack/entity. Timings of imported events are supplied by a lawful clip sampler;
 * source key indices and key-time thresholds are deliberately not equated with Minecraft ticks.
 */
public final class WrenchActionState {
    public static final float SLAM_DAMAGE = 10.2F;
    public static final int START_COOLDOWN_TICKS = 10;
    private long lastIntent = -1;
    private long serial;
    private long nextStartTick;
    private long startTick;
    private long lastTick = Long.MIN_VALUE;
    private Phase phase = Phase.IDLE;
    private double clipSeconds;
    private double wrenchClipSeconds;
    private double bodyBlendWeight;
    private double wrenchBlendWeight;
    private ActionVector position = ActionVector.ZERO;
    private ActionVector direction = ActionVector.ZERO;
    private double speed;
    private double deceleration;
    private double spin;
    private final Set<UUID> hitTargets = new HashSet<>();

    public enum Intent { SLAM, THROW }

    public boolean canStartAt(long tick) {
        return phase == Phase.IDLE && tick >= nextStartTick;
    }

    /** Consolidation seam: P27 calls only after its normal attack start is accepted. */
    public void normalAttackAccepted(long tick) {
        nextStartTick = Math.max(nextStartTick, tick + START_COOLDOWN_TICKS);
    }

    /** Values must come from server observations, never the intent packet. */
    public record Eligibility(boolean wrench, boolean alive, boolean allowedMode,
            boolean crouching, int airTicks, double groundClearance, boolean descending,
            boolean collisionSamplerReady) {}

    /**
     * Serial is monotonically increasing per connection, including rejected intents. This blocks
     * replay without allowing clients to pick action IDs, targets, damage, positions or clip time.
     */
    public boolean start(long intentSerial, Intent intent, long tick, Eligibility e,
            ActionVector hand, ActionVector aim) {
        if (intentSerial < 0 || intentSerial <= lastIntent) return false;
        lastIntent = intentSerial;
        if (!e.wrench || !e.alive || !e.allowedMode || !canStartAt(tick)) return false;
        if (intent == Intent.THROW) {
            if (!e.crouching || aim.lengthSquared() < 1.0e-9) return false;
            phase = Phase.THROW_WINDUP;
            direction = aim.normalized();
        } else {
            double clearance = e.descending ? 0.57 : 0.1;
            if (e.crouching || e.airTicks < 4 || e.groundClearance < clearance
                    || !e.collisionSamplerReady) return false;
            phase = Phase.SLAM_WINDUP;
        }
        serial++;
        startTick = tick;
        nextStartTick = tick + START_COOLDOWN_TICKS;
        lastTick = tick - 1;
        clipSeconds = 0;
        wrenchClipSeconds = 0;
        bodyBlendWeight = wrenchBlendWeight = 0;
        position = hand;
        spin = 0;
        speed = deceleration = 0;
        hitTargets.clear();
        return true;
    }

    /**
     * One call per server tick. Imported sequence sampling controls release/descent/catch/recovery;
     * render calls cannot advance these events. Three fixed 60Hz flight substeps per MC tick.
     */
    public void tick(long tick, boolean validOwner, boolean landed, ActionVector movingHand,
            ClipFrame frame, Surface surface) {
        if (phase == Phase.IDLE || tick <= lastTick) return;
        lastTick = tick;
        if (!validOwner || tick - startTick > 100) {
            cancel();
            return;
        }
        clipSeconds = frame.seconds;
        wrenchClipSeconds = frame.wrenchSeconds;
        bodyBlendWeight = frame.bodyBlendWeight;
        wrenchBlendWeight = frame.wrenchBlendWeight;
        if (phase == Phase.THROW_WINDUP && frame.release) {
            phase = Phase.DETACHED_OUT;
            position = movingHand;
            speed = 23;
        }
        if (phase == Phase.SLAM_WINDUP && frame.descending) phase = Phase.SLAM_DESCENT;
        if (phase == Phase.SLAM_DESCENT && landed) phase = Phase.RECOVERY;
        if (phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK) {
            for (int step = 0; step < 3; step++) {
                flightStep(movingHand, surface);
                if (phase == Phase.CATCH) break;
            }
        }
        if ((phase == Phase.CATCH || phase == Phase.RECOVERY) && frame.finished) cancel();
    }

    private void flightStep(ActionVector hand, Surface surface) {
        double dt = 1.0 / 60;
        spin = (spin + 24.434608 * dt) % (2 * Math.PI);
        if (phase == Phase.DETACHED_OUT) {
            ActionVector next = position.add(direction.multiply(speed * dt));
            ActionVector contact = surface.firstContact(position, next);
            if (contact != null) {
                position = contact;
                speed *= 0.5;
                phase = Phase.DETACHED_BACK;
            } else {
                position = next;
                // Independently expressed in world-units/second; accumulator is per source update.
                deceleration += 170 * dt * dt;
                speed = Math.max(0, speed - deceleration);
                if (speed == 0) phase = Phase.DETACHED_BACK;
            }
        } else {
            ActionVector toHand = hand.subtract(position);
            // The original accumulator continues across OUT -> BACK; it is not reset.
            deceleration += 0.9 * dt;
            speed += deceleration;
            double travel = speed * dt;
            if (toHand.length() <= Math.max(travel, 0.025)) {
                position = hand;
                phase = Phase.CATCH;
            } else {
                position = position.add(toHand.normalized().multiply(travel));
            }
        }
    }

    /**
     * Caller must first verify the imported active window/blend. Wrench contact may precede body
     * descent; body contact has its separate descending condition. Whole-action target dedup is
     * the explicit MC safety adaptation, not a claimed original rule.
     */
    public boolean claimTarget(UUID target) {
        return (phase == Phase.SLAM_WINDUP || phase == Phase.SLAM_DESCENT) && hitTargets.add(target);
    }

    public void cancel() {
        phase = Phase.IDLE;
        hitTargets.clear();
        position = ActionVector.ZERO;
    }

    public WrenchActionPresentation presentation() {
        boolean slam = phase == Phase.SLAM_WINDUP || phase == Phase.SLAM_DESCENT
                || phase == Phase.RECOVERY;
        boolean detached = phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK;
        return phase == Phase.IDLE ? WrenchActionPresentation.idle()
                : new WrenchActionPresentation(serial, phase, slam ? 43 : 26,
                        slam ? 10 : detached ? 6 : 1, clipSeconds, wrenchClipSeconds,
                        bodyBlendWeight, wrenchBlendWeight, !detached, position, spin);
    }

    public record ClipFrame(double seconds, double wrenchSeconds, double bodyBlendWeight,
            double wrenchBlendWeight, boolean release, boolean descending, boolean finished) {
        /** Convenience for unblended synthetic/static clips; original samplers supply both clocks. */
        public ClipFrame(double seconds, boolean release, boolean descending, boolean finished) {
            this(seconds, seconds, 1, 1, release, descending, finished);
        }

        public ClipFrame {
            if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(wrenchSeconds)
                    || wrenchSeconds < 0 || !Double.isFinite(bodyBlendWeight)
                    || bodyBlendWeight < 0 || bodyBlendWeight > 1
                    || !Double.isFinite(wrenchBlendWeight) || wrenchBlendWeight < 0
                    || wrenchBlendWeight > 1) {
                throw new IllegalArgumentException("Invalid clip time");
            }
        }
    }

    @FunctionalInterface
    public interface Surface {
        /** Null for no surface; supplied server-side. Never gameplay damage or crate interaction. */
        ActionVector firstContact(ActionVector from, ActionVector to);
    }
}
