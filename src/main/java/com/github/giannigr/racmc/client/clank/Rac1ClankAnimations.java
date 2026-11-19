package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.clank.Rac1ClankCapability.Action;
import com.github.giannigr.racmc.clank.Rac1ClankClock.Profile;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;

/** Source-backed clip pairs. Ratchet sequence/frame is a NONRENDERED phase dependency only. */
public final class Rac1ClankAnimations {
    public record Selection(int clankSequence, int packSequence, int sourceHeroSequence,
                             int packBlendSteps) {}
    private Rac1ClankAnimations() {}
    public static Selection select(Module module, Action action, Profile profile) {
        if (module == Module.HELI) return switch (action) {
            case HIGH_JUMP -> new Selection(12, 5, 21, profile.ticks(2));
            case LONG_JUMP -> new Selection(15, 8, 18, profile.ticks(2));
            case GLIDE -> new Selection(1, 6, 19, profile.ticks(10));
            default -> new Selection(1, 1, 0, profile.ticks(2));
        };
        // No original special 608 long/high pair: do not invent a rotor/Thruster action clip.
        return new Selection(1, 1, switch (action) {
            case HIGH_JUMP -> 17;
            case LONG_JUMP -> 38;
            case GLIDE -> 19;
            default -> 0;
        }, profile.ticks(2));
    }

    /** An authored-frame segment, not a fixed-FPS hero clip or a movement authority. */
    public record HeroPhase(double fromFrame, double toFrame, int durationSourceSteps) {
        public HeroPhase {
            if (!Double.isFinite(fromFrame) || !Double.isFinite(toFrame) || durationSourceSteps < 1)
                throw new IllegalArgumentException("Invalid source hero segment");
        }
        public double frame(double elapsedSourceSteps) {
            if (!Double.isFinite(elapsedSourceSteps) || elapsedSourceSteps < 0)
                throw new IllegalArgumentException("Invalid source hero phase time");
            return fromFrame + (toFrame - fromFrame)
                    * Math.min(1, elapsedSourceSteps / durationSourceSteps);
        }
    }
}
