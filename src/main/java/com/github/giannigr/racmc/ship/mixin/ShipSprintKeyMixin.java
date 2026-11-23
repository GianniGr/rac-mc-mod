package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.ship.ShipPilotInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ToggleKeyMapping.class)
abstract class ShipSprintKeyMixin {
    @Inject(method = "setDown", at = @At("HEAD"), cancellable = true)
    private void racmc$ignoreSprintPressAtShipSeat(boolean pressed, CallbackInfo callback) {
        // Release events and vanilla resets still flow; keep a preexisting toggle latch intact.
        if (pressed && ShipPilotInput.blocksSprint((KeyMapping) (Object) this)) callback.cancel();
    }

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void racmc$hideSprintAtShipSeat(CallbackInfoReturnable<Boolean> callback) {
        if (ShipPilotInput.blocksSprint((KeyMapping) (Object) this)) callback.setReturnValue(false);
    }
}
