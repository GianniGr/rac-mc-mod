package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.item.weapon.bomb.BombGloveActionEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** One optional semantic signal at the accepted server blast; no effect on bomb simulation. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class BombGloveDryAudio {
    private BombGloveDryAudio() {}

    @SubscribeEvent
    public static void action(BombGloveActionEvent event) {
        var cue = cueFor(event.kind());
        if (cue == null || !(event.bomb().level() instanceof ServerLevel level)) return;
        Rac1AudioSignal.emit(level, event.bomb().position(), cue);
    }

    static Rac1AudioEvent cueFor(BombGloveActionEvent.Kind action) {
        return action == BombGloveActionEvent.Kind.BLAST ? Rac1AudioEvent.BOMB_GLOVE_DRY_BURST : null;
    }
}
