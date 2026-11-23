package com.github.giannigr.racmc.ship;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Single-seat vehicle. Deliberately has no vanilla controlling passenger:
 * native vehicle-movement packets cannot mutate it. The passenger list still drives native seating and tracking. */
public final class Rac1TravelShip extends Entity {
    public enum AccessPhase { PARKED, BOARDING, PILOTING, EXITING }
    public enum VerticalBoost { NONE, CLIMB, DESCEND }

    /** Resolved movement axes for presentation, separate from the enabled mode or requested keys. */
    public record BoostThrust(boolean forward, VerticalBoost vertical) {}

    /** Presentation-only snapshot; see {@link #localToWorld} for axes. */
    public record AccessSnapshot(AccessPhase phase, long startTick, int serial,
                                 Vec3 boardingLocal, Vec3 seatLocal, Vec3 exitLocal) {}

    private static final int ACCESS_TICKS = 8;
    private static final EntityDataAccessor<Byte> PHASE =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PHASE_SERIAL =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SESSION =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROUND_SERIAL =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BOOSTED =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> ACCESS_OFFSET =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> ENGINE_STATE =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> BOOST_THRUST =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<CompoundTag> ACCESS_PATH =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> ACCESS_PROGRESS =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> NORMAL_FORWARD =
            SynchedEntityData.defineId(Rac1TravelShip.class, EntityDataSerializers.BOOLEAN);

    private ShipFlight.Input input = new ShipFlight.Input();
    private final ShipEngineBeat.Inbox engineInbox = new ShipEngineBeat.Inbox();
    private long engineBeatSerial;
    private int groundCounter;
    private boolean wasAirborne;
    private ServerPlayer mounting;
    private Vec3 checkedExit;
    private Vec3 dismountOverride;
    private Vec3 recoveryBerth;
    private final ShipTrackingInterpolation trackingInterpolation = new ShipTrackingInterpolation();
    private long lastMotionViewTick = Long.MIN_VALUE;
    private final ShipCockpitProfile cockpit;
    private Transit transit;
    private int passedAccessTicks;
    private int preferredExitSide;
    private Vec3 hullShapeOrigin;
    private float hullShapeYaw;
    private List<AABB> hullShape = List.of();

    private record Transit(ShipAccessPath path, Vec3 outside, Vec3 hullOrigin, float hullYaw) {}

    public Rac1TravelShip(EntityType<Rac1TravelShip> type, Level level) {
        this(type, level, ShipCockpitRefit.activeProfile());
    }

