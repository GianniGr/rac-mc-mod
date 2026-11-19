package com.github.giannigr.racmc.clank.mixin;

import com.github.giannigr.racmc.clank.Rac1ClankConnectionAccess;
import com.github.giannigr.racmc.clank.Rac1ClankRuntime;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ClankMovePacketMixin implements Rac1ClankConnectionAccess {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private boolean clientIsFloating;
    @Override public boolean racmc$clankAwaitingTeleport(){return awaitingPositionFromClient!=null;}

    // Vanilla tests coordinate NaN but clamps infinity. Strengthen only controlled Clank packets.
    @Inject(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at=@At(value="INVOKE",
                    target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;containsInvalidValues(DDDFF)Z"),
            cancellable=true,require=1)
    private void racmc$rejectInfiniteClankPosition(ServerboundMovePlayerPacket packet,CallbackInfo callback) {
        if(Rac1ClankRuntime.ownsTravel(player)&&(Double.isInfinite(packet.getX(0))
                ||Double.isInfinite(packet.getY(0))||Double.isInfinite(packet.getZ(0)))) {
            player.connection.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
            callback.cancel();
        }
    }

    // 1.21.1: this call follows ensureRunningOnSameThread AND the native invalid-value branch.
    @ModifyVariable(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;updateAwaitingTeleport()Z"),
            argsOnly=true,ordinal=0,require=1)
    private ServerboundMovePlayerPacket racmc$authoritativeClankPosition(ServerboundMovePlayerPacket packet) {
        return Rac1ClankRuntime.authoritativePacket(player,packet);
    }
    @Inject(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at=@At("RETURN"),require=1)
    private void racmc$validatedClankDescent(ServerboundMovePlayerPacket packet,CallbackInfo callback) {
        if(!Double.isFinite(packet.getX(0))||!Double.isFinite(packet.getY(0))
                ||!Double.isFinite(packet.getZ(0))||!Float.isFinite(packet.getYRot(0))
                ||!Float.isFinite(packet.getXRot(0)))return;
        if(Rac1ClankRuntime.floatingCredit(player))clientIsFloating=false;
    }
}
