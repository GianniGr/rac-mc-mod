package com.github.giannigr.racmc.item.weapon;

import java.util.Objects;

/** Bounded synchronous server-tick admission. Preparation/obstruction precedes payment;
 * rejected entity admission refunds the reservation; feedback runs only after commit.
 * This is not an arbitrary-plugin or hard-crash transaction protocol. */
public final class FireService {
    private FireService() {}

    public interface AmmoReservation {
        void commit();
        void refund();
    }

    @FunctionalInterface
    public interface AmmoPolicy {
        /** Null means unavailable. A successful reservation removes exactly the price now. */
        AmmoReservation reserve();
    }

    public interface ProjectileFactory<P> {
        /** Construct and validate an unadmitted projectile; null means obstruction/rejection. */
        P prepare();
        boolean admit(P projectile);
        void discard(P projectile);
    }

    public static <P> boolean fire(AmmoPolicy ammo, ProjectileFactory<P> projectiles,
                                   java.util.function.Consumer<P> accepted) {
        Objects.requireNonNull(ammo);
        Objects.requireNonNull(projectiles);
        Objects.requireNonNull(accepted);
        P projectile = projectiles.prepare();
        if (projectile == null) return false;
        AmmoReservation reservation = ammo.reserve();
        if (reservation == null) {
            projectiles.discard(projectile);
            return false;
        }
        boolean admitted = false;
        try {
            admitted = projectiles.admit(projectile);
            if (!admitted) return false;
            reservation.commit();
        } finally {
            if (!admitted) {
                projectiles.discard(projectile);
                reservation.refund();
            }
        }
        accepted.accept(projectile);
        return true;
    }
}
