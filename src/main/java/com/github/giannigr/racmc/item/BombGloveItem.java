package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.item.weapon.bomb.BombGlovePose;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveThrows;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BombGloveItem extends Item {
    public BombGloveItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer && !BombGloveThrows.begin(serverPlayer, stack))
            return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return BombGlovePose.USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (user instanceof ServerPlayer player)
            BombGloveThrows.useTick(player, stack, BombGlovePose.USE_DURATION - remaining);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksLeft) {
        if (user instanceof ServerPlayer player) BombGloveThrows.cancel(player);
    }
}
