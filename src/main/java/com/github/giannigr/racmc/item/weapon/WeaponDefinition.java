package com.github.giannigr.racmc.item.weapon;

import java.util.Objects;

/**
 * Immutable content data, in imported mesh blocks and degrees. Camera/GUI fitting is deliberately
 * not physical scale. Ammo identity names a policy, not an inventory implementation or shop price.
 */
public record WeaponDefinition(String id, String sourceAsset, Vector grip, Vector forward,
                               Vector up, Vector muzzle, double worldScale, Vector itemRotation,
                               int heldSequence, int fireSequence, int shotIntervalTicks,
                               String ammoPolicy, String projectilePolicy, FireMode fireMode,
                               Presentation firstPerson, Presentation gui) {
    public enum FireMode { HELD }
    /** Presentation translation is in blocks, NOT the JSON's sixteenth-block input units. */
    public record Presentation(Vector rotation, Vector translation, double scale) {
        public Presentation {
            Objects.requireNonNull(rotation); Objects.requireNonNull(translation);
            if (!Double.isFinite(scale) || scale <= 0 || scale > 16)
                throw new IllegalArgumentException("Invalid presentation scale");
        }
    }
    public WeaponDefinition {
        for (String value : new String[]{id, sourceAsset, ammoPolicy, projectilePolicy})
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing weapon identity");
        Objects.requireNonNull(grip);
        Objects.requireNonNull(muzzle);
        Objects.requireNonNull(itemRotation);
        Objects.requireNonNull(forward);
        Objects.requireNonNull(up);
        Objects.requireNonNull(fireMode);
        Objects.requireNonNull(firstPerson);
        Objects.requireNonNull(gui);
        if (Math.abs(forward.lengthSquared() - 1) > 1e-6
                || Math.abs(up.lengthSquared() - 1) > 1e-6
                || Math.abs(forward.dot(up)) > 1e-6)
            throw new IllegalArgumentException("Weapon axes must be orthonormal");
        if (!Double.isFinite(worldScale) || worldScale <= 0 || worldScale > 16
                || heldSequence < 0 || fireSequence < 0 || shotIntervalTicks < 1)
            throw new IllegalArgumentException("Invalid weapon scale, animation or cadence");
    }

    public record Vector(double x, double y, double z) {
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Nonfinite weapon vector");
        }
        public double lengthSquared() { return dot(this); }
        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
    }
}
