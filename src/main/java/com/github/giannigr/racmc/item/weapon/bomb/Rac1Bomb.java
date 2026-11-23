package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.item.weapon.RangedCrateHits;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/** Class-121 gameplay adapter. This entity never creates a vanilla Explosion. */
public final class Rac1Bomb extends Projectile {
    public enum Phase { FLIGHT, SINKING, BLAST, DEEP_BURST }

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(
            Rac1Bomb.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> WAVE_STEP = SynchedEntityData.defineId(
            Rac1Bomb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> COMMITTED_THROW = SynchedEntityData.defineId(
            Rac1Bomb.class, EntityDataSerializers.COMPOUND_TAG);
    private static final float DAMAGE = 3;
    private final Set<UUID> hit = new HashSet<>();
    private final Set<BlockPos> hitBlocks = new HashSet<>();
    private int flightTicks;
    private int waterTicks;
    private boolean expiredOnLoad;
    private BombGloveTracking.Commit committedThrow;

    public Rac1Bomb(EntityType<? extends Rac1Bomb> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) Phase.FLIGHT.ordinal());
        builder.define(WAVE_STEP, 0);
        builder.define(COMMITTED_THROW, new CompoundTag());
    }

    public @Nullable BombGloveTracking.Commit committedThrow() { return committedThrow; }

    void commitThrow(BombGloveTracking.Commit identity) {
        if (committedThrow == null) entityData.set(COMMITTED_THROW, identity.encode());
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (COMMITTED_THROW.equals(key)) {
            committedThrow = BombGloveTracking.Commit.decode(entityData.get(COMMITTED_THROW));
            if (committedThrow != null && level().isClientSide())
                BombGloveTrackingNetwork.observeClientFlight(this);
        }
    }

    public Phase phase() { return Phase.values()[entityData.get(PHASE)]; }
    public int blastStep() { return entityData.get(WAVE_STEP); }
    /** Read-only presentation fact, including a shallow-water finish that has BLAST phase. */
    public boolean enteredWater() { return waterTicks > 0; }
    public double blastRadius() {
        return phase() == Phase.BLAST ? BombBallistics.radius(blastStep()) : 0;
    }

    private void phase(Phase next) { entityData.set(PHASE, (byte) next.ordinal()); }

