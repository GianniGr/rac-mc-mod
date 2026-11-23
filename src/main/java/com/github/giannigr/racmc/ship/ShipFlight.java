package com.github.giannigr.racmc.ship;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Digital controls only: nothing on the wire can describe a transform or velocity. */
final class ShipFlight {
    static final int FORWARD = 1, BRAKE = 2, LEFT = 4, RIGHT = 8, UP = 16, SNEAK = 32;
    static final int ACTIVE = 64, EXIT_PRESS = 128, BOOST_HELD = 256, BOOST_PRESS = 512;
    static final double NORMAL_SPEED = .40, BOOST_SPEED = 1.50;
    static final double MAX_CLIMB = .18, MAX_DESCENT = .14, BOOST_CLIMB = .60, BOOST_DESCENT = .50;
    static final byte THRUST_FORWARD = 1, THRUST_CLIMB = 2, THRUST_DESCEND = 4;
    static final int FRESH_TICKS = 4;

    record Step(float yaw, Vec3 velocity, boolean forward, int lift) {
        byte actualBoostThrust(Vec3 moved) {
            return (byte) ((deliveredForward(moved) ? THRUST_FORWARD : 0)
                    | (lift > 0 && moved.y > 1e-7 ? THRUST_CLIMB : 0)
                    | (lift < 0 && moved.y < -1e-7 ? THRUST_DESCEND : 0));
        }

        boolean deliveredForward(Vec3 moved) {
            double heading = Math.toRadians(yaw);
            return forward && moved.z * Math.cos(heading) - moved.x * Math.sin(heading) > 1e-7;
        }
    }

    static boolean valid(int flags) {
        return (flags & ~1023) == 0 && (flags == 0 || (flags & ACTIVE) != 0)
                && ((flags & EXIT_PRESS) == 0 || (flags & SNEAK) != 0)
                && ((flags & BOOST_PRESS) == 0 || (flags & BOOST_HELD) != 0);
    }

    static Step step(int flags, float yaw, Vec3 previous, boolean grounded, boolean boosted) {
        yaw = Mth.wrapDegrees(yaw + 3.5F * (held(flags, RIGHT) - held(flags, LEFT)));
        int lift = held(flags, UP) - (grounded ? 0 : held(flags, SNEAK));
        boolean thrust = held(flags, FORWARD) != 0 && held(flags, BRAKE) == 0
                && (!grounded || lift > 0);
        double heading = Math.toRadians(yaw);
        double cap = boosted ? BOOST_SPEED : NORMAL_SPEED;
        double speed = thrust ? cap : 0;
        double climb = boosted ? BOOST_CLIMB : MAX_CLIMB;
        double descent = boosted ? BOOST_DESCENT : MAX_DESCENT;
        double vertical = lift > 0 ? climb : lift < 0 ? -descent : 0;
        double rate = held(flags, BRAKE) != 0 ? 0.65 : 0.27;
        double x = Mth.lerp(rate, previous.x, -Math.sin(heading) * speed);
        double z = Mth.lerp(rate, previous.z, Math.cos(heading) * speed);
        double speedSquared = x * x + z * z;
        if (speedSquared > cap * cap) {
            double limit = cap / Math.sqrt(speedSquared);
            x *= limit;
            z *= limit;
        }
        double y = Mth.clamp(Mth.lerp(0.42, previous.y, vertical), -descent, climb);
        return new Step(yaw, new Vec3(deadband(x), deadband(y), deadband(z)), thrust, lift);
    }

    private static double deadband(double v) {
        return Math.abs(v) < 0.004 ? 0 : v;
    }

    private static int held(int flags, int key) {
        return (flags & key) == 0 ? 0 : 1;
    }

    /** No debounce state survives an occupancy change. Ordinary movement and exit have separate gates. */
    static final class Input {
        private long last = Long.MIN_VALUE;
        private int sequence;
        private int flags;
        private int armedGround;
        private int exitGround;
        private long exitAt;
        private boolean boostArmed;
        private boolean boostPress;
        private long boostAt;

        boolean accept(int seq, int clientGround, int serverGround, int value, long tick) {
            if (!valid(value) || tick <= last || seq <= sequence) return false;
            boolean eligible = serverGround != 0 && clientGround == serverGround && (value & ACTIVE) != 0;
            if (!eligible) {
                armedGround = 0;
                exitGround = 0;
            } else if ((value & SNEAK) == 0) {
                // An accepted release with the current server-issued ground token arms the next press.
                armedGround = serverGround;
            } else {
                if ((value & EXIT_PRESS) != 0 && armedGround == serverGround
                        && seq == sequence + 1 && fresh(tick) != 0 && (flags & SNEAK) == 0) {
                    exitGround = serverGround;
                    exitAt = tick;
                }
                armedGround = 0;
            }
            if ((value & ACTIVE) == 0) {
                boostArmed = false;
                boostPress = false;
            } else if ((value & BOOST_HELD) == 0) {
                boostArmed = true;
            } else {
                boostPress = (value & BOOST_PRESS) != 0 && boostArmed && seq == sequence + 1
                        && fresh(tick) != 0 && (flags & BOOST_HELD) == 0;
                if (boostPress) boostAt = tick;
                boostArmed = false;
            }
            sequence = seq;
            flags = value;
            last = tick;
            return true;
        }

        int fresh(long tick) {
            return last != Long.MIN_VALUE && tick >= last && tick - last <= FRESH_TICKS ? flags : 0;
        }

        boolean consumeExit(long tick, int groundSerial) {
            boolean requested = groundSerial != 0 && exitGround == groundSerial
                    && tick >= exitAt && tick - exitAt <= FRESH_TICKS;
            exitGround = 0;
            return requested;
        }

        boolean consumeBoost(long tick) {
            boolean requested = boostPress && tick >= boostAt && tick - boostAt <= FRESH_TICKS;
            boostPress = false;
            return requested;
        }
    }
}
