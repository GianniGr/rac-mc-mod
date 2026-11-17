package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WrenchSlamMotionTest {
    @Test void descentUsesUnitConvertedAccelerationAndTerminalCap() {
        assertEquals(-0.425, WrenchSlamMotion.descend(-0.1), 1.0e-9);
        assertEquals(-1.85, WrenchSlamMotion.descend(-1.8), 1.0e-9);
        assertEquals(-1.85, WrenchSlamMotion.descend(-5), 1.0e-9);
    }

    @Test void invalidMotionIsNotPropagatedIntoPlayerPhysics() {
        assertThrows(IllegalArgumentException.class,
                () -> WrenchSlamMotion.descend(Double.NaN));
    }
}
