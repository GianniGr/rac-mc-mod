package com.github.giannigr.racmc.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Blaster of the game.
 *
 * <p>The weapon is fired while the trigger is held: the item is used when the trigger is pulled
 * and the shots follow in the ticks of the use of the item. The first shot leaves the weapon at
 * once, the shots of the burst follow every {@link Rac1FirePolicy#SHOT_INTERVAL_TICKS} ticks.
 *
 * <p>Everything that happens with a shot is decided on the server, in
 * {@link Rac1BlasterServer}. The client only draws the weapon and plays the animation of the
 * shot, so a shot is never seen twice and never happens without the server.
 */
public class BlasterItem extends Item {

    /**
     * The number of ticks the weapon may be used.
     *
     * <p>The number is the number of ticks of the bow of the game: it is long enough for every
     * burst of shots and it is never reached in a game.
     */
    public static final int USE_DURATION = 72000;

    /**
     * Creates the weapon.
     *
     * @param properties the properties of the weapon
     */
    public BlasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean fired = true;
        if (!level.isClientSide()) {
            // The first shot of the burst leaves the weapon at once. When the mouth of the weapon
            // is blocked, no shot is made and the weapon is not used at all, so that the shot is
            // not drawn either.
            fired = Rac1BlasterServer.fireFirstShot(level, player);
        }
        if (fired && !player.isUsingItem()) {
            // The weapon keeps being used, so that the shots of the burst follow. The state of
            // the use is sent to the other players by the game itself.
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        // The shots of the burst are fired by the server only.
        Rac1BlasterServer.fireWhileHeld(level, player);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        // The trigger was released: the burst is over and the next pull of the trigger fires at
        // once again.
        Rac1BlasterServer.resetBurst(player);
    }
}
