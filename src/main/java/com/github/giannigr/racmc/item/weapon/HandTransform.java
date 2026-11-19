package com.github.giannigr.racmc.item.weapon;

import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** MC1.21.1/NeoForge21.1.265 ItemInHandLayer + ModelPart chain, column-vector convention.
 * Cube centering -.5 and imported drawing +.5 cancel exactly once before this local point.
 * No camera projection or GUI fitting belongs here. */
public final class HandTransform {
    private HandTransform() {}

    public static Vector muzzle(WeaponDefinition weapon, HandPoseSnapshot pose) {
        return point(weapon, pose, weapon.muzzle());
    }

    public static Vector point(WeaponDefinition weapon, HandPoseSnapshot pose, Vector local) {
        return worldPoint(pose, modelPoint(weapon, pose, local));
    }

    public static Vector modelPoint(WeaponDefinition weapon, HandPoseSnapshot pose, Vector local) {
        double[] p = {(local.x() - weapon.grip().x()) * weapon.worldScale(),
                (local.y() - weapon.grip().y()) * weapon.worldScale(),
                (local.z() - weapon.grip().z()) * weapon.worldScale()};
        var rotation = weapon.itemRotation(pose.left());
        // ItemTransform rotationXYZ; mirrored Y/Z for physical LEFT.
        rotate(p, 2, pose.left() ? -rotation.z() : rotation.z());
        rotate(p, 1, pose.left() ? -rotation.y() : rotation.y());
        rotate(p, 0, rotation.x());
        p[0] += (pose.left() ? -1 : 1) / 16.0;
        p[1] += .125;
        p[2] -= .625;
        rotate(p, 1, 180);
        rotate(p, 0, -90);
        p[0] *= pose.armScale().x();
        p[1] *= pose.armScale().y();
        p[2] *= pose.armScale().z();
        // ModelPart rotationZYX: point application X, then Y, then Z.
        rotate(p, 0, pose.armRotation().x());
        rotate(p, 1, pose.armRotation().y());
        rotate(p, 2, pose.armRotation().z());
        p[0] += pose.pivot().x() / 16;
        p[1] += pose.pivot().y() / 16;
        p[2] += pose.pivot().z() / 16;
        return new Vector(p[0], p[1], p[2]);
    }

    public static Vector worldPoint(HandPoseSnapshot pose, Vector model) {
        double[] p = {model.x(), model.y(), model.z()};
        p[1] -= pose.modelHeight();
        p[0] *= -pose.playerScale();
        p[1] *= -pose.playerScale();
        p[2] *= pose.playerScale();
        var rooted = pose.root().apply(new Vector(p[0], p[1], p[2]));
        return new Vector(pose.position().x() + rooted.x() * pose.entityScale(),
                pose.position().y() + rooted.y() * pose.entityScale(),
                pose.position().z() + rooted.z() * pose.entityScale());
    }

    /** Convert a known model-space pivot displacement back into this item's local frame.
     * Used only for vanilla's asynchronous skin-model fallback: no world/camera fitting.
     */
    public static Vector localForModelDisplacement(WeaponDefinition weapon, HandPoseSnapshot pose, Vector delta) {
        var origin = modelPoint(weapon, pose, new Vector(0, 0, 0));
        var x = modelPoint(weapon, pose, new Vector(1, 0, 0));
        var y = modelPoint(weapon, pose, new Vector(0, 1, 0));
        var z = modelPoint(weapon, pose, new Vector(0, 0, 1));
        var basis = new org.joml.Matrix3d()
                .setColumn(0, new org.joml.Vector3d(x.x() - origin.x(), x.y() - origin.y(), x.z() - origin.z()))
                .setColumn(1, new org.joml.Vector3d(y.x() - origin.x(), y.y() - origin.y(), y.z() - origin.z()))
                .setColumn(2, new org.joml.Vector3d(z.x() - origin.x(), z.y() - origin.y(), z.z() - origin.z()));
        if (Math.abs(basis.determinant()) < 1e-12) throw new IllegalArgumentException("Singular item hand frame");
        var local = basis.invert().transform(new org.joml.Vector3d(delta.x(), delta.y(), delta.z()));
        return new Vector(local.x, local.y, local.z);
    }

    static void rotate(double[] p, int axis, double degrees) {
        double angle = Math.toRadians(degrees), c = Math.cos(angle), s = Math.sin(angle);
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        double x = p[a], y = p[b];
        p[a] = c * x - s * y;
        p[b] = s * x + c * y;
    }
}
