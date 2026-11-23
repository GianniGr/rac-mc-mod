package com.github.giannigr.racmc.ship;

import static com.github.giannigr.racmc.ship.ShipFlight.*;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Raw held keys for motion, separately qualified edges for exit and toggle. */
public final class ShipPilotInput {
    static final FrameState FRAMES = new FrameState();

    static KeyMapping boostMapping(BooleanSupplier active) {
        return new KeyMapping("key.racmc.travel_ship.boost", new IKeyConflictContext() {
            @Override public boolean isActive() { return active.getAsBoolean(); }
            @Override public boolean conflicts(IKeyConflictContext other) { return this == other; }
        }, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.racmc.travel_ship") {
            @Override
            public boolean isConflictContextAndModifierActive() {
                // In a custom NeoForge context NONE normally rejects all held modifiers, which
                // would make bare Left Ctrl deactivate its own mapping. Explicit chords still apply.
                return getKeyConflictContext().isActive()
                        && (getKeyModifier() == KeyModifier.NONE
                                || getKeyModifier().isActive(getKeyConflictContext()));
            }
        };
    }

    static boolean isSeated(Minecraft mc) {
        return mc != null && mc.player != null && mc.player.getVehicle() instanceof Rac1TravelShip;
    }

    public static boolean blocksSprint(KeyMapping mapping) {
        var mc = Minecraft.getInstance();
        return isSeated(mc) && blocksSprint(true, mapping, mc.options.keySprint);
    }

    static boolean blocksSprint(boolean seated, KeyMapping mapping, KeyMapping sprint) {
        return seated && mapping == sprint;
    }

    public static void interrupt() { FRAMES.interrupt(); }

    record Keys(KeyMapping forward, KeyMapping brake, KeyMapping left, KeyMapping right,
                KeyMapping jump, KeyMapping sneak, KeyMapping boost) {}

    static final class FrameState {
        private final PressLatch sneak = new PressLatch();
        private final PressLatch boost = new PressLatch();
        private int craft;
        private int seat;
        private int sequence;
        private int ground;

        void interrupt() {
            sneak.clear();
            boost.clear();
        }

        ShipInputPayload frame(int craftId, int seatId, int groundSerial, boolean active,
                               Keys keys, Predicate<KeyMapping> held) {
            if (craft != craftId || seat != seatId) {
                interrupt();
                craft = craftId;
                seat = seatId;
                sequence = 0;
            }
            if (ground != groundSerial) {
                sneak.clear();
                ground = groundSerial;
            }
            if (!active) {
                interrupt();
                return new ShipInputPayload(craft, seat, ++sequence, 0, 0);
            }
            boolean sneaking = held.test(keys.sneak);
            boolean boosting = held.test(keys.boost);
            boolean exit = sneak.press(keys.sneak, sneaking) && ground != 0;
            boolean duplicate = keys.sneak.getKey().equals(keys.boost.getKey())
                    && keys.sneak.getKeyModifier() == keys.boost.getKeyModifier();
            boolean toggle = boost.press(keys.boost, boosting) && !duplicate;
            int flags = ACTIVE | bit(held, keys.forward, FORWARD) | bit(held, keys.brake, BRAKE)
                    | bit(held, keys.left, LEFT) | bit(held, keys.right, RIGHT) | bit(held, keys.jump, UP)
                    | (sneaking ? SNEAK : 0) | (exit ? EXIT_PRESS : 0)
                    | (boosting ? BOOST_HELD : 0) | (toggle ? BOOST_PRESS : 0);
            return new ShipInputPayload(craft, seat, ++sequence, ground, flags);
        }
    }

    private static final class PressLatch {
        private InputConstants.Key key;
        private KeyModifier modifier;
        private boolean armed;

        boolean press(KeyMapping binding, boolean down) {
            if (!binding.isConflictContextAndModifierActive()) {
                clear();
                return false;
            }
            if (!binding.getKey().equals(key) || binding.getKeyModifier() != modifier) {
                clear();
                key = binding.getKey();
                modifier = binding.getKeyModifier();
            }
            boolean pressed = down && armed;
            armed = !down;
            return pressed;
        }

        void clear() {
            key = null;
            modifier = null;
            armed = false;
        }
    }

    private static int bit(Predicate<KeyMapping> held, KeyMapping mapping, int flag) {
        return held.test(mapping) ? flag : 0;
    }

    private ShipPilotInput() {}
}
