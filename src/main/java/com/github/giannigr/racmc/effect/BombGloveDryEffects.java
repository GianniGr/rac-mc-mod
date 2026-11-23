package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveActionEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Presentation subscriber only: the dry signal originates at the sealed accepted burst. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class BombGloveDryEffects {
    private BombGloveDryEffects() {}

    @SubscribeEvent
    public static void action(BombGloveActionEvent event) {
        var bomb = event.bomb();
        if (accepts(event.kind(), bomb.enteredWater()) && bomb.level() instanceof ServerLevel level) {
            Rac1EffectTriggers.bombGloveDryBurst(level, bomb.getUUID(), bomb.position());
        }
    }

    static boolean accepts(BombGloveActionEvent.Kind kind, boolean enteredWater) {
        return kind == BombGloveActionEvent.Kind.BLAST && !enteredWater;
    }
}
