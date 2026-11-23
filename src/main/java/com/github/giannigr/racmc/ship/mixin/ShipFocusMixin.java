package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.ship.ShipPilotInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ShipFocusMixin {
    @Inject(method = "setWindowActive", at = @At("HEAD"))
    private void racmc$interruptEdgesOnBlur(boolean active, CallbackInfo callback) {
        if (!active) ShipPilotInput.interrupt();
    }
}
