package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.item.Rac1BlasterAnimation;
import com.github.giannigr.racmc.item.RacMcItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
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
     * @param entity the entity
     * @return {@code true} when the entity fires the weapon
     */
    public static boolean isFiring(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isUsingItem()) {
            return false;
        }
        InteractionHand hand = player.getUsedItemHand();
        return player.getItemInHand(hand).is(RacMcItems.BLASTER.get());
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
