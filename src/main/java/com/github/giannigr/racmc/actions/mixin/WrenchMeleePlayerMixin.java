package com.github.giannigr.racmc.actions.mixin;

import com.github.giannigr.racmc.actions.WrenchMeleeAudio;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class WrenchMeleePlayerMixin {
    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 1)
    private boolean racmc$primaryHurt(Entity target, DamageSource damage, float amount,
            Operation<Boolean> original, @Share("racmc$sprintFeedback") LocalBooleanRef sprint,
            @Share("racmc$confirmedHit") LocalRef<WrenchMeleeAudio.Hit> confirmed) {
        var result = WrenchMeleeAudio.primaryHurt((Player)(Object)this, target, damage, sprint.get(),
                () -> original.call(target, damage, amount));
        confirmed.set(result.hit());
        return result.accepted();
    }

    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;"
                    + "DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"), require = 6)
    private void racmc$directFeedback(Level level, Player except, double x, double y, double z,
            SoundEvent sound, SoundSource source, float volume, float pitch, Operation<Void> original,
            @Share("racmc$sprintFeedback") LocalBooleanRef sprint,
            @Share("racmc$confirmedHit") LocalRef<WrenchMeleeAudio.Hit> confirmed) {
        if (sound == SoundEvents.PLAYER_ATTACK_KNOCKBACK) sprint.set(true);
        var hit = WrenchMeleeAudio.directFeedback(sound) ? confirmed.get() : null;
        if (hit != null) confirmed.set(null);
        WrenchMeleeAudio.feedback(level, hit, sound, source, volume, pitch,
                () -> original.call(level, except, x, y, z, sound, source, volume, pitch));
    }
}
