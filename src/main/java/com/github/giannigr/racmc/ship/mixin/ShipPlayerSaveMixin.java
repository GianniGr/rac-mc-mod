package com.github.giannigr.racmc.ship.mixin;

import com.github.giannigr.racmc.ship.Rac1TravelShip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ShipPlayerSaveMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void racmc$saveShipOnlyAsAChunkEntity(CompoundTag tag, CallbackInfo callback) {
        ServerPlayer pilot = (ServerPlayer) (Object) this;
        if (!(pilot.getVehicle() instanceof Rac1TravelShip ship)) return;
        // Never hand a second copy to PlayerList's RootVehicle restore path.
        tag.remove("RootVehicle");
        var recovery = ship.safeRecoveryPosition(pilot);
        if (recovery == null) return;
        // Autosave/shutdown may run before disconnect. Save a validated dismounted player, not a seat claim.
        var feet = new ListTag();
        feet.add(DoubleTag.valueOf(recovery.x));
        feet.add(DoubleTag.valueOf(recovery.y));
        feet.add(DoubleTag.valueOf(recovery.z));
        tag.put("Pos", feet);
        tag.putFloat("FallDistance", 0);
    }
}
