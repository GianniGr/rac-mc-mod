package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import com.github.giannigr.racmc.client.clank.Rac1HydroHeldKeys;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;

class ShipPilotInputTest {
    @Test
    void physicalRemapsNeitherHonorToggleLatchNorCreateFreshPressOnFocusOrNewSeat() {
        var keys = new Harness();
        keys.press(GLFW_KEY_LEFT_SHIFT);
        keys.press(GLFW_KEY_LEFT_CONTROL);
        assertEquals(SNEAK | BOOST_HELD, keys.flags(1, 0, true) & (SNEAK | BOOST_HELD | EXIT_PRESS | BOOST_PRESS));
        assertEquals(SNEAK | BOOST_HELD, keys.flags(1, 5, true) & (SNEAK | BOOST_HELD | EXIT_PRESS | BOOST_PRESS));
        assertEquals(0, keys.flags(1, 5, false));
        assertEquals(0, keys.flags(1, 5, true) & (EXIT_PRESS | BOOST_PRESS));
        assertEquals(0, keys.flags(2, 6, true) & (EXIT_PRESS | BOOST_PRESS));
        keys.held.clear();
        keys.flags(2, 6, true);
        keys.press(GLFW_KEY_LEFT_SHIFT);
        keys.press(GLFW_KEY_LEFT_CONTROL);
        assertEquals(EXIT_PRESS | BOOST_PRESS, keys.flags(2, 6, true) & (EXIT_PRESS | BOOST_PRESS));
        assertEquals(0, keys.flags(2, 6, true) & (EXIT_PRESS | BOOST_PRESS));

        // Saved Toggle Sneak remains set; it is not sampled by flight.
        keys.sneak.setDown(true);
        keys.held.clear();
        assertEquals(0, keys.flags(2, 6, true) & SNEAK);
        assertTrue(keys.sneak.isDown());
        keys.press(GLFW_KEY_J);
        keys.boost.setKeyModifierAndCode(KeyModifier.NONE, key(GLFW_KEY_J));
        assertEquals(BOOST_HELD, keys.flags(2, 6, true) & (BOOST_HELD | BOOST_PRESS));
        keys.held.clear();
        keys.flags(2, 6, true);
        keys.press(GLFW_KEY_J);
        assertEquals(BOOST_PRESS, keys.flags(2, 6, true) & BOOST_PRESS);
        keys.state.interrupt(); // blur/refocus without any inactive tick being sent
        assertEquals(0, keys.flags(2, 6, true) & BOOST_PRESS);
    }

    @Test
    void sharedSprintBindingUsesVanillaOnFootAndIsNotRemapped() {
        var keys = new Harness();
        var sprint = new ToggleKeyMapping("ship-test-sprint", GLFW_KEY_LEFT_CONTROL, "test", () -> true);
        sprint.setKeyConflictContext(KeyConflictContext.IN_GAME);
        assertEquals(sprint.getKey(), keys.boost.getKey());
        assertFalse(keys.boost.getKeyConflictContext().conflicts(sprint.getKeyConflictContext()));
        assertFalse(ShipPilotInput.blocksSprint(false, sprint, sprint));
        assertTrue(ShipPilotInput.blocksSprint(true, sprint, sprint));
        assertFalse(ShipPilotInput.blocksSprint(true, keys.boost, sprint));
        assertEquals(key(GLFW_KEY_LEFT_CONTROL), sprint.getKey());
        sprint.setKeyModifierAndCode(KeyModifier.NONE, key(GLFW_KEY_K));
        assertFalse(ShipPilotInput.blocksSprint(false, sprint, sprint));
        assertEquals(key(GLFW_KEY_K), sprint.getKey());
        assertEquals(key(GLFW_KEY_LEFT_CONTROL), keys.boost.getKey());
    }

    private static InputConstants.Key key(int key) { return InputConstants.Type.KEYSYM.getOrCreate(key); }

    private static final class Harness {
        final Set<InputConstants.Key> held = new HashSet<>();
        final ShipPilotInput.FrameState state = new ShipPilotInput.FrameState();
        final KeyMapping sneak = new ToggleKeyMapping("ship-test-sneak", GLFW_KEY_LEFT_SHIFT, "test", () -> true);
        final KeyMapping boost = ShipPilotInput.boostMapping(() -> true);
        final KeyMapping unused = new KeyMapping("ship-test-unassigned", -1, "test");
        final ShipPilotInput.Keys keys = new ShipPilotInput.Keys(unused, unused, unused, unused, unused, sneak, boost);

        void press(int value) { held.add(key(value)); }
        int flags(int seat, int ground, boolean active) {
            return state.frame(34, seat, ground, active, keys,
                    mapping -> Rac1HydroHeldKeys.down(mapping, true, held::contains)).flags();
        }
    }
}
