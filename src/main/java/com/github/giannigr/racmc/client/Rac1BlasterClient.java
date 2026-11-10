package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterAnimation;
import com.github.giannigr.racmc.item.Rac1BlasterGrip;
import com.github.giannigr.racmc.item.Rac1BlasterSprint;
import com.github.giannigr.racmc.item.RacMcItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * The state of the Blaster on the client.
 *
 * <p>The client does not fire the weapon: it only draws it. The state of the weapon of a player
 * is read out of the state of the use of the item, which the server sends to every client by
 * itself, so that the players around see the weapon fire as well.
 *
 * <p>The state of the use of the item is forgotten by the game when the player changes the slot,
 * when the player dies and when the player leaves and comes back, so the weapon stops firing in
 * every one of these cases without anything else having to be done.
 */
public final class Rac1BlasterClient {

    /** The animation of the weapon of every player that is in the world. */
    private static final Map<Integer, Rac1BlasterAnimation> ANIMATIONS = new HashMap<>();

    /** The player that is drawn at the moment, or {@code null} when none is drawn. */
    private static LivingEntity current;

    /** The trigger of the sprint of the player of the client. */
    private static final Rac1BlasterSprint.Trigger SPRINT = new Rac1BlasterSprint.Trigger();

    private Rac1BlasterClient() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Remembers the player that is drawn.
     *
     * @param event the event that is fired before a living entity is drawn
     */
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        current = event.getEntity();
    }

    /**
     * Forgets the player that was drawn.
     *
     * @param event the event that is fired after a living entity was drawn
     */
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        current = null;
    }

    /**
     * The player that is drawn at the moment.
     *
     * @return the player, or {@code null} when none is drawn
     */
    public static LivingEntity current() {
        return current;
    }

    /**
     * Whether a player is firing the weapon at the moment.
     *
     * <p>The weapon is fired with the main hand only. A player that fires the weapon uses it
     * with the main hand and holds the Blaster in the main hand, so the answer is the same on
     * every client and the pose of the arm and the animation of the shot agree with the shots
     * that are really made.
     *
     * @param entity the entity
     * @return {@code true} when the entity fires the weapon
     */
    public static boolean isFiring(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return Rac1BlasterArmPose.aimsWithMainHand(player.isUsingItem(),
                player.getUsedItemHand() == InteractionHand.MAIN_HAND,
                player.getMainHandItem().is(RacMcItems.BLASTER.get()));
    }

    /**
     * Lets the player of the client start to sprint while the weapon is fired.
     *
     * <p>The game of blocks refuses every player that uses an item the right to start to sprint,
     * and the Blaster is held with the trigger. The rules of the game of blocks are kept as they
     * are here, and only the use of the item is put aside: the food of the player, the water,
     * the collision, the crouch, the blindness, the passenger, the fall of the player and every
     * other item keep the rules of the game of blocks.
     *
     * <p>The sprint is started the same way the game of blocks starts it: with the key of the
     * sprint, or with the trigger of the sprint, which is armed while the player lets go of the
     * key of the forward.
     *
     * @param player the player of the client
     * @param input the input of the player
     */
    public static void sprint(LocalPlayer player, Input input) {
        SPRINT.tick();
        if (!isFiring(player)) {
            // The weapon is not fired: the game of blocks holds the trigger of the sprint itself.
            SPRINT.clear();
            return;
        }
        Rac1BlasterSprint.Conditions conditions = conditions(player, input);
        if (!Rac1BlasterSprint.mayStart(conditions)) {
            SPRINT.clear();
            return;
        }
        boolean start = false;
        if (Rac1BlasterSprint.enoughImpulse(conditions)) {
            start = conditions.sprintKey() || SPRINT.armed();
            SPRINT.clear();
        } else if (Rac1BlasterSprint.mayArm(conditions)) {
            SPRINT.arm();
        } else {
            // The game of blocks forgets the trigger of the sprint while the player holds the
            // key of the crouch.
            SPRINT.clear();
        }
        if (start) {
            player.setSprinting(true);
        }
    }

    /**
     * The conditions of the sprint of the player of the client.
     *
     * @param player the player of the client
     * @param input the input of the player
     * @return the conditions of the sprint of the player
     */
    private static Rac1BlasterSprint.Conditions conditions(LocalPlayer player, Input input) {
        boolean passenger = player.isPassenger();
        boolean underWater = player.isUnderWater();
        float impulse = underWater
                ? (input.hasForwardImpulse() ? 1.0f : 0.0f)
                : input.forwardImpulse;
        return new Rac1BlasterSprint.Conditions(
                passenger ? player.getVehicle().onGround() : player.onGround(),
                player.isInWater(), underWater || player.canStartSwimming(),
                input.shiftKeyDown, player.hasEffect(MobEffects.BLINDNESS), passenger,
                !passenger || player.getVehicle().canSprint()
                        && player.getVehicle().isControlledByLocalInstance(),
                player.isFallFlying(), player.isSpectator(),
                player.getFoodData().getFoodLevel(), player.mayFly(), impulse,
                Minecraft.getInstance().options.keySprint.isDown());
    }

    /**
     * Brings the state of the weapons of the players of the world up to date.
     *
     * <p>The state is looked at once a tick, so that the moment of the change is known and the
     * blend of the weapon can be measured from it.
     *
     * @param level the world of the client
     */
    public static void update(ClientLevel level) {
        int tick = (int) level.getGameTime();
        List<AbstractClientPlayer> players = level.players();
        for (AbstractClientPlayer player : players) {
            Rac1BlasterAnimation animation = ANIMATIONS.computeIfAbsent(player.getId(),
                    key -> new Rac1BlasterAnimation());
            animation.setFiring(isFiring(player), tick);
        }
        // The players that left the world are removed, so that their weapons are not drawn with
        // an old state.
        ANIMATIONS.keySet().removeIf(id -> {
            for (AbstractClientPlayer player : players) {
                if (player.getId() == id) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * The animation of the weapon of a player.
     *
     * @param entity the player
     * @return the animation, or {@code null} when the player is not known
     */
    public static Rac1BlasterAnimation animationOf(LivingEntity entity) {
        return entity == null ? null : ANIMATIONS.get(entity.getId());
    }

    /**
     * The place of the mouth of the weapon in the world, for a player.
     *
     * <p>The place is built out of the place of the player and out of the turn of the body of the
     * player, in the same way the server builds it when it fires a shot, so that the mouth of the
     * drawn weapon and the mouth of the weapon in the world are the same place of the world.
     *
     * @param player the player
     * @return the place of the mouth of the weapon in the world
     */
    public static Rac1BlasterGrip.Point muzzleOf(Player player) {
        Rac1BlasterGrip.Look look = new Rac1BlasterGrip.Look(
                Mth.wrapDegrees(player.getYRot() - player.yBodyRot), player.getXRot(),
                player.yBodyRot);
        return Rac1BlasterGrip.muzzle(player.getX(), player.getY(), player.getZ(), look);
    }

    /**
     * The share of the pose of the shot in the pose of the weapon of a player.
     *
     * @param entity the player
     * @param tick the tick of the client, with its partial tick
     * @return the share, from {@code 0} (the held weapon) to {@code 1} (the shot)
     */
    public static float weightOf(LivingEntity entity, float tick) {
        Rac1BlasterAnimation animation = animationOf(entity);
        return animation == null ? 0.0f : animation.weight(tick);
    }

    /**
     * The moment of the sequence of the shot of the weapon of a player.
     *
     * @param entity the player
     * @param tick the tick of the client, with its partial tick
     * @return the moment of the sequence in seconds
     */
    public static float fireTimeOf(LivingEntity entity, float tick) {
        Rac1BlasterAnimation animation = animationOf(entity);
        return animation == null ? 0.0f : animation.fireTime(tick);
    }
}
