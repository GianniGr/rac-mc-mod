package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.actions.WrenchActionState.AudioEdge;
import com.github.giannigr.racmc.actions.WrenchActionState.AudioKind;
import com.github.giannigr.racmc.sound.WrenchAudioSignal;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative wrench audio edges, separate from animation packets and contact delivery. */
final class WrenchActionAudio {
    private WrenchActionAudio() {}

    static WrenchAudioSignal.Kind binding(AudioKind kind, WrenchActionPresentation.Phase phase) {
        return switch (kind) {
            case SLAM_IMPACT -> WrenchAudioSignal.Kind.SLAM_IMPACT;
            // Release/catch in the same batch has no sustained episode. No synthetic one-shot.
            case COMET_RELEASE -> WrenchActionPresentation.inFlight(phase)
                    ? WrenchAudioSignal.Kind.COMET_RELEASE : null;
        };
    }

    static void dispatch(ServerPlayer owner, AudioEdge edge, long serial,
            WrenchActionPresentation.Phase phase) {
        var cue = binding(edge.kind(), phase);
        if (cue == null) return;
        WrenchAudioSignal.emit(owner, cue, serial, WrenchActions.vec(emitter(edge,
                WrenchActions.vector(owner.position()))));
    }

    static ActionVector emitter(AudioEdge edge, ActionVector owner) {
        return edge.kind() == AudioKind.COMET_RELEASE ? owner : edge.position();
    }

    /** A native attempt is not a hit; consume its audio edge only on accepted damage. */
    static final class MeleeGate {
        private long armedAt;
        private UUID target;

        void arm(long tick, UUID target) {
            armedAt = tick;
            this.target = target;
        }

        boolean claim(long tick, UUID hit) {
            if (tick != armedAt || target == null || !target.equals(hit)) return false;
            clear();
            return true;
        }

        void clear() { target = null; }
    }
}
