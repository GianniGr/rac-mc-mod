package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.RacMcConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server integration boundary. A lawful, calibrated common sampler must be installed explicitly;
 * absent data disables actions rather than substituting guessed original trajectories.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class WrenchActions {
    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "omniwrench");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static Sampler sampler;
    private static ServerPlayer delivering;
    private static Harvest harvest;

    private WrenchActions() {}

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Install during server setup, not a render call. Null disables and reconciles all sessions.
     * Sampler owns clip clocks/retarget calibration, never damage or inventory.
     */
    public static void installSampler(Sampler value) {
        sampler = value;
        SESSIONS.values().forEach(session -> session.state.cancel());
    }

    public static boolean busy(Player player) {
        if (player.level().isClientSide) return false;
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.state.presentation().phase()
                != WrenchActionPresentation.Phase.IDLE;
    }

    public static boolean normalAttackAllowed(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.state.canStartAt(player.serverLevel().getGameTime());
    }

    /** For P27's hook: allow protection dispatch but do not start a second normal attack here. */
    public static boolean deliveringContact(Player player) {
        return player != null && delivering == player;
    }

    public static void normalAttackAccepted(ServerPlayer player) {
        SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player))
                .state.normalAttackAccepted(player.serverLevel().getGameTime());
    }

    static void intent(ServerPlayer player, long serial, WrenchActionState.Intent intent, boolean slim) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player));
        long tick = player.serverLevel().getGameTime();
        // Bound packet work before any raycast/sampling; no client coordinates or targets accepted.
        if (session.intentTick == tick) return;
        session.intentTick = tick;
        if (!session.state.freshIntent(serial) || !session.state.canStartAt(tick)
                || !isWrench(player.getMainHandItem()) || !player.isAlive() || !valid(player)
                || (intent == WrenchActionState.Intent.THROW) != player.isShiftKeyDown()) return;
        if (sampler == null || !sampler.ready(player)
                || player.getAttackStrengthScale(0) < 0.999F
                || player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) return;
        double clearance = groundClearance(player);
        if (intent == WrenchActionState.Intent.SLAM && (session.airTicks < 4
                || clearance < (player.getDeltaMovement().y < 0 ? 0.57 : 0.1))) return;
        Sample initial = sampler.sample(player, WrenchActionPresentation.idle(), 0, slim);
        if (initial == null) return;
        boolean valid = valid(player);
        boolean accepted = session.state.start(serial, intent, tick,
                new WrenchActionState.Eligibility(isWrench(player.getMainHandItem()),
                        player.isAlive(), valid, player.isShiftKeyDown(), session.airTicks,
                        clearance, player.getDeltaMovement().y < 0, true),
                initial.hand, vector(player.getLookAngle()));
        if (accepted) {
            session.slot = player.getInventory().selected;
            session.stack = player.getMainHandItem();
            session.dimension = player.level().dimension().location();
            session.age = 0;
            session.audit = new WrenchHitAudit();
            session.actionSerial = session.state.presentation().serial();
            session.actionType = intent;
            session.previous = initial;
            session.slim = slim;
            session.wasBusy = true;
            player.resetAttackStrengthTicker();
            broadcast(player, session);
        }
    }

    static boolean valid(ServerPlayer player) {
        return !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
                && !player.isUsingItem()
                && (player.getPose() == net.minecraft.world.entity.Pose.STANDING
                        || player.getPose() == net.minecraft.world.entity.Pose.CROUCHING)
                && !player.onClimbable()
                && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInLava()
                && !player.gameMode.getGameModeForPlayer().isBlockPlacingRestricted();
    }

    public static double groundClearance(Player player) {
        Vec3 from = player.position().add(0, 0.01, 0);
        HitResult result = player.level().clip(new ClipContext(from, from.add(0, -4, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.MISS ? 4
                : Math.max(0, from.y - result.getLocation().y);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player));
        session.airTicks = player.onGround() ? 0 : Math.min(100, session.airTicks + 1);
        long tick = player.serverLevel().getGameTime();
        if (sampler == null || !sampler.ready(player)) {
            session.state.cancel();
        } else if (busy(player)) {
            boolean validOwner = valid(player) && player.isAlive()
                    && isWrench(player.getMainHandItem())
                    && session.slot == player.getInventory().selected
                    && session.stack == player.getMainHandItem()
                    && session.dimension.equals(player.level().dimension().location());
            if (!validOwner) {
                session.state.cancel();
            } else {
                Sample sample = sampler.sample(player, session.state.presentation(), ++session.age,
                        session.slim);
                if (sample == null) {
                    session.state.cancel();
                } else {
                    session.state.tickSources(tick, true, sample.sources.stream()
                            .map(p -> new WrenchActionState.SourceFrame(p.frame(), p.hand(),
                                    p.rotation(), p.grounded())).toList(),
                            new WrenchActionState.Surface() {
                                @Override
                                public ActionVector firstContact(ActionVector from, ActionVector to) {
                                    if (!loaded(player, from) || !loaded(player, to)) return from;
                                    return WrenchThrowContacts.surface(player, from, to);
                                }

                                @Override
                                public double groundHeight(ActionVector pos) {
                                    if (!loaded(player, pos)) return Double.NaN;
                                    HitResult hit = player.level().clip(new ClipContext(
                                            vec(pos).add(0, 0.5, 0),
                                            new Vec3(pos.x(), player.level().getMinBuildHeight()
                                                    + 0.01, pos.z()), ClipContext.Block.COLLIDER,
                                            ClipContext.Fluid.NONE, player));
                                    return hit.getType() == HitResult.Type.MISS ? Double.NaN
                                            : hit.getLocation().y;
                                }
                            });
                    var movementPhase = session.state.presentation().phase();
                    if (!player.onGround() && (movementPhase
                            == WrenchActionPresentation.Phase.SLAM_DESCENT
                            || movementPhase == WrenchActionPresentation.Phase.SLAM_WINDUP)) {
                        Vec3 velocity = player.getDeltaMovement();
                        player.setDeltaMovement(velocity.x,
                                WrenchSlamMotion.velocity(velocity.y, session.age,
                                        groundClearance(player)), velocity.z);
                        player.hurtMarked = true;
                    }
                    // Distinct calibrated slam and thrown contact paths, never a landing blast.
                    var phase = session.state.presentation().phase();
                    if ((phase == WrenchActionPresentation.Phase.SLAM_WINDUP
                            || phase == WrenchActionPresentation.Phase.SLAM_DESCENT
                            || phase == WrenchActionPresentation.Phase.RECOVERY)
                            && (sample.active || sample.descendingBody)) {
                        WrenchSlamContacts.deliver(player, session.state, session.previous, sample,
                                session.audit);
                    }
                    WrenchThrowContacts.deliver(player, session.state, sample, session.audit);
                    session.previous = sample;
                }
            }
        }
        if (busy(player) || session.wasBusy || tick % 20 == 0) broadcast(player, session);
        if (session.wasBusy && !busy(player)) {
            com.github.giannigr.racmc.RacMcMod.LOGGER.info("Wrench {} #{} for {}: {}",
                    session.actionType, session.actionSerial, player.getUUID(), session.audit);
        }
        session.wasBusy = busy(player);
    }

    private static void broadcast(ServerPlayer player, Session session) {
        WrenchActionNetwork.broadcast(player, sampler != null && sampler.ready(player),
                session.slim, session.state.presentation());
    }

    private static boolean loaded(ServerPlayer player, ActionVector point) {
        return player.level().hasChunkAt(net.minecraft.core.BlockPos.containing(
                point.x(), point.y(), point.z()));
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        // Forged vanilla attack packets cannot add normal/critical damage during a managed action.
        if (busy(event.getEntity()) && delivering != event.getEntity()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mine(PlayerInteractEvent.LeftClickBlock event) {
        if (busy(event.getEntity()) && delivering != event.getEntity()) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player
                    && player.level().hasChunkAt(event.getPos())) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
                        player.level(), event.getPos()));
            }
        }
    }

    static boolean attackAllowed(ServerPlayer player, net.minecraft.world.entity.Entity target) {
        ServerPlayer previous = delivering;
        delivering = player;
        try {
            return net.neoforged.neoforge.common.CommonHooks.onPlayerAttackTarget(player, target);
        } finally {
            delivering = previous;
        }
    }

    static boolean blockContactAllowed(ServerPlayer player, net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction face) {
        ServerPlayer previous = delivering;
        delivering = player;
        try {
            var event = net.neoforged.neoforge.common.CommonHooks.onLeftClickBlock(player, pos, face,
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            return !event.isCanceled()
                    && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE
                    && event.getUseItem() != net.neoforged.neoforge.common.util.TriState.FALSE;
        } finally {
            delivering = previous;
        }
    }

    static void harvestContact(ServerPlayer player, WrenchSlamBlocks.Candidate candidate) {
        harvestContact(player, candidate, null);
    }

    static void harvestContact(ServerPlayer player, WrenchSlamBlocks.Candidate candidate,
            ThrowReach ranged) {
        Harvest previous = harvest;
        harvest = new Harvest(player, candidate, player.getMainHandItem(), ranged);
        try {
            player.gameMode.destroyBlock(candidate.pos());
        } finally {
            harvest = previous;
        }
    }

    static void explosiveContact(ServerPlayer player, WrenchSlamBlocks.Candidate candidate,
            ThrowReach ranged) {
        explosiveContact(player, candidate, ranged, ranged == null
                ? com.github.giannigr.racmc.item.Rac1WrenchCrateHits.ContactKind.SLAM
                : com.github.giannigr.racmc.item.Rac1WrenchCrateHits.ContactKind.THROWN);
    }

    /** Direct hits use the same protection event, identity check and short-fuse adapter as actions. */
    public static void heldExplosiveContact(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        var level = player.serverLevel();
        if (busy(player) || !valid(player) || !player.isAlive() || !isWrench(player.getMainHandItem())
                || !normalAttackAllowed(player) || player.getAttackStrengthScale(0) < .999F
                || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !player.canInteractWithBlock(pos, 0)
                || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return;
        var hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return;
        var state = level.getBlockState(pos);
        if (com.github.giannigr.racmc.item.Rac1WrenchEvents.action(state)
                != com.github.giannigr.racmc.item.Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED)
            return;
        var lifecycle = level.getBlockEntity(pos);
        if (lifecycle == null) return;
        explosiveContact(player, new WrenchSlamBlocks.Candidate(pos, state, lifecycle), null,
                com.github.giannigr.racmc.item.Rac1WrenchCrateHits.ContactKind.HELD);
        normalAttackAccepted(player);
        player.resetAttackStrengthTicker();
    }

    private static void explosiveContact(ServerPlayer player, WrenchSlamBlocks.Candidate candidate,
            ThrowReach ranged, com.github.giannigr.racmc.item.Rac1WrenchCrateHits.ContactKind kind) {
        if (!com.github.giannigr.racmc.item.Rac1WrenchCrateHits.hasContactHandler()) return;
        Harvest previous = harvest;
        ServerPlayer priorDelivery = delivering;
        harvest = new Harvest(player, candidate, player.getMainHandItem(), ranged);
        delivering = player;
        try {
            var event = net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(player.serverLevel(),
                    player.gameMode.getGameModeForPlayer(), player, candidate.pos(), candidate.state());
            if (!event.isCanceled() && validHarvestLifecycle(player.level(), candidate.pos(), player)) {
                com.github.giannigr.racmc.item.Rac1WrenchCrateHits.contact(player, candidate.pos(),
                        candidate.state(), candidate.entity(), kind);
            }
        } finally {
            harvest = previous;
            delivering = priorDelivery;
        }
    }

    /** Rechecked by P27's removal seam AFTER native BreakEvent, including with no reward handler. */
    public static boolean validHarvestLifecycle(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, Player player) {
        Harvest context = harvest;
        if (context == null) return true; // Ordinary native mining is still P27-owned.
        var expected = context.candidate;
        return context.player == player && player.level() == level && expected.pos().equals(pos)
                && player.isAlive() && player.getMainHandItem() == context.held
                && isWrench(context.held) && valid(context.player)
                && withinBlockReach(context.player, pos, context.ranged)
                && context.player.serverLevel().mayInteract(context.player, pos)
                && level.getBlockState(pos) == expected.state()
                && level.getBlockEntity(pos) == expected.entity();
    }

    static boolean withinBlockReach(ServerPlayer player, net.minecraft.core.BlockPos pos,
            ThrowReach ranged) {
        if (ranged == null) return player.canInteractWithBlock(pos, 0);
        var center = vector(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        var owner = vector(player.position());
        var box = new WrenchContact.Box(new ActionVector(pos.getX(), pos.getY(), pos.getZ()),
                new ActionVector(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
        return center.subtract(owner).length() <= ranged.maximum + Math.sqrt(3) / 2
                && Double.isFinite(WrenchContact.sweptSphere(ranged.path, 0.4, box));
    }

    record ThrowReach(WrenchContact.Segment path, double maximum) {}
    private record Harvest(ServerPlayer player, WrenchSlamBlocks.Candidate candidate, ItemStack held,
            ThrowReach ranged) {}

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        if (sampler != null) sampler.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        installSampler(new WrenchOriginalSampler());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        sampler = null;
        delivering = null;
        harvest = null;
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        cancel(event.getEntity());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cancel(event.getEntity());
    }

    private static void cancel(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.state.cancel();
            if (sampler != null) sampler.forget(player.getUUID());
            if (player instanceof ServerPlayer server) broadcast(server, session);
        }
    }

    public static ActionVector vector(Vec3 vector) {
        return new ActionVector(vector.x, vector.y, vector.z);
    }

    public static Vec3 vec(ActionVector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    public interface Sampler {
        boolean ready(ServerPlayer player);
        /** Shared accepted action clock; never sample from a client's transmitted pose arrays. */
        Sample sample(ServerPlayer player, WrenchActionPresentation action, int elapsedTicks,
                boolean slim);
        default void forget(UUID player) {}
    }

    /** World-space anchors already projected onto the actual rigid Steve skeleton/socket. */
    public record Sample(WrenchActionState.ClipFrame frame, ActionVector hand, ActionVector head,
            ActionVector bodyContact, boolean active, boolean descendingBody,
            ActionRotation socketRotation, java.util.List<WrenchSlamSampling.Pose> sources) {}

    private static final class Session {
        private final WrenchActionState state = new WrenchActionState();
        private long intentTick = Long.MIN_VALUE;
        private int airTicks;
        private int age;
        private int slot;
        private ItemStack stack;
        private ResourceLocation dimension;
        private Sample previous;
        private boolean wasBusy;
        private boolean slim;
        private WrenchHitAudit audit = new WrenchHitAudit();
        private long actionSerial;
        private WrenchActionState.Intent actionType;

        private Session(ServerPlayer player) {
            stack = player.getMainHandItem();
            dimension = player.level().dimension().location();
        }
    }
}
