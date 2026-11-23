package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.shop.Rac1PdaServer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Standalone active handheld. No equipment/Belt dependency or acquisition hooks. */
public final class Rac1PdaItem extends Item {
    public Rac1PdaItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        lines.add(Component.translatable("item.racmc.pda.visual_hint"));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if (player.level()!=level || !stack.is(this) || stack.getCount()!=1
                || !player.isAlive() || player.isRemoved() || player.isSpectator())
            return InteractionResultHolder.fail(stack);
        if (!level.isClientSide() && (!(player instanceof ServerPlayer server)
                || !Rac1PdaServer.get(server.server).open(server,hand)))
            return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
    }
}
