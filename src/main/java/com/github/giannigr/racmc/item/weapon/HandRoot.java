package com.github.giannigr.racmc.item.weapon;

import java.util.List;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/**
 * Resolved vanilla renderer root, in source pose-stack order (rightmost operation acts first).
 * This is server-derived local math, never a client-supplied matrix or coordinate protocol.
 * Explicit operations preserve pitch/yaw/roll and translations that cannot be one yaw angle.
 */
public record HandRoot(List<Step> steps) {
    public enum Kind { X, Y, Z, TRANSLATE }
    public record Step(Kind kind, double degrees, Vector translation) {
        public Step {
            if (kind == null || translation == null || !Double.isFinite(degrees))
                throw new IllegalArgumentException("Invalid hand root operation");
        }
        public static Step x(double degrees) { return rotate(Kind.X, degrees); }
        public static Step y(double degrees) { return rotate(Kind.Y, degrees); }
        public static Step z(double degrees) { return rotate(Kind.Z, degrees); }
        private static Step rotate(Kind kind, double degrees) {
            return new Step(kind, degrees, new Vector(0, 0, 0));
        }
        public static Step translate(double x, double y, double z) {
            return new Step(Kind.TRANSLATE, 0, new Vector(x, y, z));
        }
    }
    public HandRoot {
        steps = List.copyOf(steps);
        if (steps.size() > 12) throw new IllegalArgumentException("Hand root is not a bounded player chain");
    }
    public static HandRoot yaw(double bodyYaw) { return new HandRoot(List.of(Step.y(180 - bodyYaw))); }
    Vector apply(Vector point) {
        double[] p = {point.x(), point.y(), point.z()};
        for (int index = steps.size() - 1; index >= 0; index--) {
            var step = steps.get(index);
            if (step.kind() == Kind.TRANSLATE) {
                p[0] += step.translation().x(); p[1] += step.translation().y(); p[2] += step.translation().z();
            } else HandTransform.rotate(p, step.kind().ordinal(), step.degrees());
        }
        return new Vector(p[0], p[1], p[2]);
    }
}