    @Override public boolean isPushedByFluid() { return false; }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        if (expiredOnLoad || phase() == Phase.DEEP_BURST) {
            discard();
        } else if (phase() == Phase.BLAST) {
            if (blastStep() == BombBallistics.WAVE_STEPS) discard();
            else {
                entityData.set(WAVE_STEP, blastStep() + 1);
                wave(level);
            }
        } else {
            fly(level);
        }
    }

    private void fly(ServerLevel level) {
        Vec3 from = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 to = from.add(velocity);
        if (!loaded(level, new AABB(from, to).inflate(.25))) {
            discard(); // never force a chunk or manufacture a blast at an unknown boundary
            return;
        }
        boolean sinking = phase() == Phase.SINKING;
        if (sinking) waterTicks++;
        BlockHitResult solid = clip(level, from, to, ClipContext.Fluid.NONE);
        BlockHitResult surface = !sinking && velocity.y < 0
                ? clip(level, from, to, ClipContext.Fluid.WATER) : solid;
        boolean entersWater = !sinking && velocity.y < 0 && surface.getType() != HitResult.Type.MISS
                && level.getFluidState(surface.getBlockPos()).is(FluidTags.WATER)
                && (solid.getType() == HitResult.Type.MISS
                        || from.distanceToSqr(surface.getLocation()) < from.distanceToSqr(solid.getLocation()));
        Vec3 stop = entersWater ? surface.getLocation()
                : solid.getType() == HitResult.Type.MISS ? to : solid.getLocation();
        var target = ProjectileUtil.getEntityHitResult(level, this, from, stop,
                new AABB(from, stop).inflate(1), this::directCandidate, .12F);
        if (target != null && !EventHooks.onProjectileImpact(this, target)) {
            beginBurst(level, target.getLocation(), target.getEntity(), null);
        } else if (entersWater) {
            sink(surface.getLocation());
        } else if (solid.getType() != HitResult.Type.MISS) {
            if (EventHooks.onProjectileImpact(this, solid)) {
                discard(); // honoring a solid veto must not tunnel the projectile through it
                return;
            }
            var normal = solid.getDirection().getNormal();
            beginBurst(level, solid.getLocation().add(.02 * normal.getX(), .02 * normal.getY(),
                    .02 * normal.getZ()), null, solid.getBlockPos());
        } else if (!sinking && level.getFluidState(BlockPos.containing(from)).is(FluidTags.WATER)) {
            sink(from); // release inside water has no surface crossing
        } else {
            setPos(to);
            setDeltaMovement(velocity.add(0, sinking ? -BombBallistics.WATER_GRAVITY : -BombBallistics.GRAVITY, 0));
        }
        if (++flightTicks >= BombBallistics.FLIGHT_TICKS && !isRemoved()
                && (phase() == Phase.FLIGHT || phase() == Phase.SINKING)) {
            beginBurst(level, position(), null, null);
        }
    }

    private void sink(Vec3 point) {
        phase(Phase.SINKING);
        waterTicks = 1;
        setPos(point.add(0, -.015, 0));
        setDeltaMovement(BombBallistics.SINK);
        BombGloveActionEvent.emit(BombGloveActionEvent.Kind.WATER_ENTRY, this);
    }

    private void beginBurst(ServerLevel level, Vec3 at, Entity direct, BlockPos contactBlock) {
        if (isRemoved() || (phase() != Phase.FLIGHT && phase() != Phase.SINKING)) return;
        boolean deep = waterTicks >= BombBallistics.DEEP_WATER_TICKS;
        phase(deep ? Phase.DEEP_BURST : Phase.BLAST); // seal terminal transition before any callbacks
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        if (deep) {
            BombGloveActionEvent.emit(BombGloveActionEvent.Kind.DEEP_BURST, this);
            return;
        }
        if (direct instanceof FallingBlockEntity crate) airborne(crate);
        else if (direct instanceof LivingEntity living) damage(living, true);
        if (contactBlock != null) grounded(contactBlock);
        wave(level);
        BombGloveActionEvent.emit(BombGloveActionEvent.Kind.BLAST, this);
    }

    private boolean directCandidate(Entity entity) {
        if (entity instanceof FallingBlockEntity crate) return RangedCrateHits.eligible(crate.getBlockState());
        return entity instanceof LivingEntity living && super.canHitEntity(entity) && mayDamage(living);
    }

    /** Only the player bound to an admitted and paid throw may arm a secondary fuse. */
    public @Nullable ServerPlayer acceptedOwner() {
        if (phase()!=Phase.BLAST || committedThrow==null || !(getOwner() instanceof ServerPlayer player)
                || !player.isAlive() || player.isRemoved() || player.isSpectator() || player.level()!=level()
                || !player.getUUID().equals(committedThrow.key().owner())
                || !level().dimension().location().equals(committedThrow.key().dimension())) return null;
        return player;
    }

    boolean mayDamage(LivingEntity target) {
        Entity owner = getOwner();
        if (!target.isAlive() || target.isSpectator() || target == owner) return false;
        if (target instanceof Player other) {
            return owner instanceof ServerPlayer thrower && thrower.isAlive() && !thrower.isRemoved()
                    && !thrower.isSpectator() && thrower.level() == level()
                    && thrower.getServer().isPvpAllowed() && thrower.canHarmPlayer(other);
        }
        return owner == null || (!owner.isAlliedTo(target) && !target.isAlliedTo(owner));
    }

    private void damage(LivingEntity target, boolean direct) {
        if (!mayDamage(target) || !hit.add(target.getUUID())) return;
        var owner = getOwner();
        var source = direct ? damageSources().thrown(this, owner) : damageSources().explosion(this, owner);
        if (target.hurt(source, DAMAGE)) {
            Vec3 horizontal = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            target.push(horizontal.x * .25, .25, horizontal.z * .25);
            target.hurtMarked = true;
        }
    }

    private void wave(ServerLevel level) {
        double radius = blastRadius();
        var area = new AABB(position(), position()).inflate(radius);
        if (!loaded(level, area)) return;
        var targets = level.getEntities(this, area, e -> e instanceof LivingEntity || e instanceof FallingBlockEntity);
        targets.sort(Comparator.comparingInt(Entity::getId));
        for (Entity entity : targets) {
            if (isRemoved()) return;
            Vec3 point = nearest(entity.getBoundingBox());
            if (point.distanceToSqr(position()) > radius * radius
                    || hit.contains(entity.getUUID()) || !unoccluded(level, point, null)) continue;
            if (entity instanceof LivingEntity living) damage(living, false);
            else if (entity instanceof FallingBlockEntity crate) airborne(crate);
        }
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            if (isRemoved()) return;
            if (hitBlocks.contains(pos) || !RangedCrateHits.eligible(level.getBlockState(pos))) continue;
            Vec3 point = nearest(new AABB(pos));
            if (point.distanceToSqr(position()) <= radius * radius
                    && unoccluded(level, Vec3.atCenterOf(pos), pos)) grounded(pos.immutable());
        }
    }

    private void grounded(BlockPos pos) {
        if (RangedCrateHits.eligible(level().getBlockState(pos)) && hitBlocks.add(pos))
            RangedCrateHits.groundedBomb(this, pos);
    }

    private void airborne(FallingBlockEntity crate) {
        if (RangedCrateHits.eligible(crate.getBlockState()) && hit.add(crate.getUUID()))
            RangedCrateHits.airborneBomb(this, crate);
    }

    private Vec3 nearest(AABB box) {
        return new Vec3(Mth.clamp(getX(), box.minX, box.maxX), Mth.clamp(getY(), box.minY, box.maxY),
                Mth.clamp(getZ(), box.minZ, box.maxZ));
    }

    private boolean unoccluded(ServerLevel level, Vec3 point, BlockPos allow) {
        var trace = clip(level, position(), point, ClipContext.Fluid.NONE);
        return trace.getType() == HitResult.Type.MISS || trace.getBlockPos().equals(allow);
    }

    private BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, ClipContext.Fluid fluid) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid, this));
    }

    private static boolean loaded(ServerLevel level, AABB bounds) {
        var lo = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        var hi = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        return level.hasChunksAt(lo, hi) && !level.isOutsideBuildHeight(lo) && !level.isOutsideBuildHeight(hi)
                && level.getWorldBorder().isWithinBounds(lo) && level.getWorldBorder().isWithinBounds(hi);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightTicks", flightTicks);
        tag.putInt("WaterTicks", waterTicks);
        tag.putByte("BombPhase", expiredOnLoad ? (byte) Phase.DEEP_BURST.ordinal() : entityData.get(PHASE));
        if (committedThrow != null) tag.put("CommittedThrow", committedThrow.encode());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = Mth.clamp(tag.getInt("FlightTicks"), 0, BombBallistics.FLIGHT_TICKS);
        waterTicks = Mth.clamp(tag.getInt("WaterTicks"), 0, BombBallistics.FLIGHT_TICKS);
        int savedPhase = tag.getByte("BombPhase");
        expiredOnLoad = savedPhase < 0 || savedPhase > Phase.SINKING.ordinal()
                || flightTicks >= BombBallistics.FLIGHT_TICKS;
        if (!expiredOnLoad) {
            entityData.set(PHASE, (byte) savedPhase);
            var identity = BombGloveTracking.Commit.decode(tag.getCompound("CommittedThrow"));
            if (identity != null) commitThrow(identity);
        }
    }
}
