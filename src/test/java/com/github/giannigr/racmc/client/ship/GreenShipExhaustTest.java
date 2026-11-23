package com.github.giannigr.racmc.client.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ship.ShipCockpitRefit;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GreenShipExhaustTest {
    @Test
    void twoBurstsUseSixHullVentsAndDetachIntoWorldSpace() {
        var exhaust = new GreenShipExhaust();
        exhaust.emit(2, 30, new Vec3(8, 64, 6), 90, 123);
        var newborn = exhaust.sample(30, 0);
        assertEquals(12, newborn.size());
        double fit = ShipCockpitRefit.activeProfile().blocksPerSourceUnit();
        assertEquals(8 - 1.1 * fit, newborn.getFirst().position().x, .0001);
        assertEquals(64 + .7 * fit, newborn.getFirst().position().y, .0001);
        assertEquals(newborn.get(0).position(), newborn.get(6).position());
        var moving = exhaust.sample(31, 0);
        assertTrue(moving.getFirst().position().y < newborn.getFirst().position().y);
        assertTrue((moving.getFirst().argb() >>> 24) > 0);
        assertTrue(exhaust.sample(35, 0).isEmpty());
    }

    @Test
    void rejectsUnboundedBurstsAndDoesNotSpawnAnythingOnRead() {
        var exhaust = new GreenShipExhaust();
        exhaust.emit(3, 2, Vec3.ZERO, 0, 0);
        assertTrue(exhaust.sample(2, 0).isEmpty());
        for (int i = 0; i < 10; i++) {
            exhaust.emit(2, 2, Vec3.ZERO, 0, i);
        }
        assertEquals(60, exhaust.sample(2, 0).size());
    }
}
