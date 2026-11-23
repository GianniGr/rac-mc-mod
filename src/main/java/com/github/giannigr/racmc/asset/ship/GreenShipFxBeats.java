package com.github.giannigr.racmc.asset.ship;

import java.util.List;

/** Source-beat envelopes, expressed in 60 Hz reference ticks, not Minecraft travel duration. */
public final class GreenShipFxBeats {
    private static final int FLAME_TEXTURE = 5;

    private GreenShipFxBeats() {}

    public record Anchor(float forward, float side, float up) {}
    public record Flame(Anchor anchor, float size) {}

    // Class 531 only. Ship-local source axes; map all anchors through the hull draw transform.
    public static final List<Flame> FLAMES = List.of(
            flame(1.85f, .13f), flame(1.42f, .42f), flame(1.42f, -.15f), flame(.80f, 1.6f),
            flame(-1.85f, .13f), flame(-1.42f, .42f), flame(-1.42f, -.15f), flame(-.80f, 1.6f));
    public static final List<Anchor> EXHAUST_VENTS = List.of(
            new Anchor(1.1f, 0, .7f), new Anchor(2.1f, 0, .7f),
            new Anchor(-.6f, -.9f, .7f), new Anchor(-1.6f, -.9f, .7f),
            new Anchor(-.6f, .9f, .7f), new Anchor(-1.6f, .9f, .7f));

    public record Sample(int glow, int flameCommand, int flameQuads, int flameTexture,
                         int exhaustBursts, boolean landingFlicker) {}

    /** Board, seat while parked, and exit share the idle glow: no thrust, dust or screen flash. */
    public static Sample parked(int tick) {
        double angle = ((tick * 2 & 0xff) - 0x80) * (Math.PI / 128);
        return sample(0x96 + (int) (Math.cos(angle) * 50), 0, 0, false);
    }

    /** Call only for an actual launch, not when boarding happens to reach the seat. */
    public static Sample takeoff(int tick) {
        if (tick < 360) {
            return sample(pulse(tick), 0, 0, false);
        }
        if (tick < 504) {
            return sample((int) ((tick - 360) * 1.25), Math.max(0, (int) ((tick - 420) * 1.2)),
                    0, tick > 420);
        }
        return sample(0xa0, 0, 0, false);
    }

    public static Sample landing(int tick) {
        if (tick < 240) {
            return new Sample(pulse(tick), 0x32, FLAMES.size(), FLAME_TEXTURE,
                    tick < 200 ? 2 : 0, true);
        }
        if (tick <= 360) {
            return sample((int) ((360 - tick) * 1.25),
                    Math.max(0, (int) ((300 - tick) * 1.5)), 0, tick < 300);
        }
        return sample(pulse(tick), 0, 0, false);
    }

    private static int pulse(int tick) {
        return 0x78 + (int) (80 * Math.cos(((tick & 63) - 32) * (Math.PI / 32)));
    }

    private static Sample sample(int glow, int flame, int bursts, boolean showFlame) {
        return new Sample(glow, flame, showFlame ? FLAMES.size() : 0,
                showFlame ? FLAME_TEXTURE : -1, bursts, false);
    }

    private static Flame flame(float side, float up) {
        return new Flame(new Anchor(-3.3f, side, up), .7f);
    }
}
