package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.ship.ShipHullCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityGetter.class)
interface ShipPlayerHullCollisionMixin {
    @Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
    private void racmc$addRefitPlayerHull(Entity mover, AABB area, CallbackInfoReturnable<List<VoxelShape>> callback) {
        var current = callback.getReturnValue();
        var result = ShipHullCollision.appendPlayerShapes((EntityGetter)(Object)this, mover, area, current);
        if (result != current) callback.setReturnValue(result);
    }
}
