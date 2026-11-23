package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;
import static com.github.giannigr.racmc.ship.ShipWorldGameTests.*;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("racmc_ship_ratio")
@PrefixGameTestTemplate(false)
public final class ShipRatioTurboVerticalGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void ratioTurboClipsDiagonalCeilingThenLandsWithoutDismount(GameTestHelper h) {
        var level = h.getLevel();
        var base = pad(h);
        var center = Vec3.atBottomCenterOf(base);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 34; z++) {
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, 11, z), Blocks.STONE.defaultBlockState());
        }
        var ship = craft(level, center);
        var pilot = player(level, center.add(0, 0, -5.70), "ratio-vertical");
        var frames = new Frames(h, ship, pilot);
        board(h, ship, pilot);
        h.runAtTickTime(29, () -> frames.send(ACTIVE));
        h.runAtTickTime(30, () -> frames.send(ACTIVE | BOOST_HELD | BOOST_PRESS));
        double[] max = {0, 0};
        boolean[] clippedAndMoving = {false};
        for (int tick = 31; tick <= 50; tick++) {
            int flags = ACTIVE | UP | (tick <= 44 ? FORWARD : BRAKE);
            h.runAtTickTime(tick, () -> {
                var velocity = ship.getDeltaMovement();
                max[0] = Math.max(max[0], velocity.y);
                clippedAndMoving[0] |= velocity.horizontalDistance() > .5 && velocity.y == 0
                        && Math.abs(ship.getBoundingBox().maxY - (center.y + 11)) < 1e-6;
                frames.send(flags);
            });
        }
        boolean[] approach = {false};
        for (int tick = 51; tick <= 92; tick++) {
            int bits = ACTIVE | SNEAK | BRAKE | (tick == 51 ? EXIT_PRESS : 0);
            h.runAtTickTime(tick, () -> {
                max[1] = Math.max(max[1], -ship.getDeltaMovement().y);
                approach[0] |= ship.engineState() == ShipEngineState.APPROACH;
                h.assertTrue(ship.getBbWidth() == 10.4F && ship.getBoundingBox().maxY <= center.y + 11.000001
                        && ship.getY() >= center.y - 1e-6 && pilot.getVehicle() == ship,
                        "Ratio hull crossed a vertical collision or ejected held Sneak");
                frames.send(bits);
            });
        }
        h.runAtTickTime(94, () -> {
            h.assertTrue(max[0] >= .598 && max[1] >= .499
                    && max[0] <= .600001 && max[1] <= .500001 && clippedAndMoving[0],
                    "Ratio ascent/descent cap or diagonal ceiling clip was not witnessed");
            h.assertTrue(approach[0] && ship.engineState() == ShipEngineState.SUPPORTED
                    && Math.abs(ship.getY() - center.y) < 1e-6 && !ship.isBoosted()
                    && pilot.getVehicle() == ship, "Landing did not confirm/reset Turbo while staying seated");
            clean(level, ship, pilot);
            h.succeed();
        });
    }
}
