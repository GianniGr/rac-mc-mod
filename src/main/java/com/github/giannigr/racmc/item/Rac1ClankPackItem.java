package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.clank.Rac1ClankServer;
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

/** Creative-only session setup, not a survival acquisition or equipment-slot policy. */
public final class Rac1ClankPackItem extends Item {
    private final Module module;
    public Rac1ClankPackItem(Properties properties,Module module) {
        super(properties.stacksTo(1));
        if(module!=Module.HELI&&module!=Module.THRUSTER)throw new IllegalArgumentException("Not a wearable pack");
        this.module=module;
    }
    public Module module(){return module;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(player.level()!=level||!stack.is(this)||!player.isCreative()||player.isSpectator()
                ||!player.isAlive()||player.isRemoved())return InteractionResultHolder.fail(stack);
        if(!level.isClientSide()&&(!(player instanceof ServerPlayer server)
                ||!Rac1ClankServer.equipCreative(server,hand)))return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        lines.add(Component.translatable("item.racmc.clank_pack.creative_hint"));
    }
}
