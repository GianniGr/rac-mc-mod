package com.github.giannigr.racmc.actions;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The same 60Hz world/socket frames used by server delivery and real-data headless regressions. */
public final class WrenchSlamSampling {
    private WrenchSlamSampling() {}
    public record Actor(ActionVector position, float yaw, float scale, boolean crouching,
            boolean left, boolean slim) {}
    public record Pose(ActionVector hand, ActionVector head, ActionVector body, ActionRotation rotation,
            boolean active, boolean bodyContact, boolean grounded, WrenchActionState.ClipFrame frame) {}

    public static List<Pose> sample(WrenchSteveRig rig, int bodySequence, int childSequence,
            List<WrenchOriginalClock.Result> source, Actor before, Actor current) {
        List<Pose> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            double alpha = (i + 1.0) / source.size();
            Actor frame = new Actor(before.position.lerp(current.position, alpha),
                    before.yaw + (float) Math.IEEEremainder(current.yaw - before.yaw, 360) * (float) alpha,
                    current.scale, current.crouching, current.left, current.slim);
            result.add(at(rig, bodySequence, childSequence, source.get(i), frame));
        }
        return List.copyOf(result);
    }

    public static Pose at(WrenchSteveRig rig, int bodySequence, int childSequence,
            WrenchOriginalClock.Result source, Actor actor) {
        var frame = source.frame();
        var model = rig.sample(bodySequence, frame.seconds(), childSequence, frame.wrenchSeconds(),
                actor.left, actor.slim, frame.bodyBlendWeight(), actor.crouching);
        Matrix4f world = WrenchSteveRig.world(0, 0, 0, actor.yaw, actor.scale, actor.crouching);
        Matrix4f grip = new Matrix4f(world).mul(model.socket());
        Quaternionf q = grip.getUnnormalizedRotation(new Quaternionf()).normalize();
        var foot = new Matrix4f(world).mul(model.root()).transformPosition(
                new Vector3f(0, (actor.crouching ? 24.2F : 24) / 16,
                        actor.crouching ? 4.0F / 16 : 0));
        ActionVector body = actor.position.add(new ActionVector(foot.x,
                foot.y - 0.5 * actor.scale, foot.z));
        return new Pose(point(actor.position, grip), point(actor.position,
                new Matrix4f(world).mul(model.head())), body,
                new ActionRotation(q.x, q.y, q.z, q.w), source.active(), source.bodyContact(),
                source.grounded(), frame);
    }

    public static ActionVector tip(Pose pose) {
        return pose.head.subtract(pose.head.subtract(pose.hand).normalized().multiply(0.085));
    }

    public static boolean spheres(Pose pose, WrenchContact.Box target) {
        return pose.active && WrenchContact.sphereIntersects(tip(pose), 0.47, target)
                || pose.bodyContact && WrenchContact.sphereIntersects(pose.body, 0.4, target);
    }

    /** Single-target narrowphase; runtime separately chooses first line collision/occlusion. */
    public static boolean intersects(Pose previous, Pose current, WrenchContact.Box target) {
        if (spheres(current, target)) return true;
        if (current.active) {
            for (var ray : WrenchContact.sweep(previous.hand, previous.head, current.hand, current.head)) {
                if (Double.isFinite(WrenchContact.intersection(ray, target))) return true;
            }
        }
        return false;
    }

    private static ActionVector point(ActionVector origin, Matrix4f matrix) {
        Vector3f p = matrix.getTranslation(new Vector3f());
        return origin.add(new ActionVector(p.x, p.y, p.z));
    }
}
