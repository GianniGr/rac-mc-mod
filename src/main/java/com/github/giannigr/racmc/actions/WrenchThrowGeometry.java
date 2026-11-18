package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared local mesh anchor -> actual server-owned detached transform, including final catch. */
public final class WrenchThrowGeometry {
    private WrenchThrowGeometry() {}

    public record Query<T>(ActionVector center, List<T> candidates) {}

    /** Candidate presence, not damage acceptance, selects the original list1/list0 fallback. */
    public static <T> Query<T> query(ActionVector primary, Supplier<ActionVector> fallback,
            Function<ActionVector, List<T>> sphere) {
        List<T> candidates = sphere.apply(primary);
        if (!candidates.isEmpty()) return new Query<>(primary, candidates);
        ActionVector center = fallback.get();
        return new Query<>(center, sphere.apply(center));
    }

    public static ActionVector anchor(Rac1AnimatedAsset asset, WrenchActionState.FlightStep step,
            float playerScale, int list, boolean previous) {
        var rotation = previous ? step.fromRotation() : step.toRotation();
        var position = previous ? step.from() : step.to();
        int sequence = previous ? step.fromSequence() : step.sequence();
        double blend = previous ? step.fromBlend() : step.blend();
        double spin = previous ? step.fromSpin() : step.toSpin();
        Matrix4f root = new Matrix4f().rotate(new Quaternionf(rotation.x(), rotation.y(),
                rotation.z(), rotation.w())).scale(0.9375F * playerScale).rotateY((float) spin);
        Vector3f p = root.mul(WrenchActionGeometry.anchor(asset, sequence, list, blend))
                .getTranslation(new Vector3f());
        return position.add(new ActionVector(p.x, p.y, p.z));
    }

    /** Derived from the implemented original outward law, not a guessed remote targeting range. */
    public static double outwardTravelBudget() {
        double speed = 23, deceleration = 0, distance = 0;
        for (int update = 0; speed > 0 && update < 120; update++) {
            distance += speed / 60;
            deceleration += 170.0 / (60 * 60);
            speed = Math.max(0, speed - deceleration);
        }
        return distance;
    }
}
