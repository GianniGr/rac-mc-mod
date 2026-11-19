package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankClock.Profile;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;

/**
 * Independently written source-step controller. No Minecraft hook/packet authorization here.
 * Velocity and requested displacement are ORIGINAL UNITS PER SOURCE STEP, never MC delta.
 * A host must resolve each request against native collision, then call resolved exactly once.
 */
public final class Rac1ClankMotion {
    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Nonfinite source displacement");
        }
        public double length() { return Math.sqrt(x*x + y*y + z*z); }
        public Vector scale(double scale) { return new Vector(x*scale, y*scale, z*scale); }
    }
    public record Observation(boolean grounded, double groundDistance, boolean forwardBlocked,
            boolean compatible, boolean hurt, int slotState, Vector nativeStepVelocity) {
        public Observation {
            if (!Double.isFinite(groundDistance) || groundDistance < 0 || groundDistance > 4096
                    || slotState < 0 || slotState > 3 || nativeStepVelocity == null)
                throw new IllegalArgumentException("Invalid native observation");
        }
    }
    public record Step(boolean controlled, boolean started, Action action, int sourceTime,
                       Vector displacement) {}
    public record Parameters(double gravity, double minimumHop, double maximumHop,
            int takeoff60, int heightRamp60, int maximumAir60, double airSpeed) {}
    public static Parameters parameters(Module module, Action action) {
        if (module == Module.NONE || (action != Action.HIGH_JUMP && action != Action.LONG_JUMP))
            throw new IllegalArgumentException("Not an original pack jump");
        if (action == Action.HIGH_JUMP)
            return module == Module.HELI ? new Parameters(29.7, 1.9, 1.95, 9, 5, 130, 3.3)
                    : new Parameters(29.7, .1, .2, 9, 14, 120, 1.9);
        return module == Module.HELI ? new Parameters(11, 1.9, 1.95, 5, 14, 120, 0)
                : new Parameters(8.5, .4, .9, 5, 1, 80, 11.5);
    }

    private final Profile profile;
    private Rac1ClankInput input = Rac1ClankInput.RELEASED;
    private Action action = Action.IDLE;
    private Module module = Module.NONE;
    private Vector velocity = Vector.ZERO, requested = Vector.ZERO;
    private long sourceClock, jumpPressed = Long.MIN_VALUE / 2;
    private int crouchSteps, phase;
    private boolean previousJump, spentAirJump, awaitingCollision, airborne;
    private double hopHeight, appliedHop, windupSpeed, gravity, facingX, facingZ = 1;
    private Parameters parameters;
    private Rac1ClankLiftCurve lift;

    public Rac1ClankMotion(Profile profile) { this.profile = java.util.Objects.requireNonNull(profile); }
    /** Once per input sample / MC tick, not once per source substep. */
    public void input(Rac1ClankInput sample) {
        if (sample.jumpHeld() && !previousJump) jumpPressed = sourceClock;
        previousJump = sample.jumpHeld();
        input = sample;
    }
    public Step step(Rac1ClankEquipment equipment, Observation observation) {
        if (awaitingCollision) throw new IllegalStateException("Unresolved previous source step");
        sourceClock++;
        crouchSteps = input.crouch() ? Math.min(crouchSteps + 1, profile.ticks(1000)) : 0;
        if (observation.grounded() && action == Action.IDLE) spentAirJump = false;
        if (!observation.compatible() || observation.hurt() || !equipment.worn()
                || (action != Action.IDLE && equipment.selected() != module)) cancel();
        boolean started = false;
        var context = new Rac1ClankCapability.Context(true, true, observation.slotState(),
                equipment.selected().sourceId());
        if (action == Action.IDLE && observation.compatible() && !observation.hurt()
                && equipment.worn()) {
            velocity = observation.nativeStepVelocity();
            boolean buffered = sourceClock - jumpPressed <= profile.ticks(9);
            if (observation.grounded() && !spentAirJump && input.crouch()
                    && crouchSteps > profile.ticks(6) && buffered
                    // Side/back flip priority is not silently turned into a pack high jump.
                    && (input.magnitude() < .7 || input.forward() > .7)) {
                double forwardSpeed = (-Math.sin(Math.toRadians(input.yawDegrees())) * velocity.x()
                        + Math.cos(Math.toRadians(input.yawDegrees())) * velocity.z()) / profile.dt();
                Action candidate = input.magnitude() > .7 && forwardSpeed > .9
                        && !observation.forwardBlocked() ? Action.LONG_JUMP : Action.HIGH_JUMP;
                if (Rac1ClankCapability.permits(equipment, context, candidate)) {
                    begin(equipment.selected(), candidate);
                    spentAirJump = true;
                    jumpPressed = Long.MIN_VALUE / 2;
                    started = true;
                }
            } else if (!observation.grounded() && input.jumpHeld() && velocity.y() <= 0
                    && observation.groundDistance() > .6
                    && Rac1ClankCapability.permits(equipment, context, Action.GLIDE)) {
                begin(equipment.selected(), Action.GLIDE);
                started = true;
            }
        }
        if (action == Action.IDLE) return idleStep();
        if (!Rac1ClankCapability.permits(equipment, context, action)) { cancel(); return idleStep(); }
        if (action != Action.GLIDE && !observation.grounded() && input.jumpHeld()
                && observation.groundDistance() > .6
                && phase > profile.ticks(module == Module.THRUSTER && action == Action.LONG_JUMP ? 8 : 20)
                && phase > profile.ticks(module == Module.HELI
                        ? action == Action.HIGH_JUMP ? 85 : 70 : 0)
                && Rac1ClankCapability.permits(equipment, context, Action.GLIDE)) {
            begin(module, Action.GLIDE);
            started = true;
        }
        if (action == Action.GLIDE) {
            if (observation.grounded() || Rac1ClankClock.glideReleased(profile, phase,
                    input.jumpA(), input.jumpB())) { cancel(); return idleStep(); }
            glide();
        } else {
            if (phase >= profile.ticks(parameters.maximumAir60())
                    || (module == Module.THRUSTER && action == Action.LONG_JUMP
                    && phase > profile.ticks(60) && observation.groundDistance() > 1.5)) {
                cancel(); return idleStep();
            }
            jump();
        }
        requested = velocity;
        awaitingCollision = true;
        Step result = new Step(true, started, action, phase, requested);
        phase++;
        return result;
    }

    private void begin(Module module, Action action) {
        this.module = module;
        this.action = action;
        phase = 0;
        airborne = action == Action.GLIDE;
        facingX = -Math.sin(Math.toRadians(input.yawDegrees()));
        facingZ = Math.cos(Math.toRadians(input.yawDegrees()));
        if (action == Action.GLIDE) {
            double cap = 4.5 * profile.dt();
            if (velocity.length() > cap) velocity = velocity.scale(cap / velocity.length());
        } else {
            parameters = parameters(module, action);
            hopHeight = parameters.minimumHop();
            appliedHop = 0;
            windupSpeed = Math.hypot(velocity.x(), velocity.z());
            gravity = parameters.gravity();
            lift = action == Action.HIGH_JUMP
                    ? new Rac1ClankLiftCurve(profile, module == Module.THRUSTER) : null;
        }
    }
    private void glide() {
        double targetSpeed = module == Module.THRUSTER ? 5 : 3;
        steer(targetSpeed, 15, 7, 720);
        velocity = new Vector(velocity.x(), -.72 * targetSpeed * profile.dt(), velocity.z());
    }
    private void jump() {
        int takeoff = profile.ticks(parameters.takeoff60());
        if (action == Action.LONG_JUMP && module == Module.THRUSTER && phase > profile.ticks(54))
            gravity += Math.clamp(25 - gravity, -7, 7);
        double gStep = gravity * profile.dtSquared();
        double liftStep = lift == null ? 0 : lift.step(phase);
        if (phase <= profile.ticks(15) && (input.jumpHeld() || appliedHop == 0)) {
            hopHeight = Math.min(parameters.maximumHop(), hopHeight
                    + (parameters.maximumHop() - parameters.minimumHop())
                    / profile.ticks(parameters.heightRamp60()));
        }
        if (action == Action.LONG_JUMP) {
            if (module == Module.HELI && phase < takeoff) {
                double push = 53 * profile.dtSquared() * phase;
                double displacement = windupSpeed + push;
                velocity = new Vector(facingX * displacement, velocity.y(), facingZ * displacement);
                double target = 5.7 * input.magnitude() * profile.dt();
                double rate = (target > windupSpeed ? 20 : 11) * profile.dtSquared();
                windupSpeed += Math.clamp(target - windupSpeed, -rate, rate);
            } else if (module == Module.THRUSTER) {
                double target = (phase >= profile.ticks(55) ? 3.5 : 11.5) * profile.dt();
                double current = Math.hypot(velocity.x(), velocity.z());
                double rate = (target > current ? 44 : 45) * profile.dtSquared();
                double speed = current + Math.clamp(target - current, -rate, rate);
                velocity = new Vector(facingX * speed, velocity.y(), facingZ * speed);
            }
            // No ordinary air steering for the Heli long jump.
        } else {
            // Original jump windup20/11; ordinary air AirAccel20, or2 at a small stick target.
            double airRate = parameters.airSpeed() * input.magnitude() < .2 * 5.7 ? 2 : 20;
            steer(parameters.airSpeed(), phase < takeoff ? 20 : airRate,
                    phase < takeoff ? 11 : airRate, 860);
        }
        if (phase < takeoff) velocity = new Vector(velocity.x(), 0, velocity.z());
        else {
            double totalHop = Math.sqrt(2 * hopHeight * gStep);
            double pending = Math.max(0, totalHop - appliedHop);
            appliedHop += pending;
            velocity = new Vector(velocity.x(), velocity.y() + pending + liftStep - gStep, velocity.z());
            airborne = true;
        }
    }
    private void steer(double speed, double acceleration, double brake, double turnDegreesPerSecond) {
        double currentSpeed = Math.hypot(velocity.x(), velocity.z());
        double magnitude = input.magnitude();
        double angle = currentSpeed > 1e-10 ? Math.atan2(velocity.x(), velocity.z())
                : Math.atan2(facingX, facingZ);
        if (magnitude > 0) {
            double targetAngle = Math.atan2(input.worldX(), input.worldZ());
            double delta = Math.IEEEremainder(targetAngle - angle, 2 * Math.PI);
            double cap = Math.toRadians(turnDegreesPerSecond) * profile.dt();
            angle += Math.clamp(delta, -cap, cap);
        }
        double target = speed * magnitude * profile.dt();
        double change = (target > currentSpeed ? acceleration : brake) * profile.dtSquared();
        double next = currentSpeed + Math.clamp(target - currentSpeed, -change, change);
        velocity = new Vector(Math.sin(angle)*next, velocity.y(), Math.cos(angle)*next);
    }
    /** Native collision feedback. Collision may consume momentum, NEVER replenish timers/lift. */
    public boolean resolved(Vector actual, boolean grounded) {
        if (!awaitingCollision) throw new IllegalStateException("No source step awaiting collision");
        awaitingCollision = false;
        velocity = new Vector(blocked(requested.x(), actual.x()) ? 0 : velocity.x(),
                blocked(requested.y(), actual.y()) ? 0 : velocity.y(),
                blocked(requested.z(), actual.z()) ? 0 : velocity.z());
        boolean landed = airborne && grounded && requested.y() <= 0;
        if (landed) {
            // Source Heli long lands into skid with .8 horizontal momentum.
            if (action == Action.LONG_JUMP && module == Module.HELI)
                velocity = new Vector(velocity.x()*.8, velocity.y(), velocity.z()*.8);
            cancel();
        }
        return landed;
    }
    private static boolean blocked(double requested, double actual) {
        return Math.abs(requested - actual) > 1e-6;
    }
    private Step idleStep() { return new Step(false, false, Action.IDLE, 0, Vector.ZERO); }
    public void cancel() { action = Action.IDLE; phase = 0; lift = null; awaitingCollision = false; }
    public Action action() { return action; }
    public Module module() { return module; }
    public int sourceTime() { return phase; }
    public Vector velocity() { return velocity; }
    public boolean spentAirJump() { return spentAirJump; }
}
