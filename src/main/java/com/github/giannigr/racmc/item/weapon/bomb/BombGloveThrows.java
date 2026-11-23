package com.github.giannigr.racmc.item.weapon.bomb;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.RacMcEntities;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.FireService;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-only discrete throw; never starts a Blaster held-fire session. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class BombGloveThrows {
    public static final int COOLDOWN_TICKS = 7;
    private static final double HALF_BOMB = .12;
    private static final Map<ServerPlayer, Windup> PENDING = new IdentityHashMap<>();

    private record Windup(ServerLevel level, ItemStack stack, int slot, BombGloveTracking.Key key) {
        boolean valid(ServerPlayer player) {
            return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                    && player.level() == level && player.getInventory().selected == slot
                    && player.getMainHandItem() == stack && player.getUseItem() == stack
                    && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    && !player.getCooldowns().isOnCooldown(stack.getItem());
        }
    }

    private BombGloveThrows() {}

    public static boolean begin(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isUsingItem()
                || stack != player.getMainHandItem() || !stack.is(RacMcItems.BOMB_GLOVE.get())
                || player.getCooldowns().isOnCooldown(stack.getItem())
                || (!player.getAbilities().instabuild && BombGloveAmmo.count(player) == 0)) return false;
        var windup = new Windup(player.serverLevel(), stack, player.getInventory().selected, null);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (!windup.valid(player)) return false;
        PENDING.put(player, new Windup(windup.level(), stack, windup.slot(), BombGloveTrackingServer.begin(player)));
        return true;
    }

    public static void useTick(ServerPlayer player, ItemStack stack, int elapsed) {
        var windup = PENDING.get(player);
        if (windup == null) return;
        if (windup.stack() != stack || !windup.valid(player)) {
            cancel(player);
            return;
        }
        if (elapsed < BombGlovePose.WINDUP_TICKS - 1) return;
        PENDING.remove(player); // consume this throw before any event/callback can reenter
        try {
            admit(player, stack, windup);
        } finally {
            BombGloveTrackingServer.abort(player, windup.key()); // only if not already released
        }
        // Native use stays active until the player lets go, preventing held-input repeats.
    }

    private static void admit(ServerPlayer player, ItemStack stack, Windup windup) {
        var level = windup.level();
        FireService.fire(() -> windup.valid(player) ? BombGloveAmmo.reserve(player) : null,
                new FireService.ProjectileFactory<Rac1Bomb>() {
                    @Override public Rac1Bomb prepare() {
                        Vec3 root = BombGlovePose.palmRoot(player);
                        Vec3 palm = BombGlovePose.palm(player);
                        if (!clearPalm(level, player, root, palm)) return null;
                        var bomb = new Rac1Bomb(RacMcEntities.BOMB.get(), level);
                        bomb.setOwner(player);
                        bomb.setPos(palm);
                        bomb.setDeltaMovement(BombBallistics.launch(player.getYRot(), player.getXRot()));
                        return bomb;
                    }
                    @Override public boolean admit(Rac1Bomb bomb) {
                        Vec3 velocity = bomb.getDeltaMovement();
                        return level.addFreshEntity(bomb) && !bomb.isRemoved() && windup.valid(player)
                                && bomb.getOwner() == player && bomb.getDeltaMovement().equals(velocity)
                                && bomb.position().equals(BombGlovePose.palm(player))
                                && clearPalm(level, player, BombGlovePose.palmRoot(player), bomb.position());
                    }
                    @Override public void discard(Rac1Bomb bomb) { bomb.discard(); }
                }, bomb -> {
                    player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
                    BombGloveTrackingServer.committed(player, windup.key(), bomb);
                    BombGloveActionEvent.emit(BombGloveActionEvent.Kind.RELEASE, bomb);
                });
    }

    static boolean clearPalm(ServerLevel level, ServerPlayer owner, Vec3 root, Vec3 palm) {
        var bounds = new AABB(root, palm).inflate(HALF_BOMB, 0, HALF_BOMB)
                .expandTowards(0, 2 * HALF_BOMB, 0);
        var lo = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        var hi = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        if (!level.hasChunksAt(lo, hi) || level.isOutsideBuildHeight(lo) || level.isOutsideBuildHeight(hi)
                || !level.getWorldBorder().isWithinBounds(lo) || !level.getWorldBorder().isWithinBounds(hi))
            return false;
        for (var shape : level.getBlockCollisions(owner, bounds)) {
            for (var box : shape.toAabbs()) {
                if (clipsPalm(box, root, palm)) return false;
            }
        }
        return true;
    }

    static boolean clipsPalm(AABB obstacle, Vec3 root, Vec3 palm) {
        // Expand by the bomb bounds (anchored at its feet), then sweep the anchor through it.
        var expanded = obstacle.inflate(HALF_BOMB, 0, HALF_BOMB).expandTowards(0, -2 * HALF_BOMB, 0);
        return expanded.contains(root) || expanded.contains(palm) || expanded.clip(root, palm).isPresent();
    }

    public static void cancel(ServerPlayer player) {
        var windup = PENDING.remove(player);
        if (windup != null) BombGloveTrackingServer.abort(player, windup.key());
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var pending = PENDING.get(player);
            if (pending != null && !pending.valid(player)) cancel(player);
            if (!PENDING.containsKey(player)) BombGloveTrackingServer.idle(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void changeLevel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer owner && event.getEntity() instanceof ServerPlayer watcher
                && watcher.level() == owner.level()) {
            var snapshot = BombGloveTrackingServer.snapshot(owner);
            if (snapshot != null) BombGloveTrackingNetwork.sendTo(watcher, snapshot);
        }
    }
    private static void forget(ServerPlayer player) {
        cancel(player);
        BombGloveTrackingServer.forget(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PENDING.clear();
        BombGloveTrackingServer.stopped();
    }
}
