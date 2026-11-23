package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.effect.EffectSignal;
import com.github.giannigr.racmc.effect.EffectTriggeredEvent;
import com.github.giannigr.racmc.item.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Real ServerPlayer/item-use ticks, local packet sink only; not negotiated-client coverage. */
@GameTestHolder("racmc_ranged")
@PrefixGameTestTemplate(false)
public final class RangedOwnerGameTests {
    private RangedOwnerGameTests() {}

    private static ServerPlayer player(GameTestHelper h, UUID id) {
        var level = h.getLevel();
        var profile = new com.mojang.authlib.GameProfile(id, "owner-probe");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RacMcItems.BLASTER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RacMcItems.BLASTER_AMMO.get(), 8));
        var pos = h.absolutePos(new BlockPos(4, 4, 4));
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return player;
    }

    private static Object session(ServerPlayer player) {
        try {
            var field = RangedFireSessions.class.getDeclaredField("ACTIVE");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(null)).get(player.getUUID()); // read-only test inspection
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void killKeepingInventory(ServerPlayer player) {
        var rules = player.level().getGameRules();
        boolean before = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, player.server);
        try { player.kill(); } // actual death API, no fabricated health/use flags
        finally { rules.getRule(GameRules.RULE_KEEPINVENTORY).set(before, player.server); }
    }

    private static int[] observe(ServerPlayer player, Runnable action) {
        int[] counts = {0, 0};
        var proposals = new HashSet<UUID>();
        Consumer<EntityJoinLevelEvent> joins = event -> {
            if (event.getEntity() instanceof Rac1BlasterArrow arrow && arrow.getOwner() == player) {
                proposals.add(arrow.getUUID()); counts[0]++;
            }
        };
        Consumer<EffectTriggeredEvent> effects = event -> {
            if (event.signal().semantic() == EffectSignal.Semantic.BLASTER_SHOT
                    && proposals.contains(event.signal().lifecycle())) counts[1]++;
        };
        NeoForge.EVENT_BUS.addListener(joins);
        NeoForge.EVENT_BUS.addListener(effects);
        try { action.run(); return counts; }
        finally { NeoForge.EVENT_BUS.unregister(joins); NeoForge.EVENT_BUS.unregister(effects); }
    }

    private static void denied(GameTestHelper h, ServerPlayer player, Runnable action) {
        int ammo = BlasterAmmo.count(player);
        int[] result = observe(player, action);
        h.assertTrue(result[0] == 0 && result[1] == 0, "Invalid owner proposed a projectile/accepted effect");
        h.assertTrue(BlasterAmmo.count(player) == ammo, "Invalid owner spent or gained ammo");
        h.assertTrue(!player.isUsingItem() && !Rac1BlasterServer.isFiring(player), "Invalid owner retained held firing");
        h.assertTrue(player.getPersistentData().getLong("racmcBlasterLastShot") == Long.MIN_VALUE,
                "Invalid owner retained burst");
    }

    @GameTest(template = "empty", batch = "ranged_owner", timeoutTicks = 40)
    public static void heldItemTicksStopAtDeathAndOldOwnerCannotResurrectRespawnSession(GameTestHelper h) {
        var player = player(h, UUID.randomUUID());
        var level = h.getLevel();
        int[] initial = observe(player, () -> player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND));
        h.assertTrue(initial[0] == 1 && initial[1] == 1 && BlasterAmmo.count(player) == 7
                && player.isUsingItem(), "Genuine item use must admit/pay immediate first shot");
        Object firstSession = session(player);
        h.assertTrue(firstSession != null, "Accepted live shot must start session");
        h.runAtTickTime(1, () -> {
            int[] counts = observe(player, player::doTick);
            h.assertTrue(counts[0] == 0 && BlasterAmmo.count(player) == 7, "Held cadence fired at tick1");
        });
        h.runAtTickTime(2, () -> {
            int[] counts = observe(player, player::doTick);
            h.assertTrue(counts[0] == 1 && counts[1] == 1 && BlasterAmmo.count(player) == 6,
                    "Held cadence failed to admit/pay shot at tick2 BEFORE death");
            h.assertTrue(player.isUsingItem(), "Kill fixture requires genuinely active held use");
            killKeepingInventory(player);
            h.assertTrue(!player.isAlive() && BlasterAmmo.count(player) == 6, "Actual kill/keep-inventory fixture failed");
            h.assertTrue(!Rac1BlasterServer.isFiring(player), "Dead held use reported as firing before cleanup");
            denied(h, player, () -> {
                player.doTick(); // same tick as accepted live shot, but after actual kill
                Rac1BlasterServer.fireWhileHeld(level, player);
                h.assertTrue(!Rac1BlasterServer.fireFirstShot(level, player), "Dead direct first shot accepted");
            });
            h.assertTrue(session(player) == null, "Dead owner's audio session survived rejection");
        });
        for (int tick = 3; tick <= 8; tick++) h.runAtTickTime(tick, () -> {
            denied(h, player, () -> {
                player.doTick();
                Rac1BlasterServer.fireWhileHeld(level, player);
                h.assertTrue(!Rac1BlasterServer.fireFirstShot(level, player), "Dead retry resurrected firing");
            });
            h.assertTrue(BlasterAmmo.count(player) == 6 && session(player) == null,
                    "Post-death tick spent/refunded ammo or resurrected session");
        });
        h.runAtTickTime(9, () -> {
            // Replacement identity with same UUID, not a claim of a negotiated respawn.
            var replacement = player(h, player.getUUID());
            h.assertTrue(!Rac1BlasterServer.isFiring(replacement), "Replacement inherited held use");
            int[] counts = observe(replacement,
                    () -> replacement.getMainHandItem().use(level, replacement, InteractionHand.MAIN_HAND));
            h.assertTrue(counts[0] == 1 && counts[1] == 1 && BlasterAmmo.count(replacement) == 7,
                    "Fresh replacement trigger failed immediate paid shot");
            Object nextSession = session(replacement);
            h.assertTrue(nextSession != null && nextSession != firstSession, "Replacement reused dead session");
            denied(h, player, () -> Rac1BlasterServer.fireWhileHeld(level, player));
            RangedFireSessions.accepted(player); // stale accepted callback must not revive old object
            h.assertTrue(session(replacement) == nextSession, "Stale old-owner callback changed replacement session");
            replacement.stopUsingItem();
            RangedFireSessions.reject(replacement);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "ranged_owner_direct")
    public static void deadRemovedAndWrongWorldDenyDirectAndHeldAdmission(GameTestHelper h) {
        for (int kind = 0; kind < 5; kind++) {
            var player = player(h, UUID.randomUUID());
            player.startUsingItem(InteractionHand.MAIN_HAND);
            var suppliedLevel = h.getLevel();
            if (kind == 0) killKeepingInventory(player);
            if (kind == 1) player.discard();
            if (kind == 2) suppliedLevel = player.server.getLevel(net.minecraft.world.level.Level.NETHER);
            if (kind == 3) player.setGameMode(GameType.SPECTATOR);
            if (kind == 4) { player.setGameMode(GameType.CREATIVE); killKeepingInventory(player); }
            if (kind != 2) h.assertTrue(!Rac1BlasterServer.isFiring(player), "Invalid held owner reported firing");
            var wrongOrActual = suppliedLevel;
            denied(h, player, () -> {
                Rac1BlasterServer.fireWhileHeld(wrongOrActual, player);
                h.assertTrue(!Rac1BlasterServer.fireFirstShot(wrongOrActual, player), "Invalid direct owner accepted");
            });
            h.assertTrue(session(player) == null, "Invalid owner created audio session");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "ranged_owner_reentrant")
    public static void deathInsideEntityAdmissionRefundsOnlyItsActualReservationWithoutFeedback(GameTestHelper h) {
        var player = player(h, UUID.randomUUID());
        Rac1BlasterArrow[] proposed = {null};
        Consumer<EntityJoinLevelEvent> kill = event -> {
            if (event.getEntity() instanceof Rac1BlasterArrow arrow && arrow.getOwner() == player) {
                proposed[0] = arrow;
                killKeepingInventory(player);
            }
        };
        NeoForge.EVENT_BUS.addListener(kill);
        try {
            int[] counts = observe(player, () ->
                    h.assertTrue(!Rac1BlasterServer.fireFirstShot(h.getLevel(), player), "Reentrant dead-owner admission accepted"));
            h.assertTrue(counts[0] == 1 && counts[1] == 0 && proposed[0] != null && proposed[0].isRemoved(),
                    "Rejected proposed projectile survived or produced accepted feedback");
            h.assertTrue(BlasterAmmo.count(player) == 8 && session(player) == null,
                    "Actual one-arrow reservation was not refunded exactly once");
            denied(h, player, () -> Rac1BlasterServer.fireWhileHeld(h.getLevel(), player));
            h.assertTrue(BlasterAmmo.count(player) == 8, "Unreserved retry invented extra ammo");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(kill); }
    }

    @GameTest(template = "empty", batch = "ranged_owner_feedback")
    public static void deathInAlreadyAcceptedFeedbackKeepsOnePaidShotButCannotStartHeldUse(GameTestHelper h) {
        var player = player(h, UUID.randomUUID());
        Consumer<EffectTriggeredEvent> kill = event -> {
            if (event.signal().semantic() == EffectSignal.Semantic.BLASTER_SHOT
                    && h.getLevel().getEntity(event.signal().lifecycle()) instanceof Rac1BlasterArrow arrow
                    && arrow.getOwner() == player) killKeepingInventory(player);
        };
        NeoForge.EVENT_BUS.addListener(kill);
        try {
            int[] counts = observe(player, () ->
                    player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND));
            h.assertTrue(counts[0] == 1 && counts[1] == 1 && !player.isAlive(),
                    "Fixture must kill in feedback of exactly one already-accepted live shot");
            h.assertTrue(BlasterAmmo.count(player) == 7 && !player.isUsingItem() && session(player) == null,
                    "Already-accepted death shot was refunded or restarted held use/session");
            denied(h, player, () -> {
                Rac1BlasterServer.fireWhileHeld(h.getLevel(), player);
                h.assertTrue(!Rac1BlasterServer.fireFirstShot(h.getLevel(), player), "Feedback-dead owner fired again");
            });
            h.assertTrue(BlasterAmmo.count(player) == 7, "Post-acceptance death invented an ammo refund");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(kill); }
    }
}
