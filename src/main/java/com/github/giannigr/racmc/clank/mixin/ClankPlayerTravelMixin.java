package com.github.giannigr.racmc.clank.mixin;

import com.github.giannigr.racmc.clank.Rac1ClankTravel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class ClankPlayerTravelMixin {
    @Inject(method="travel(Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true,require=1)
    private void racmc$clankTravel(Vec3 input,CallbackInfo callback) {
        if(Rac1ClankTravel.replace((Player)(Object)this))callback.cancel();
    }
}
