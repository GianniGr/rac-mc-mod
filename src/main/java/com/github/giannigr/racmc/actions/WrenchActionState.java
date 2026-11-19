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
    private ActionRotation rotation = ActionRotation.IDENTITY;
    private boolean nearCatch;
    private double catchBlend;
    private int cometIdentity = 1;
    private final java.util.List<FlightStep> flightSteps = new java.util.ArrayList<>();
    private long dispatchedFlightTick = Long.MIN_VALUE;
    private final Set<Object> hitCrates = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());
    private final Set<UUID> hitTargets = new HashSet<>();
    private final Set<Long> hitBlocks = new HashSet<>();

    public enum Intent { SLAM, THROW }

    public boolean canStartAt(long tick) {
        return phase == Phase.IDLE && tick >= nextStartTick;
    }

    public boolean freshIntent(long serial) {
        return serial >= 0 && serial > lastIntent;
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
        if (!freshIntent(intentSerial)) return false;
        lastIntent = intentSerial;
        if (!e.wrench || !e.alive || !e.allowedMode || !canStartAt(tick)) return false;
        if (intent == Intent.THROW) {
            if (!e.crouching || aim.lengthSquared() < 1.0e-9) return false;
            phase = Phase.THROW_WINDUP;
            direction = aim.normalized();
            cometIdentity = 3 - cometIdentity;
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
        nearCatch = false;
        catchBlend = 0;
        speed = deceleration = 0;
        hitTargets.clear();
        hitBlocks.clear();
        hitCrates.clear();
        flightSteps.clear();
        return true;
    }

    /**
     * One call per server tick. Imported sequence sampling controls release/descent/catch/recovery;
     * render calls cannot advance these events. Three fixed 60Hz flight substeps per MC tick.
     */
    public void tick(long tick, boolean validOwner, boolean landed, ActionVector movingHand,
            ClipFrame frame, Surface surface) {
        tick(tick, validOwner, landed, movingHand, ActionRotation.IDENTITY, frame, surface);
    }

    public void tick(long tick, boolean validOwner, boolean landed, ActionVector movingHand,
            ActionRotation handRotation, ClipFrame frame, Surface surface) {
        var sample = new SourceFrame(frame, movingHand, handRotation, landed);
        tickSources(tick, validOwner, java.util.List.of(sample, sample, sample), surface);
    }

    public void tickSources(long tick, boolean validOwner, java.util.List<SourceFrame> sources,
            Surface surface) {
        if (phase == Phase.IDLE || tick <= lastTick) return;
        if (sources.size() != 3) throw new IllegalArgumentException("Expected three original substeps");
        lastTick = tick;
        flightSteps.clear();
        if (!validOwner || tick - startTick > 100) {
            cancel();
            return;
        }
        for (int step = 0; step < sources.size(); step++) {
            SourceFrame source = sources.get(step);
            ClipFrame frame = source.frame;
            int oldSequence = (phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK)
                    && !nearCatch ? 6 : 1;
            double oldBlend = nearCatch ? catchBlend : wrenchBlendWeight;
            clipSeconds = frame.seconds;
            wrenchClipSeconds = frame.wrenchSeconds;
            bodyBlendWeight = frame.bodyBlendWeight;
            wrenchBlendWeight = frame.wrenchBlendWeight;
            if (phase == Phase.THROW_WINDUP && frame.release) {
                phase = Phase.DETACHED_OUT;
                position = source.hand;
                rotation = source.rotation;
                speed = 23;
            }
            if (phase == Phase.SLAM_WINDUP && frame.descending) phase = Phase.SLAM_DESCENT;
            if (phase == Phase.SLAM_DESCENT && source.grounded) phase = Phase.RECOVERY;
            if (phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK) {
                ActionVector old = position;
                double oldSpin = spin;
                ActionRotation oldRotation = rotation;
                ActionVector returnOrigin = flightStep(source.hand, source.rotation, surface);
                flightSteps.add(new FlightStep(step, old, position, oldSpin, spin, oldRotation, rotation,
                        oldSequence, oldBlend,
                        nearCatch || phase == Phase.CATCH ? 1 : 6,
                        nearCatch ? catchBlend : wrenchBlendWeight, returnOrigin));
            }
            if ((phase == Phase.CATCH || phase == Phase.RECOVERY) && frame.finished) {
                var finalSteps = java.util.List.copyOf(flightSteps);
                var alreadyHit = java.util.List.copyOf(hitCrates);
                cancel();
                flightSteps.addAll(finalSteps); // Retail catch continues into this update's query.
                hitCrates.addAll(alreadyHit);
            }
        }
    }

    private ActionVector flightStep(ActionVector hand, ActionRotation handRotation, Surface surface) {
        double dt = 1.0 / 60;
        spin = (spin + 24.434608 * dt) % (2 * Math.PI);
        double ground = surface.groundHeight(position);
        if (Double.isFinite(ground) && position.y() - ground < 0.8) {
            double target = ground + 0.55;
            double limit = (position.y() < target ? 4 : 1) * dt;
            double dy = Math.clamp(target - position.y(), -limit, limit);
            position = position.add(new ActionVector(0, dy, 0));
        }
        ActionVector returnOrigin = phase == Phase.DETACHED_BACK ? position : null;
        if (phase == Phase.DETACHED_OUT) {
            ActionVector next = position.add(direction.multiply(speed * dt));
            ActionVector contact = surface.firstContact(position, next);
            position = next; // Original surface response does not snap the computed position back.
            if (contact != null) {
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
            if (toHand.length() < travel * 5) nearCatch = true;
            if (nearCatch) catchBlend = Math.min(1, catchBlend + 0.2);
            if (toHand.length() <= Math.max(travel, 0.025)) {
                position = hand;
                rotation = handRotation;
                spin = 0;
                catchBlend = 1;
                phase = Phase.CATCH;
            } else {
                position = position.add(toHand.normalized().multiply(travel));
            }
        }
        return returnOrigin;
    }

    /**
     * Caller must first verify the imported active window/blend. Wrench contact may precede body
     * descent; body contact has its separate descending condition. Whole-action target dedup is
     * the explicit MC safety adaptation, not a claimed original rule.
     */
    public boolean claimTarget(UUID target) {
        return claimTarget(target, false);
    }

    public boolean claimTarget(UUID target, boolean wrenchWindow) {
        return (phase == Phase.SLAM_WINDUP || phase == Phase.SLAM_DESCENT
                || phase == Phase.RECOVERY && wrenchWindow) && hitTargets.add(target);
    }

    public boolean claimBlock(long position) {
        return claimBlock(position, false);
    }

    public boolean claimBlock(long position, boolean wrenchWindow) {
        return (phase == Phase.SLAM_WINDUP || phase == Phase.SLAM_DESCENT
                || phase == Phase.RECOVERY && wrenchWindow) && hitBlocks.add(position);
    }

    public boolean claimThrownCrate(Object lifecycle) {
        return !flightSteps.isEmpty() && hitCrates.add(lifecycle);
    }

    public java.util.List<FlightStep> flightSteps() { return java.util.List.copyOf(flightSteps); }
    public java.util.List<FlightStep> beginFlightContacts(long tick) {
        if (tick != lastTick || dispatchedFlightTick == tick) return java.util.List.of();
        dispatchedFlightTick = tick;
        return flightSteps();
    }
    public void endFlightContacts() {
        flightSteps.clear();
        if (phase == Phase.IDLE) hitCrates.clear();
    }
    public int cometIdentity() { return cometIdentity; }

    public void cancel() {
        phase = Phase.IDLE;
        hitTargets.clear();
        hitBlocks.clear();
        hitCrates.clear();
        flightSteps.clear();
        position = ActionVector.ZERO;
    }

    public WrenchActionPresentation presentation() {
        boolean slam = phase == Phase.SLAM_WINDUP || phase == Phase.SLAM_DESCENT
                || phase == Phase.RECOVERY;
        boolean detached = phase == Phase.DETACHED_OUT || phase == Phase.DETACHED_BACK;
        return phase == Phase.IDLE ? WrenchActionPresentation.idle()
                : new WrenchActionPresentation(serial, phase, slam ? 43 : 26,
                        slam ? 10 : detached && !nearCatch ? 6 : 1, clipSeconds, wrenchClipSeconds,
                        bodyBlendWeight, nearCatch ? catchBlend : wrenchBlendWeight,
                        !detached, position, spin, rotation);
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

    public record SourceFrame(ClipFrame frame, ActionVector hand, ActionRotation rotation,
            boolean grounded) {}
    public record FlightStep(int substep, ActionVector from, ActionVector to, double fromSpin,
            double toSpin, ActionRotation fromRotation, ActionRotation toRotation, int fromSequence,
            double fromBlend, int sequence, double blend, ActionVector returnOrigin) {}

    @FunctionalInterface
    public interface Surface {
        /** Null for no surface; supplied server-side. Never gameplay damage or crate interaction. */
        ActionVector firstContact(ActionVector from, ActionVector to);
        default double groundHeight(ActionVector position) { return Double.NaN; }
    }
}
