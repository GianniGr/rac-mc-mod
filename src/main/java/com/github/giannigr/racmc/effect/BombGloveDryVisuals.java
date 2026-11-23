package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.effect.EffectAssetRef;
import java.util.Random;
import java.util.UUID;
import static com.github.giannigr.racmc.effect.EffectRuntime.*;
import static com.github.giannigr.racmc.effect.EffectSignal.Point;

/** Original class-1192 flash shell layer only. No crate art, wet effects or replacement particles. */
public final class BombGloveDryVisuals {
    public static final int FLASH_CLASS = 1192;
    private static final Point STILL = new Point(0, 0, 0);
    private BombGloveDryVisuals() {}

    public static int emit(EffectRuntime runtime, String sha1, UUID owner, Point origin, Point camera, long seed) {
        int before = runtime.size();
        var asset = new EffectAssetRef(sha1, 1, EffectAssetRef.Bank.MOBY, FLASH_CLASS);
        var rng = new Random(seed);
        // The optional far-camera pair follows the three always-on flashes for admission
        // under the Minecraft pool cap; colours/sizes/timers are the Novalis dry call.
        shell(runtime, asset, owner, origin, rng, 4, 20, 0x30003f7f);
        shell(runtime, asset, owner, origin, rng, 3.5f, 27, 0x40001060);
        shell(runtime, asset, owner, origin, rng, 3, 29, 0x20000020);
        if (origin.distanceSquared(camera) > 81) {
            shell(runtime, asset, owner, origin, rng, 4, 15, 0x207f7f7f);
            shell(runtime, asset, owner, origin, rng, 4, 24, 0x2000207f);
        }
        return runtime.size() - before;
    }

    private static void shell(EffectRuntime runtime, EffectAssetRef asset, UUID owner, Point origin,
                              Random rng, float fullSize, int ticks, int colour) {
        var rotation = new Point(angle(rng), angle(rng), angle(rng));
        // The logical burst has no verified source-unit impact drift vector. Keep
        // the original mesh/tint/timer/spin anchored at the accepted burst instead.
        runtime.spawn(new Spawn(owner, asset, Geometry.MESH, Program.FLASH1192, origin, STILL, fullSize, 1,
                rotation, (float) Math.PI / runtime.profile().hz(), colour,
                runtime.profile().ticks(ticks), 0, EffectMaterial.NORMAL_REPEAT));
    }

    private static double angle(Random rng) { return (2 * rng.nextDouble() - 1) * Math.PI; }
}
