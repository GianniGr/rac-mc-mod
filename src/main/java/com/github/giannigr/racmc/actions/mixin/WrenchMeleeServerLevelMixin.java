package com.github.giannigr.racmc.actions.mixin;

import com.github.giannigr.racmc.actions.WrenchMeleeAudio;
import com.github.giannigr.racmc.sound.WrenchMeleeSignal;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class WrenchMeleeServerLevelMixin {
    private static final String RACMC_SEEDED = "playSeededSound(Lnet/minecraft/world/entity/player/Player;"
            + "DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V";

    @Inject(method = RACMC_SEEDED, at = @At("HEAD"), require = 1)
    private void racmc$captureOffer(Player except, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource category, float volume, float pitch, long seed, CallbackInfo callback,
            @Share("racmc$meleeOffer") LocalRef<WrenchMeleeAudio.Offer> offer) {
        offer.set(WrenchMeleeAudio.takeOffer((ServerLevel)(Object)this, sound.value()));
    }

    @WrapOperation(method = RACMC_SEEDED, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;"
                    + "DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"), require = 1)
    private void racmc$perListener(PlayerList list, Player except, double x, double y, double z,
            double radius, ResourceKey<Level> dimension, Packet<?> packet, Operation<Void> original,
            @Share("racmc$meleeOffer") LocalRef<WrenchMeleeAudio.Offer> offered) {
        var offer = offered.get();
        if (offer == null || !(packet instanceof ClientboundSoundPacket sound)
                || !WrenchMeleeSignal.unchanged(sound, offer.sound(), offer.category(), offer.volume(), offer.pitch())) {
            original.call(list, except, x, y, z, radius, dimension, packet);
            return;
        }
        var hit = offer.hit();
        var signal = WrenchMeleeSignal.offer(dimension.location(), hit.variant(), hit.position(), sound);
        if (signal == null) {
            original.call(list, except, x, y, z, radius, dimension, packet);
            return;
        }
        for (var player : list.getPlayers()) {
            if (!WrenchMeleeAudio.eligibleListener(player == except, player.level().dimension(),
                    dimension, player.distanceToSqr(x, y, z), radius)) continue;
            player.connection.send(signal.packetFor(player.connection.hasChannel(WrenchMeleeSignal.TYPE)));
        }
    }
}
