package com.github.giannigr.racmc.loot.mixin;

import com.github.giannigr.racmc.loot.BlasterAmmoPickup;
import com.github.giannigr.racmc.loot.BombGloveAmmoPickup;
import com.github.giannigr.racmc.sound.Rac1AudioEvent;
import com.github.giannigr.racmc.sound.Rac1AudioSignal;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
abstract class BlasterAmmoPickupMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean racmc$observeAmmoInsertion(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        if (!(inventory.player instanceof ServerPlayer collector)) return original.call(inventory, stack);
        var pickup = (ItemEntity) (Object) this;
        if (BombGloveAmmoPickup.matches(stack)) {
            return BombGloveAmmoPickup.observe(inventory, stack, () -> original.call(inventory, stack),
                    added -> Rac1AudioSignal.emitTo(collector, pickup.position(), Rac1AudioEvent.BOMB_GLOVE_AMMO_PICKUP));
        }
        return BlasterAmmoPickup.observe(inventory, stack, () -> original.call(inventory, stack),
                added -> Rac1AudioSignal.emitTo(collector, pickup.position(), Rac1AudioEvent.BLASTER_AMMO_PICKUP));
    }
}
