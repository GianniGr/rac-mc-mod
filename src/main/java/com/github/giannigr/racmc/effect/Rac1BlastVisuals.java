package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import java.util.Random;
import java.util.UUID;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Bounded blast slice: six type13 dust sprites + class112 flash shells.
 * Type11 branching sparks/smoke and P29 cosmetic debris are intentionally NOT duplicated here.
 * Independent implementation from permitted R09 format/behavior facts, not original code.
 * Event seed is deterministic locally, never claimed to match the game's shared RNG stream. */
public final class Rac1BlastVisuals {
    private Rac1BlastVisuals() {}
    public static int emit(EffectRuntime runtime, String sha1, UUID owner, Point origin, Point camera,
                           long seed, boolean extraShellsAllowed) {
        Random random = new Random(seed);
        int before = runtime.size();
        dust(runtime, sha1, owner, origin, random);
        shell(runtime, sha1, owner, origin, random, 3.5f, 27, 0x40001060);
        shell(runtime, sha1, owner, origin, random, 3, 29, 0x20000020);
        if (extraShellsAllowed && origin.distanceSquared(camera) > 81) {
            shell(runtime, sha1, owner, origin, random, 4, 15, 0x207f7f7f);
            shell(runtime, sha1, owner, origin, random, 4, 24, 0x2000207f);
            shell(runtime, sha1, owner, origin, random, 4, 20, 0x3000407f);
        }
        return runtime.size() - before;
    }
    public static void dust(EffectRuntime runtime, String sha1, UUID owner, Point origin, Random random) {
        EffectAssetRef dust = new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.PARTICLE, 0);
        for (int puff = 0; puff < 6; puff++) {
            Point position = origin.add(jitter(random, .3), .3 + jitter(random, .3), jitter(random, .3));
            float growth = 1.01f + random.nextFloat() * (puff == 0 ? .065f : .06f);
            runtime.spawn(new Spawn(owner, dust, Geometry.BILLBOARD, Program.DUST13, position,
                    new Point(0, .05, 0), 50000, growth, new Point(0, 0, 0),
                    (random.nextBoolean() ? 1 : -1) * (float) (Math.PI * 2 / 256),
                    0x40808080, runtime.profile().ticks(40), runtime.profile().ticks(10), EffectMaterial.ADDITIVE));
        }
    }
    private static void shell(EffectRuntime runtime, String sha1, UUID owner, Point origin,
                              Random random, float size, int ticks, int colour) {
        // R04's "3 draws" meant three random Euler angles, NEVER three render submissions.
        Point rotation = new Point(random.nextFloat() * Math.PI * 2,
                random.nextFloat() * Math.PI * 2, random.nextFloat() * Math.PI * 2);
        runtime.spawn(new Spawn(owner, new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.MOBY, 112),
                Geometry.MESH, Program.FLASH112, origin.add(0, .5, 0), new Point(0, 0, 0),
                size, 1, rotation, (float) Math.PI / runtime.profile().hz(), colour,
                runtime.profile().ticks(ticks), runtime.profile().ticks(4), EffectMaterial.NORMAL_REPEAT));
    }
    private static double jitter(Random random, double limit) { return (random.nextDouble() * 2 - 1) * limit; }
}
