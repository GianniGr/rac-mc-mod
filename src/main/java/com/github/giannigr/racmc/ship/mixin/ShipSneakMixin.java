package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.ship.Rac1TravelShip;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class ShipSneakMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void racmc$shipUsesCheckedExit(CallbackInfoReturnable<Boolean> callback) {
        if (((Player) (Object) this).getVehicle() instanceof Rac1TravelShip) callback.setReturnValue(false);
    }
}