    /** Package-local fixture constructor; no public switch enabling the measured candidate. */
    Rac1TravelShip(EntityType<Rac1TravelShip> type, Level level, ShipCockpitProfile cockpit) {
        super(type, level);
        this.cockpit = cockpit;
        if (cockpit != ShipCockpitRefit.activeProfile()) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (cockpit == null || cockpit == ShipCockpitProfile.CURRENT) return super.getDimensions(pose);
        return EntityDimensions.fixed(cockpit.width(), cockpit.height());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) AccessPhase.PARKED.ordinal());
        builder.define(PHASE_START, 0L);
        builder.define(PHASE_SERIAL, 0);
        builder.define(SESSION, 0);
        builder.define(GROUND_SERIAL, 0);
        builder.define(BOOSTED, false);
        builder.define(ACCESS_OFFSET, new Vector3f());
        builder.define(ENGINE_STATE, (byte) ShipEngineState.UNVERIFIED.ordinal());
        builder.define(BOOST_THRUST, (byte) 0);
        builder.define(ACCESS_PATH, new CompoundTag());
        builder.define(ACCESS_PROGRESS, 0);
        builder.define(NORMAL_FORWARD, false);
    }

    public AccessPhase accessPhase() { return AccessPhase.values()[entityData.get(PHASE)]; }
    public @Nullable ShipAccessPath accessPath() {
        return level().isClientSide ? ShipAccessPath.decode(entityData.get(ACCESS_PATH))
                : transit != null ? transit.path : null;
    }
    /** Last server-checked elapsed path tick. Do not extrapolate rider feet past this value. */
    public int accessPathProgress() { return level().isClientSide ? entityData.get(ACCESS_PROGRESS) : passedAccessTicks; }
    public int seatSession() { return entityData.get(SESSION); }
    public int groundSerial() { return entityData.get(GROUND_SERIAL); }
    public boolean isBoosted() { return entityData.get(BOOSTED); }

    public BoostThrust boostThrust() {
        byte axes = isBoosted() ? entityData.get(BOOST_THRUST) : 0;
        var vertical = (axes & ShipFlight.THRUST_CLIMB) != 0 ? VerticalBoost.CLIMB
                : (axes & ShipFlight.THRUST_DESCEND) != 0 ? VerticalBoost.DESCEND : VerticalBoost.NONE;
        return new BoostThrust((axes & ShipFlight.THRUST_FORWARD) != 0, vertical);
    }

    public ShipEngineState engineState() { return ShipEngineState.values()[entityData.get(ENGINE_STATE)]; }

    /** Active rear thrust only; vertical Turbo is a separate axis and cannot light the rear jets. */
    public ShipThrust engineThrust() {
        if (accessPhase() != AccessPhase.PILOTING || !engineState().inFlight()) return ShipThrust.OFF;
        if (isBoosted()) return boostThrust().forward() ? ShipThrust.BOOST : ShipThrust.OFF;
        return entityData.get(NORMAL_FORWARD) ? ShipThrust.NORMAL : ShipThrust.OFF;
    }

    /** Client-only short admission window; consumers must deduplicate by serial. Never seeded on pairing. */
    public @Nullable ShipEngineBeat engineBeat() { return engineInbox.sample(tickCount); }

    void receiveEngineBeat(ShipEngineBeat beat) {
        engineInbox.offer(beat, getId(), getUUID(), tickCount);
    }

    private void setEngineState(ShipEngineState state) {
        entityData.set(ENGINE_STATE, (byte) state.ordinal());
        if (state == ShipEngineState.UNVERIFIED) entityData.set(BOOST_THRUST, (byte) 0);
        if (!state.inFlight()) entityData.set(NORMAL_FORWARD, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && ((ENGINE_STATE.equals(key) && engineState() == ShipEngineState.UNVERIFIED)
                || (PHASE.equals(key) && accessPhase() == AccessPhase.PARKED))) engineInbox.clearSample();
    }

    @Override
    public void onClientRemoval() {
        engineInbox.clearSample();
        super.onClientRemoval();
    }

    /** A late tracking client receives the same phase/time/serial as existing observers. */
    public AccessSnapshot accessSnapshot() {
        var phase = accessPhase();
        Vec3 anchor = new Vec3(entityData.get(ACCESS_OFFSET));
        return new AccessSnapshot(phase, entityData.get(PHASE_START), entityData.get(PHASE_SERIAL),
                phase == AccessPhase.BOARDING ? anchor : null, cockpit.seatLocal(),
                phase == AccessPhase.EXITING ? anchor : null);
    }

    /** At yaw 0 local axes match vanilla world axes: +X east, +Y up, +Z south (bow). */
    public Vec3 localToWorld(Vec3 anchor) {
        return position().add(rotateAccessVector(anchor, getYRot()));
    }

    private Vec3 worldToLocal(Vec3 anchor) {
        return rotateAccessVector(anchor.subtract(position()), -getYRot());
    }

    // Use the same rotation as the pedestrian footprint. The vanilla trig lookup
    // can put a walker touching a diagonal face on the wrong side of its plane.
    private static Vec3 rotateAccessVector(Vec3 vector, double yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        return new Vec3(cos * vector.x - sin * vector.z, vector.y, sin * vector.x + cos * vector.z);
    }

    /** Native rider feet; the vanilla pilot eye is relative to this same body origin. */
    public Vec3 pilotAnchor() { return localToWorld(cockpit.seatLocal()); }

    private void setPhase(AccessPhase phase, Vec3 worldAnchor) {
        entityData.set(ACCESS_OFFSET, worldAnchor == null ? new Vector3f() : worldToLocal(worldAnchor).toVector3f());
        entityData.set(PHASE_START, level().getGameTime());
        entityData.set(PHASE_SERIAL, entityData.get(PHASE_SERIAL) + 1);
        entityData.set(PHASE, (byte) phase.ordinal());
        entityData.set(GROUND_SERIAL, 0);
        if (phase != AccessPhase.PILOTING) {
            entityData.set(BOOSTED, false);
            entityData.set(BOOST_THRUST, (byte) 0);
            entityData.set(NORMAL_FORWARD, false);
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        var route = accessPath();
        Vec3 seat = route == null ? pilotAnchor() : localToWorld(route.sample(accessPathProgress()));
        move.accept(passenger, seat.x, seat.y, seat.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger == mounting && getPassengers().isEmpty();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer pilot) || !(level() instanceof ServerLevel level)
                || !pilot.isAlive() || !level.dimension().equals(Level.OVERWORLD)) return InteractionResult.FAIL;
        if (!pilot.canInteractWithEntity(this, 0)) {
            traceBoarding(pilot, "reach");
            return InteractionResult.FAIL;
        }
        if (isVehicle()) return feedback(pilot, "occupied");
        if (!supported(level) || getDeltaMovement().lengthSqr() != 0) return feedback(pilot, "land_first");
        AABB hull = getBoundingBox();
        var standing = pilot.getDimensions(Pose.STANDING);
        var boardFrom = pilot.position();
        boolean nearSide = isCockpitSide(pilot, boardFrom);
        if (!cockpit.fitsAtAllHeadings(standing)
                || !ShipCockpitProfile.containsPilot(hull, pilotAnchor(), standing)
                || !ShipSpace.loaded(level, hull) || !level.noCollision(this, hull)
                 || !ShipSpace.dryAndSafe(level, hull)) {
            traceBoarding(pilot, "hull_or_pilot_bounds");
            return feedback(pilot, "blocked");
        }
        if (nearSide) {
            if (!canAimAtCockpitSide(level, pilot, boardFrom)) {
                traceBoarding(pilot, "side_aim_or_line_of_sight");
                return feedback(pilot, "aim_side");
            }
        } else if (!ShipSpace.loaded(level, new AABB(pilot.getEyePosition(), getEyePosition()))
                || !pilot.hasLineOfSight(this)) {
            traceBoarding(pilot, "center_line_of_sight");
            return feedback(pilot, "blocked");
        }
        List<Vec3> ingress = routedCockpit() ? routeToStand(level, pilot, boardFrom, false) : null;
        if (routedCockpit() && ingress == null) return feedback(pilot, "blocked");
        mounting = pilot;
        if (ingress != null) {
            // Native startRiding immediately asks for the rider position, before it returns.
            transit = new Transit(new ShipAccessPath(ingress, level.getGameTime(), 0, false),
                    boardFrom, position(), getYRot());
            passedAccessTicks = 0;
        }
        try {
            if (!pilot.startRiding(this)) {
                clearTransit();
                return InteractionResult.FAIL;
            }
        } finally {
            mounting = null;
        }
        input = new ShipFlight.Input();
        entityData.set(SESSION, seatSession() + 1);
        preferredExitSide = nearSide ? (worldToLocal(boardFrom).x < 0 ? -1 : 1) : 0;
        recoveryBerth = findExit(level, pilot);
        if (ingress == null) setPhase(AccessPhase.BOARDING, boardFrom);
        else {
            publishTransit(AccessPhase.BOARDING, boardFrom, ingress, false);
            traceBoarding(pilot, "accepted");
        }
        return InteractionResult.CONSUME;
    }

    boolean receive(ServerPlayer sender, int session, int sequence, int clientGround, int bits) {
        return sender.isAlive() && !sender.isSpectator() && !isRemoved()
                && sender.level() == level() && sender.getVehicle() == this && getFirstPassenger() == sender
                && accessPhase() == AccessPhase.PILOTING && seatSession() == session
                && input.accept(sequence, clientGround, groundSerial(),
                        bits, level().getGameTime());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 before = position();
            var frame = trackingInterpolation.advance(before, getYRot());
            if (!frame.position().equals(before)) setPos(frame.position());
            setYRot(frame.yaw());
            if (frame.rebase()) {
                // ClientLevel captures hull history before this tick and rider history immediately
                // afterwards. A genuine warp must rebase both, not just the hull before that tick.
                setOldPosAndRot();
                for (var rider : getPassengers()) {
                    positionRider(rider);
                    rider.setOldPosAndRot();
                }
            }
            if (ShipMotionTrace.ENABLED) ShipMotionTrace.log(this, "tracking_tick", "before", before,
                    "after", position(), "visual_delta", position().subtract(before), "tracked_velocity", getDeltaMovement(),
                    "target", trackingInterpolation.target(position()), "rebase", frame.rebase());
            return;
        }
        if (!(level() instanceof ServerLevel level) || !(getFirstPassenger() instanceof ServerPlayer pilot)) {
            setDeltaMovement(Vec3.ZERO);
            setEngineState(ShipEngineState.UNVERIFIED);
            return;
        }
        if (!pilot.isAlive() || pilot.isSpectator() || !level.dimension().equals(Level.OVERWORLD)) {
            pilot.stopRiding();
            return;
        }

        if (transit != null) {
            tickTransit(level, pilot);
            return;
        }
        long now = level.getGameTime();
        long phaseAge = now - entityData.get(PHASE_START);
        switch (accessPhase()) {
            case BOARDING -> {
                sampleIdleEngine(level);
                if (phaseAge >= ACCESS_TICKS) setPhase(AccessPhase.PILOTING, null);
            }
            case EXITING -> {
                sampleIdleEngine(level);
                if (phaseAge >= ACCESS_TICKS) finishExit(level, pilot);
            }
            case PILOTING -> {
                refreshGroundSerial(level, pilot);
                boolean toggle = input.consumeBoost(now);
                if (input.consumeExit(now, groundSerial())) {
                    sampleIdleEngine(level);
                    requestExit(level, pilot);
                } else {
                    if (toggle) entityData.set(BOOSTED, !isBoosted());
                    fly(level, input.fresh(now));
                    refreshGroundSerial(level, pilot);
                }
            }
            default -> { }
        }
    }

    private boolean supported(ServerLevel level) {
        // Landing requires support near the center, not just a wing skimming a ledge.
        double x = getX(), y = getY(), z = getZ();
        return ShipSpace.solidUnder(level, this, new AABB(x - .10, y, z - .10, x + .10, y + .01, z + .10));
    }

    private void refreshGroundSerial(ServerLevel level, ServerPlayer pilot) {
        boolean supported = supported(level);
        if (!supported) {
            wasAirborne = true;
        } else if (wasAirborne && (onGround() || getDeltaMovement().y == 0)) {
            wasAirborne = false;
            entityData.set(BOOSTED, false);
            entityData.set(BOOST_THRUST, (byte) 0);
            input.consumeBoost(level.getGameTime());
        }
        if (!supported || getDeltaMovement().lengthSqr() > .0004) {
            entityData.set(GROUND_SERIAL, 0);
        } else if (groundSerial() == 0) {
            if (++groundCounter == 0) ++groundCounter;
            entityData.set(GROUND_SERIAL, groundCounter);
            Vec3 berth = findExit(level, pilot);
            if (berth != null) recoveryBerth = berth;
        }
    }

    void fly(ServerLevel level, int flags) {
        var step = ShipFlight.step(flags, getYRot(), getDeltaMovement(), supported(level), isBoosted());
        var sweep = getBoundingBox().expandTowards(step.velocity());
        boolean loaded = ShipSpace.loaded(level, sweep);
        if (!loaded || !ShipSpace.dryAndSafe(level, sweep)) {
            setDeltaMovement(Vec3.ZERO);
            setEngineState(ShipEngineState.UNVERIFIED);
            if (ShipMotionTrace.ENABLED) ShipMotionTrace.log(this, "guard_stop", "pos", position(),
                    "requested", step.velocity(), "tracked_velocity", getDeltaMovement(), "loaded", loaded, "flags", flags);
            return;
        }
        if (ShipHullCollision.hitsWalker(level, this, getBoundingBox(), step.velocity(), getYRot(), step.yaw())) {
            setDeltaMovement(Vec3.ZERO);
            setEngineState(ShipEngineState.UNVERIFIED);
            return;
        }
        setYRot(step.yaw());
        if (step.velocity().lengthSqr() == 0) {
            setDeltaMovement(Vec3.ZERO);
            sampleIdleEngine(level);
            if (ShipMotionTrace.ENABLED) ShipMotionTrace.log(this, "idle", "pos", position(), "flags", flags);
            return;
        }
        var before = ShipEnginePhysics.contact(level, this, getBoundingBox());
        Vec3 start = position();
        move(MoverType.SELF, step.velocity());
        Vec3 actual = position().subtract(start);
        Vec3 requested = step.velocity();
        setDeltaMovement(new Vec3(unblocked(actual.x, requested.x), unblocked(actual.y, requested.y),
                unblocked(actual.z, requested.z)));
        if (ShipMotionTrace.ENABLED) ShipMotionTrace.log(this, "move", "before", start, "after", position(),
                "requested", requested, "actual", actual, "tracked_velocity", getDeltaMovement(), "flags", flags);
        var after = ShipEnginePhysics.contact(level, this, getBoundingBox());
        entityData.set(BOOST_THRUST, isBoosted() && after != ShipEngineState.UNVERIFIED
                ? step.actualBoostThrust(actual) : (byte) 0);
        var kind = ShipEnginePhysics.transition(before, after, actual);
        if (before == ShipEngineState.AIRBORNE && after == ShipEngineState.AIRBORNE
                && ShipEnginePhysics.verifiedApproach(level, this, flags, actual, requested)) {
            after = ShipEngineState.APPROACH;
        }
        entityData.set(NORMAL_FORWARD, !isBoosted() && after.inFlight() && step.deliveredForward(actual));
        setEngineState(after);
        if (kind != null && accessPhase() == AccessPhase.PILOTING && getFirstPassenger() instanceof ServerPlayer) {
            PacketDistributor.sendToPlayersTrackingEntity(this,
                    new ShipEngineBeat(getId(), getUUID(), kind, ++engineBeatSerial, level.getGameTime()));
        }
    }

    private void sampleIdleEngine(ServerLevel level) {
        entityData.set(BOOST_THRUST, (byte) 0);
        entityData.set(NORMAL_FORWARD, false);
        setEngineState(ShipEnginePhysics.contact(level, this, getBoundingBox()));
    }

    private static double unblocked(double actual, double requested) {
        return Math.abs(actual - requested) < 1e-6 ? requested : 0;
    }

    private void requestExit(ServerLevel level, ServerPlayer pilot) {
        if (!supported(level) || getDeltaMovement().lengthSqr() > .0004) {
            feedback(pilot, "land_first");
            return;
        }
        checkedExit = findExit(level, pilot);
        if (checkedExit == null) {
            feedback(pilot, "blocked");
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (routedCockpit()) {
            var path = routeToStand(level, pilot, checkedExit, true);
            if (path == null) {
                checkedExit = null;
                feedback(pilot, "blocked");
                return;
            }
            publishTransit(AccessPhase.EXITING, checkedExit, path, false);
        } else setPhase(AccessPhase.EXITING, checkedExit);
    }

    private Vec3 findExit(ServerLevel level, ServerPlayer pilot) {
        if (routedCockpit()) {
            var side = findSideExit(level, pilot);
            if (side != null) return side;
        }
        var facing = getDirection();
        var right = facing.getClockWise();
        var standing = pilot.getDimensions(Pose.STANDING);
        double distance = ShipCockpitProfile.exitDistance(getBoundingBox(), standing);
        for (var direction : new net.minecraft.core.Direction[]{right, right.getOpposite(), facing.getOpposite(), facing}) {
            for (double drop : new double[]{0, .5, 1}) {
                Vec3 candidate = position().add(direction.getStepX() * distance, -drop, direction.getStepZ() * distance);
                if (clearExit(level, pilot, candidate)) return candidate;
            }
        }
        return null;
    }

    private @Nullable Vec3 findSideExit(ServerLevel level, ServerPlayer pilot) {
        double lateral = ShipSideAccess.exitSide(getYRot(), pilot.getDimensions(Pose.STANDING).width());
        int first = preferredExitSide != 0 ? preferredExitSide : 1;
        for (int side : new int[]{first, -first}) {
            for (double longitudinal : new double[]{0, -.45, .45}) {
                for (double drop : new double[]{0, .5, 1}) {
                    Vec3 stand = localToWorld(new Vec3(side * lateral, -drop, cockpit.seatLocal().z + longitudinal));
                    if (clearExit(level, pilot, stand)) return stand;
                }
            }
        }
        return null;
    }

    private boolean isCockpitSide(ServerPlayer pilot, Vec3 feet) {
        return routedCockpit() && ShipSideAccess.besideCockpit(worldToLocal(feet), getYRot(),
                pilot.getDimensions(Pose.STANDING).width(), cockpit.seatLocal());
    }

    private boolean canAimAtCockpitSide(ServerLevel level, ServerPlayer pilot, Vec3 feet) {
        Vec3 eye = pilot.getEyePosition();
        // LivingEntity#getViewVector reads animated head yaw, which can be stale for
        // the current native right-click. Use the server's latest player look rotation.
        var direction = rotateAccessVector(pilot.calculateViewVector(pilot.getXRot(), pilot.getYRot()), -getYRot());
        Vec3 hit = ShipSideAccess.aimPoint(worldToLocal(eye), direction, pilot.entityInteractionRange(),
                Math.signum(worldToLocal(feet).x), cockpit.seatLocal(), cockpit.height());
        if (hit == null) return false;
        return clearHullRay(level, pilot, eye, localToWorld(hit));
    }

    private static boolean clearHullRay(ServerLevel level, ServerPlayer pilot, Vec3 eye, Vec3 target) {
        return ShipSpace.loaded(level, new AABB(eye, target))
                && level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot))
                        .getType() == HitResult.Type.MISS;
    }

    private boolean clearExit(ServerLevel level, ServerPlayer pilot, Vec3 end) {
        if (routedCockpit()) return routeToStand(level, pilot, end, true) != null;
        return ShipCockpitProfile.outsideHull(getBoundingBox(), end, pilot.getDimensions(Pose.STANDING))
                && ShipSpace.clearExit(level, pilot, pilotAnchor(), end);
    }

    private boolean routedCockpit() { return cockpit == ShipCockpitRefit.CANDIDATE; }

    public boolean hasRefitCockpit() { return routedCockpit(); }

    List<AABB> onFootHull() {
        if (!position().equals(hullShapeOrigin) || getYRot() != hullShapeYaw) {
            hullShapeOrigin = position();
            hullShapeYaw = getYRot();
            hullShape = ShipHullCollision.boxes(hullShapeOrigin, hullShapeYaw);
        }
        return hullShape;
    }

    private @Nullable List<Vec3> routeToStand(ServerLevel level, ServerPlayer pilot, Vec3 outside, boolean exiting) {
        if (!supported(level) || !safeOutside(level, pilot, outside)) {
            if (!exiting) traceBoarding(pilot, "outside_stand_or_support");
            return null;
        }
        Vec3 local = worldToLocal(outside);
        double expectedReach = ShipCockpitProfile.exitDistance(getBoundingBox(), pilot.getDimensions(Pose.STANDING));
        if (local.y < -1.01 || local.y > .51 || local.horizontalDistance() > expectedReach + 2) {
            if (!exiting) traceBoarding(pilot, "stand_height_or_distance");
            return null;
        }
        var ingress = ShipCockpitRefit.waypoints(local);
        var path = exiting ? ingress.reversed() : ingress;
        if (clearRoute(level, pilot, path)) return List.copyOf(path);
        if (!exiting) traceBoarding(pilot, "access_leg_obstructed");
        return null;
    }

    private void traceBoarding(ServerPlayer pilot, String gate) {
        if (ShipMotionTrace.ENABLED && routedCockpit()) {
            ShipMotionTrace.log(this, "boarding_probe", "gate", gate, "standLocal", worldToLocal(pilot.position()),
                    "seatLocal", cockpit.seatLocal(), "yaw", getYRot());
        }
    }

    private boolean clearRoute(ServerLevel level, ServerPlayer pilot, List<Vec3> local) {
        return ShipHullCollision.checkOwnAccess(pilot, this,
                () -> ShipSpace.clearAccessLegs(level, pilot, local.stream().map(this::localToWorld).toList()));
    }

    private boolean safeOutside(ServerLevel level, ServerPlayer pilot, Vec3 outside) {
        if (isCockpitSide(pilot, outside)) {
            // The entire standing capsule is already beyond the exact measured beam plane.
            // At oblique yaw a strip can conservatively overhang its edge. Exclude only
            // this ship's approximation from the supported-stand check, not other blockers.
            return ShipHullCollision.checkOwnAccess(pilot, this, () -> ShipSpace.safeAccessStand(level, pilot, outside));
        }
        return ShipCockpitProfile.outsideHull(getBoundingBox(), outside, pilot.getDimensions(Pose.STANDING))
                && ShipSpace.safeAccessStand(level, pilot, outside);
    }

    private void publishTransit(AccessPhase phase, Vec3 outside, List<Vec3> path, boolean rollback) {
        setPhase(phase, outside);
        var access = accessSnapshot();
        var itinerary = new ShipAccessPath(path, access.startTick(), access.serial(), rollback);
        transit = new Transit(itinerary, outside, position(), getYRot());
        passedAccessTicks = 0;
        entityData.set(ACCESS_PATH, itinerary.encode());
        entityData.set(ACCESS_PROGRESS, 0);
    }

    private void clearTransit() {
        transit = null;
        passedAccessTicks = 0;
        entityData.set(ACCESS_PATH, new CompoundTag());
        entityData.set(ACCESS_PROGRESS, 0);
    }

    private void tickTransit(ServerLevel level, ServerPlayer pilot) {
        var plan = transit;
        var route = plan.path;
        var phase = accessPhase();
        boolean toSeat = (phase == AccessPhase.BOARDING) != route.rollback();
        boolean unchangedHull = position().equals(plan.hullOrigin) && getYRot() == plan.hullYaw;
        if (!unchangedHull || !supported(level)
                || !clearRoute(level, pilot, route.waypoints())
                || ((!route.rollback() || !toSeat) && !safeOutside(level, pilot, plan.outside))) {
            abortTransit(level, pilot, plan, unchangedHull);
            return;
        }
        int elapsed = (int)Math.clamp(level.getGameTime() - route.startTick(), 0, route.durationTicks());
        passedAccessTicks = elapsed;
        entityData.set(ACCESS_PROGRESS, elapsed);
        if (elapsed < route.durationTicks()) return;
        if (toSeat) {
            clearTransit();
            checkedExit = null;
            setPhase(AccessPhase.PILOTING, null);
        } else if (!detachAt(pilot, plan.outside)) {
            abortTransit(level, pilot, plan, true);
        }
    }

    private void abortTransit(ServerLevel level, ServerPlayer pilot, Transit plan, boolean unchangedHull) {
        feedback(pilot, "blocked");
        boolean boarding = accessPhase() == AccessPhase.BOARDING;
        var back = plan.path.reversePrefix(passedAccessTicks);
        if (unchangedHull && !plan.path.rollback() && supported(level)
                && (!boarding || safeOutside(level, pilot, plan.outside))
                && clearRoute(level, pilot, back)) {
            publishTransit(accessPhase(), plan.outside, back, true);
            return;
        }
        // An obstruction can cut off both directions. This is forced recovery, not an ordinary
        // route completion: use a revalidated berth if there is one, otherwise keep the last feet.
        Vec3 safe = safeOutside(level, pilot, plan.outside) ? plan.outside : safeRecoveryPosition(pilot);
        Vec3 actual = pilot.position();
        clearTransit();
        checkedExit = null;
        if (!detachAt(pilot, safe == null ? actual : safe)) setPhase(AccessPhase.PARKED, null);
    }

    private boolean detachAt(ServerPlayer pilot, Vec3 feet) {
        dismountOverride = feet;
        try {
            pilot.stopRiding();
        } finally {
            dismountOverride = null;
        }
        return pilot.getVehicle() != this;
    }

    void releasePilot(ServerPlayer pilot) {
        if (transit != null && pilot.isAlive()) {
            Vec3 recovery = safeRecoveryPosition(pilot);
            detachAt(pilot, recovery != null ? recovery : pilot.position());
        } else pilot.stopRiding();
    }

    private void finishExit(ServerLevel level, ServerPlayer pilot) {
        Vec3 end = checkedExit;
        checkedExit = null;
        if (end == null || !supported(level) || !clearExit(level, pilot, end)) {
            feedback(pilot, "blocked");
            setPhase(AccessPhase.PILOTING, null);
            return;
        }
        dismountOverride = end;
        try {
            pilot.stopRiding();
        } finally {
            dismountOverride = null;
        }
        if (pilot.getVehicle() == this) {
            feedback(pilot, "blocked");
            setPhase(AccessPhase.PILOTING, null);
            return;
        }
        // ServerEntity's native passenger update now publishes the removal and player teleport.
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (dismountOverride != null) return dismountOverride;
        if (passenger instanceof ServerPlayer pilot && pilot.isAlive()) {
            Vec3 recovery = safeRecoveryPosition(pilot);
            if (recovery != null) return recovery;
        }
        // Forced lifecycle detach is never vetoed: no teleport into unloaded space or permanent fall immunity.
        return passenger.position();
    }

    /** Save/forced-dismount fallback; never changes an ordinary exit or asks for a chunk. */
    public Vec3 safeRecoveryPosition(ServerPlayer pilot) {
        if (!(level() instanceof ServerLevel level) || pilot.level() != level) return null;
        if (transit != null && safeOutside(level, pilot, transit.outside)) return transit.outside;
        if (supported(level)) {
            Vec3 nearby = findExit(level, pilot);
            if (nearby != null) return nearby;
        }
        Vec3 berth = recoveryBerth;
        if (berth == null || Math.abs(berth.y - getY()) > 64
                || new Vec3(berth.x - getX(), 0, berth.z - getZ()).horizontalDistanceSqr() > 24 * 24) return null;
        if (routedCockpit()) return safeOutside(level, pilot, berth) ? berth : null;
        return ShipCockpitProfile.outsideHull(getBoundingBox(), berth, pilot.getDimensions(Pose.STANDING))
                && ShipSpace.clearExit(level, pilot, berth, berth) ? berth : null;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            input = new ShipFlight.Input();
            clearTransit();
            preferredExitSide = 0;
            wasAirborne = false;
            checkedExit = null;
            setDeltaMovement(Vec3.ZERO);
            setEngineState(ShipEngineState.UNVERIFIED);
            setPhase(AccessPhase.PARKED, null);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!isRemoved() && getFirstPassenger() instanceof ServerPlayer pilot) {
            // Try the normal checked fallback before vanilla marks the hull removed.
            Vec3 seat = pilot.position();
            releasePilot(pilot);
            if (!pilot.isPassenger() && !pilot.position().equals(seat)) {
                var feet = pilot.position();
                pilot.connection.teleport(feet.x, feet.y, feet.z, pilot.getYRot(), pilot.getXRot());
            }
        }
        super.remove(reason);
    }

    private static InteractionResult feedback(ServerPlayer pilot, String key) {
        pilot.displayClientMessage(Component.translatable("message.racmc.travel_ship." + key), true);
        return InteractionResult.FAIL;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!(attacker instanceof Player player) || !player.isCreative() || !player.getMainHandItem().isEmpty()) {
            return super.skipAttackInteraction(attacker);
        }
        if (player instanceof ServerPlayer pilot && level() instanceof ServerLevel level
                && pilot.isAlive() && !pilot.isSpectator() && !isRemoved()
                && pilot.canInteractWithEntity(this, 0) && targetsHull(level, pilot)) {
            if (isVehicle() || transit != null || accessPhase() != AccessPhase.PARKED) {
                feedback(pilot, "remove_occupied");
            } else {
                discard();
            }
        }
        // Consume the Creative punch without attacking or removing anything client-side.
        return true;
    }

    private boolean targetsHull(ServerLevel level, ServerPlayer pilot) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 end = eye.add(pilot.calculateViewVector(pilot.getXRot(), pilot.getYRot())
                .scale(pilot.entityInteractionRange()));
        Vec3 nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (AABB part : routedCockpit() ? onFootHull() : List.of(getBoundingBox())) {
            Vec3 hit = part.contains(eye) ? eye : part.clip(eye, end).orElse(null);
            if (hit != null && eye.distanceToSqr(hit) < distance) {
                nearest = hit;
                distance = eye.distanceToSqr(hit);
            }
        }
        return nearest != null && clearHullRay(level, pilot, eye, nearest);
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean shouldShowName() { return true; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean canChangeDimensions(Level oldLevel, Level newLevel) { return false; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }
    @Override public void move(MoverType type, Vec3 velocity) {
        if (type == MoverType.SELF && !level().isClientSide) super.move(type, velocity);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        // Identity/position/yaw are native chunk state; do not continue saved momentum or access.
        setDeltaMovement(Vec3.ZERO);
        input = new ShipFlight.Input();
        wasAirborne = false;
        recoveryBerth = null;
        clearTransit();
        preferredExitSide = 0;
        entityData.set(SESSION, 0);
        setEngineState(ShipEngineState.UNVERIFIED);
        setPhase(AccessPhase.PARKED, null);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yaw, 0, steps);
        } else {
            var target = new Vec3(x, y, z);
            boolean rebase = trackingInterpolation.accept(position(), target, yaw);
            if (ShipMotionTrace.ENABLED) ShipMotionTrace.log(this, "vanilla_tracker_target", "render_pos", position(),
                    "target", target, "tracked_velocity", getDeltaMovement(), "vanilla_steps", steps, "rebase", rebase);
        }
    }

    @Override public double lerpTargetX() { return trackingInterpolation.target(position()).x; }
    @Override public double lerpTargetY() { return trackingInterpolation.target(position()).y; }
    @Override public double lerpTargetZ() { return trackingInterpolation.target(position()).z; }
    @Override public float lerpTargetYRot() { return trackingInterpolation.targetYaw(getYRot()); }

    boolean takeMotionViewSample(long tick) {
        if (lastMotionViewTick == tick) return false;
        lastMotionViewTick = tick;
        return true;
    }
}
