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
        return delivering == player;
    }

    public static void normalAttackAccepted(ServerPlayer player) {
        SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player))
                .state.normalAttackAccepted(player.serverLevel().getGameTime());
    }

    static void intent(ServerPlayer player, long serial, WrenchActionState.Intent intent) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player));
        long tick = player.serverLevel().getGameTime();
        // Bound packet work before any raycast/sampling; no client coordinates or targets accepted.
        if (session.intentTick == tick) return;
        session.intentTick = tick;
        if (sampler == null || !sampler.ready(player)
                || player.getAttackStrengthScale(0) < 0.999F
                || player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) return;
        Sample initial = sampler.sample(player, session.state.presentation(), 0);
        if (initial == null) return;
        double clearance = groundClearance(player);
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
            session.previous = initial;
            session.wasBusy = true;
            player.resetAttackStrengthTicker();
            broadcast(player, session);
        }
    }

    private static boolean valid(ServerPlayer player) {
        return !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
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
                Sample sample = sampler.sample(player, session.state.presentation(), ++session.age);
                if (sample == null) {
                    session.state.cancel();
                } else {
                    session.state.tick(tick, true, player.onGround(), sample.hand, sample.frame,
                            (from, to) -> {
                                HitResult hit = player.level().clip(new ClipContext(vec(from),
                                        vec(to), ClipContext.Block.COLLIDER,
                                        ClipContext.Fluid.NONE, player));
                                return hit.getType() == HitResult.Type.MISS ? null
                                        : vector(hit.getLocation());
                            });
                    if (!player.onGround() && session.state.presentation().phase()
                            == WrenchActionPresentation.Phase.SLAM_DESCENT) {
                        Vec3 velocity = player.getDeltaMovement();
                        player.setDeltaMovement(velocity.x,
                                WrenchSlamMotion.descend(velocity.y), velocity.z);
                        player.hurtMarked = true;
                    }
                    // Only calibrated local contact volumes; throw has no damage/block callbacks.
                    var phase = session.state.presentation().phase();
                    if ((phase == WrenchActionPresentation.Phase.SLAM_WINDUP
                            || phase == WrenchActionPresentation.Phase.SLAM_DESCENT)
                            && (sample.active || sample.descendingBody)) {
                        WrenchSlamContacts.deliver(player, session.state, session.previous, sample);
                    }
                    session.previous = sample;
                }
            }
        }
        if (busy(player) || session.wasBusy || tick % 20 == 0) broadcast(player, session);
        session.wasBusy = busy(player);
    }

    private static void broadcast(ServerPlayer player, Session session) {
        WrenchActionNetwork.broadcast(player, sampler != null && sampler.ready(player),
                session.state.presentation());
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        // Forged vanilla attack packets cannot add normal/critical damage during a managed action.
        if (busy(event.getEntity()) && delivering != event.getEntity()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mine(PlayerInteractEvent.LeftClickBlock event) {
        if (busy(event.getEntity())) {
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

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        sampler = null;
        delivering = null;
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
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.state.cancel();
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
        Sample sample(ServerPlayer player, WrenchActionPresentation action, int elapsedTicks);
    }

    /** World-space anchors already projected onto the actual rigid Steve skeleton/socket. */
    public record Sample(WrenchActionState.ClipFrame frame, ActionVector hand, ActionVector head,
            ActionVector bodyContact, boolean active, boolean descendingBody) {}

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

        private Session(ServerPlayer player) {
            stack = player.getMainHandItem();
            dimension = player.level().dimension().location();
        }
    }
}
