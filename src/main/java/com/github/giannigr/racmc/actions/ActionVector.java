package com.github.giannigr.racmc.actions;

/** Small headless vector type; action simulation does not load client or Minecraft classes. */
public record ActionVector(double x, double y, double z) {
    public static final ActionVector ZERO = new ActionVector(0, 0, 0);

    public ActionVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Non-finite action vector");
        }
    }

    public ActionVector add(ActionVector v) {
        return new ActionVector(x + v.x, y + v.y, z + v.z);
    }

    public ActionVector subtract(ActionVector v) {
        return new ActionVector(x - v.x, y - v.y, z - v.z);
    }

    public ActionVector multiply(double scalar) {
        return new ActionVector(x * scalar, y * scalar, z * scalar);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public ActionVector normalized() {
        double length = length();
        return length < 1.0e-9 ? ZERO : multiply(1 / length);
    }

    public ActionVector lerp(ActionVector to, double fraction) {
        return add(to.subtract(this).multiply(fraction));
    }
}
