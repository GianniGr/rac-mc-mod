package com.github.giannigr.racmc.client.bomb;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

/** Camera-space carry layout, scaled by the active world projection, not the body rig. */
final class BombGloveFirstPersonFrame {
    private BombGloveFirstPersonFrame() {}

    record Lens(double x, double y, double shiftX, double shiftY) {
        static Lens of(Matrix4fc m) {
            if (!(m.m00() > 0) || !(m.m11() > 0) || !Float.isFinite(m.m00()) || !Float.isFinite(m.m11())
                    || !Float.isFinite(m.m20()) || !Float.isFinite(m.m21())) return new Lens(1, 1.43, 0, 0);
            return new Lens(m.m00(), m.m11(), m.m20(), m.m21());
        }

        Vec3 at(double ndcX, double ndcY, double depth) {
            return new Vec3((ndcX + shiftX) * depth / x, (ndcY + shiftY) * depth / y, -depth);
        }
    }

    record Frame(Vec3 palm, Quaternionf rotation) {}

    static Frame sample(Lens lens, boolean right, Vec3 release, BombGloveStevePose.Motion motion) {
        int side = right ? 1 : -1;
        // Keep roughly the same apparent bomb and glove size at normal and wide FOV.
        double depth = Math.max(1.23, lens.y * (.77 - .045 * motion.back() + .04 * motion.follow()) + .16);
        double x = side * (.53 + .11 * motion.back() - .11 * motion.follow());
        double y = -.40 - .035 * motion.back() + .15 * motion.follow();
        var composed = lens.at(x, y, depth);
        // Project an off-edge elbow, closer to the eye than the glove. The wrist
        // settles below it in camera units, while foreshortening makes the arm
        // enter diagonally from the low outside edge rather than from above.
        var elbow = lens.at(side * .93, -.71, depth * .52);
        // Converge only at the end of wind-up. The release is still the actual
        // outgoing world point, even if it is outside the view at steep pitch.
        double releasedY = release.z < -.05 ? lens.y * release.y / -release.z - lens.shiftY : 2;
        // An off-edge real socket cannot be reframed without moving the flight.
        // Hold the usable layout longer in that case, then join exactly once.
        boolean inCarryLane = releasedY > -.62 && releasedY < -.12;
        double joinStart = inCarryLane ? .54 : .78;
        double t = Math.clamp((motion.toRelease() - joinStart) / (1 - joinStart), 0, 1);
        double join = t * t * (3 - 2 * t);
        var palm = join == 1 ? release : composed.lerp(release, join);
        // Pitch the arm across the view as it reaches the closer world socket:
        // pointing the long casing toward the lens there could cross the eye plane.
        double reach = Math.clamp((1.04 + palm.z) / .34, 0, 1);
        double turn = reach * reach * (3 - 2 * reach);
        var line = composed.subtract(elbow).normalize().lerp(new Vec3(-.88 * side, .44, -.025).normalize(), turn);
        var roll = BombGloveStevePose.frame(line, new Vec3(1, 0, 0));
        // Only a small wrist turn around the arm; no camera-pitch or body-yaw-driven barrel roll.
        roll.rotateY((float) (side * (.21 * motion.back() + .08 * motion.toRelease() - .14 * motion.follow())));
        return new Frame(palm, roll);
    }
}
