package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.ship.GreenShipFxBeats;
import com.github.giannigr.racmc.asset.ship.GreenShipHullPose;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Local cosmetic type-02 puffs. Positions detach from the hull at the instant of emission. */
final class GreenShipExhaust {
    private static final int CAP = 64;
    private static final float FIT = GreenShipHullPose.BLOCKS_PER_UNIT;
    private final ArrayList<Puff> puffs = new ArrayList<>();

    record Puff(Vec3 position, Vec3 velocity, long fromTick, int sourceLifetime, float rotation) {}
    record Draw(Vec3 position, float size, float rotation, int argb) {}

    void emit(int bursts, long tick, Vec3 shipPosition, float yaw, long seed) {
        prune(tick);
        if (bursts < 1 || bursts > 2 || puffs.size() + 6 * bursts > CAP) {
            return;
        }
        var random = new SplittableRandom(seed ^ tick);
        for (int burst = 0; burst < bursts; burst++) {
            for (var vent : GreenShipFxBeats.EXHAUST_VENTS) {
                var placed = new Vec3(vent.side(), vent.up(), vent.forward()).scale(FIT)
                        .yRot(-yaw * Mth.DEG_TO_RAD).add(shipPosition);
                // Source velocity uses -4dt/-3dt and ±dt side spread. The PRNG is a client adaptation.
                var velocity = new Vec3(random.nextDouble(-1, 1), -(4 - burst) + random.nextDouble(-.25, .25),
                        random.nextDouble(-1, 1)).scale(FIT / 60);
                puffs.add(new Puff(placed, velocity, tick, 12 + random.nextInt(4),
                        (float) random.nextDouble(0, 2 * Math.PI)));
            }
        }
    }

    List<Draw> sample(long tick, float partial) {
        prune(tick);
        var draw = new ArrayList<Draw>(puffs.size());
        for (Puff puff : puffs) {
            double age = (tick - puff.fromTick() + Math.clamp(partial, 0, 1)) * 3;
            if (age < 0 || age >= puff.sourceLifetime()) {
                continue;
            }
            // Type-02 GS alpha 0 -> 0x24 -> 0x14 -> 0 with sizes .4/.6.
            double fade = age < 4 ? 36 * age / 4 : age < 8 ? 36 - (age - 4) * 4
                    : 20 * (1 - (age - 8) / (puff.sourceLifetime() - 8));
            int alpha = (int) Math.clamp(fade * 255 / 128, 0, 255);
            float size = FIT * (.4f + .2f * (float) Math.clamp((age - 4) / 4, 0, 1));
            draw.add(new Draw(puff.position().add(puff.velocity().scale(age)), size,
                    (float) (puff.rotation() + age * Math.PI / 256), alpha << 24 | 0xffffff));
        }
        return List.copyOf(draw);
    }

    private void prune(long tick) {
        puffs.removeIf(puff -> tick - puff.fromTick() >= 5 || tick < puff.fromTick());
    }

    void clear() {
        puffs.clear();
    }
}
