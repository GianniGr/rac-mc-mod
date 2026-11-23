package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.sound.WrenchMeleeSignal;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Scoped only to the native primary hurt and subsequent direct feedback call in Player.attack. */
public final class WrenchMeleeAudio {
    public record Hit(WrenchMeleeSignal.Variant variant, Vec3 position) {}
    public record Attempt(boolean accepted, Hit hit) {}
    public record Offer(ServerLevel level, SoundEvent sound, SoundSource category, float volume,
            float pitch, Hit hit) {}

    private static final ThreadLocal<Witness> HURT = new ThreadLocal<>();
    private static final ThreadLocal<Offer> FEEDBACK = new ThreadLocal<>();

    private WrenchMeleeAudio() {}

    public static Attempt primaryHurt(Player player, Entity target, DamageSource source,
            boolean sprintFeedback, BooleanSupplier nativeHurt) {
        if (!(player instanceof ServerPlayer owner) || sprintFeedback || owner.isAutoSpinAttack()
                || !(target instanceof LivingEntity) || target instanceof ArmorStand
                || target.level() != owner.level() || !owner.isAlive()
                || source.getEntity() != owner || source.getDirectEntity() != owner
                || !source.is(DamageTypes.PLAYER_ATTACK)
                || !WrenchActions.isWrench(owner.getMainHandItem()) || WrenchActions.busy(owner)
                || WrenchActions.deliveringContact(owner) || !WrenchActions.claimMeleeAdmission(owner, target)) {
            return new Attempt(nativeHurt.getAsBoolean(), null);
        }
        var witness = new Witness(target, source);
        var at = target.getBoundingBox().getCenter();
        var previous = HURT.get();
        HURT.set(witness);
        try {
            boolean accepted = nativeHurt.getAsBoolean();
            Hit hit = witness.confirmed(accepted) && owner.level() == target.level()
                    && WrenchActions.isWrench(owner.getMainHandItem())
                    ? new Hit(variant(target.getType()), at) : null;
            return new Attempt(accepted, hit);
        } finally {
            restore(HURT, previous);
        }
    }

    static void positiveDamage(Entity target, DamageSource source, float damage) {
        Witness witness = HURT.get();
        if (witness != null) witness.record(target, source, damage);
    }

    static void incomingDamage(Entity target, DamageSource source) {
        Witness witness = HURT.get();
        if (witness != null) witness.incoming(target, source);
    }

    static WrenchMeleeSignal.Variant variant(EntityType<?> type) {
        return type == EntityType.IRON_GOLEM ? WrenchMeleeSignal.Variant.ALTERNATE
                : WrenchMeleeSignal.Variant.DEFAULT;
    }

    public static boolean directFeedback(SoundEvent sound) {
        return sound == SoundEvents.PLAYER_ATTACK_STRONG || sound == SoundEvents.PLAYER_ATTACK_WEAK;
    }

    public static void feedback(Level level, Hit hit, SoundEvent sound, SoundSource category,
            float volume, float pitch, Runnable nativeSound) {
        if (hit == null || !(level instanceof ServerLevel server) || !directFeedback(sound)) {
            nativeSound.run();
            return;
        }
        var previous = FEEDBACK.get();
        FEEDBACK.set(new Offer(server, sound, category, volume, pitch, hit));
        try {
            nativeSound.run();
        } finally {
            restore(FEEDBACK, previous);
        }
    }

    /** Consume on entry to the actual seeded overload, before its potentially reentrant event. */
    public static Offer takeOffer(ServerLevel level, SoundEvent sound) {
        Offer offer = FEEDBACK.get();
        if (offer == null || offer.level() != level || offer.sound() != sound) return null;
        FEEDBACK.remove();
        return offer;
    }

    public static boolean eligibleListener(boolean excluded, ResourceKey<Level> listenerDimension,
            ResourceKey<Level> offeredDimension, double distanceSquared, double radius) {
        // Mirrors PlayerList.broadcast; audience is based on the actual vanilla sound invocation.
        return !excluded && listenerDimension == offeredDimension && distanceSquared < radius * radius;
    }

    private static <T> void restore(ThreadLocal<T> scope, T previous) {
        if (previous == null) scope.remove();
        else scope.set(previous);
    }

    static final class Witness {
        private final Object target, source;
        private int admissions, positive;

        Witness(Object target, Object source) {
            this.target = target;
            this.source = source;
        }

        void incoming(Object target, Object source) {
            if (this.target == target && this.source == source)
                admissions = Math.min(2, admissions + 1);
        }

        void record(Object target, Object source, float damage) {
            if (this.target == target && this.source == source && Float.isFinite(damage) && damage > 0)
                positive = Math.min(2, positive + 1);
        }

        boolean confirmed(boolean accepted) { return accepted && admissions == 1 && positive == 1; }
    }
}
