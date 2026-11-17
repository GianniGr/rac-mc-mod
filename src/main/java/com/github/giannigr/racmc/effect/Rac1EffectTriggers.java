package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.RacMcMod;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Only authoritative accepted transactions call these effects-only hooks.
 * No game RNG draws, sound lookup, asset bytes or dependencies on client classes. */
public final class Rac1EffectTriggers {
    private Rac1EffectTriggers() {}
    public static EffectSignal.Point point(Vec3 point) {
        return new EffectSignal.Point(point.x, point.y, point.z);
    }
    public static void shot(Entity arrow) {
        if (arrow.level() instanceof ServerLevel level)
            emit(level, arrow.getUUID(), EffectSignal.Semantic.BLASTER_SHOT, 0, arrow.position(), null);
    }
    public static void end(Entity arrow) {
        if (arrow.level() instanceof ServerLevel level)
            emit(level, arrow.getUUID(), EffectSignal.Semantic.BLASTER_END, 1, arrow.position(), null);
    }
    public static void impact(Entity arrow, Vec3 position, Vec3 tangent, Vec3 normal) {
        if (arrow.level() instanceof ServerLevel level && normal.lengthSqr() > 1.0e-8)
            emit(level, arrow.getUUID(), EffectSignal.Semantic.BLASTER_IMPACT, 1, position,
                    new EffectSignal.Impact(point(tangent.normalize()), point(normal.normalize())));
    }
    public static void blast(ServerLevel level, Vec3 position) {
        emit(level, UUID.randomUUID(), EffectSignal.Semantic.CRATE_TNT_BLAST, 0, position, null);
    }
    public static void crateBreak(ServerLevel level, UUID id, Vec3 position, int sourceClass) {
        var semantic = sourceClass == 501 ? EffectSignal.Semantic.CRATE_BREAK_HEALTH
                : sourceClass == 500 || sourceClass == 511 ? EffectSignal.Semantic.CRATE_BREAK_WOOD : null;
        if (semantic != null) emit(level, id, semantic, 0, position, null);
    }
    private static void emit(ServerLevel level, UUID id, EffectSignal.Semantic semantic, long sequence,
                             Vec3 position, EffectSignal.Impact impact) {
        try {
            long tick = level.getGameTime();
            long seed = semantic == EffectSignal.Semantic.BLASTER_SHOT ? id.getLeastSignificantBits()
                    : id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ tick;
            EffectSignal signal = new EffectSignal(semantic, level.dimension().location().toString(),
                    id, sequence, tick, seed,
                    EffectSignal.Op.START, point(position), impact);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new EffectTriggeredEvent(signal));
            Rac1EffectNetwork.send(level, signal);
        } catch (RuntimeException failure) {
            // Cosmetic transport failure cannot roll back or suppress damage/reward/firing.
            RacMcMod.LOGGER.debug("Optional effect signal unavailable: {}", failure.toString());
        }
    }
}
