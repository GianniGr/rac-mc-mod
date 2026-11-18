package com.github.giannigr.racmc.item.weapon;

import java.util.Arrays;
import static com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/** Render-only rigid correction of a sampled animated ring onto the authoritative muzzle.
 * No rescale, animation key change, normal/deformation change or mandatory server media.
 * The gun body may translate around its animated barrel; this is not a fixed-grip animation.
 */
public final class AnimatedMuzzleAnchor {
    private final int[] vertices;
    public AnimatedMuzzleAnchor(int[] vertices) {
        if (vertices.length < 3 || Arrays.stream(vertices).anyMatch(v -> v < 0)
                || Arrays.stream(vertices).distinct().count() != vertices.length)
            throw new IllegalArgumentException("Invalid muzzle ring");
        this.vertices = vertices.clone();
    }
    public Vector correction(float[] posed, Vector authoritativeMuzzle) {
        double x = 0, y = 0, z = 0;
        for (int vertex : vertices) {
            int at = Math.multiplyExact(vertex, 3);
            if (at + 2 >= posed.length) throw new IllegalArgumentException("Muzzle vertex missing");
            x += posed[at]; y += posed[at + 1]; z += posed[at + 2];
        }
        return new Vector(authoritativeMuzzle.x() - x / vertices.length,
                authoritativeMuzzle.y() - y / vertices.length, authoritativeMuzzle.z() - z / vertices.length);
    }
}
