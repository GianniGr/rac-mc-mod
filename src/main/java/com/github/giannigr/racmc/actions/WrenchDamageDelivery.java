package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Native hurt/armor/protection dispatch with server-owned legacy source identity bookkeeping. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class WrenchDamageDelivery {
    static final WrenchThrowReceiverGate RECEIVERS = new WrenchThrowReceiverGate();
    public static final float THROW_DAMAGE = 5.1F;
    private record Context(ServerPlayer owner, LivingEntity receiver, int identity, long sourceUpdate) {}
    private static Context current;
    private WrenchDamageDelivery() {}

    static boolean hurt(ServerPlayer owner, LivingEntity receiver, float damage, int identity,
            long sourceUpdate) {
        Context previous = current;
        current = new Context(owner, receiver, identity, sourceUpdate);
        try {
            boolean accepted = receiver.hurt(owner.damageSources().playerAttack(owner), damage);
            if (accepted) RECEIVERS.accepted(owner.getUUID(), receiver.getUUID(), identity, sourceUpdate);
            return accepted;
        } finally {
            current = previous;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void observeNativeIncoming(LivingIncomingDamageEvent event) {
        // A mod reusing the very same source/target in a nested hurt is ambiguous.
        WrenchMeleeAudio.incomingDamage(event.getEntity(), event.getSource());
    }

    @SubscribeEvent
    public static void acceptedNativeDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer owner)
                || event.getSource().getDirectEntity() != owner
                || !WrenchActions.isWrench(owner.getMainHandItem()) || event.getNewDamage() <= 0) return;
        Context context = current != null && current.owner == owner
                && current.receiver == event.getEntity() ? current : null;
        RECEIVERS.accepted(owner.getUUID(), event.getEntity().getUUID(),
                context == null ? 0x47 : context.identity,
                context == null ? owner.server.getTickCount() * 3L : context.sourceUpdate);
        // Managed slam/throw and crate feedback have their own owners. Don't infer an
        // ordinary melee hit from a swing, a pre-attack event, or indirect damage.
        if (current == null && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && owner.level() == event.getEntity().level() && !WrenchActions.busy(owner)) {
            WrenchMeleeAudio.positiveDamage(event.getEntity(), event.getSource(), event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECEIVERS.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        RECEIVERS.clear();
        current = null;
    }
}
