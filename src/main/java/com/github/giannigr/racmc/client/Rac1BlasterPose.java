package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;

/**
 * The pose of the Blaster as it is held in the hand of a player.
 *
 * <p>The pose is built out of the imported sequences of the weapon alone. The weapon is held with
 * the sequence of the held weapon and fires with the sequence of the shot, and the two are
 * blended into each other.
 *
 * <p>The places of the vertices are the places of the game: the weapon keeps the size and the
 * proportions it was imported with, and the point of the game that holds it - the origin of the
 * mesh - stays where it is, so that the weapon hangs in the hand the way it hangs in the hand of
 * the hero in the game.
 *
 * <p>The class holds no Minecraft types, so the pose can be checked on a plain virtual machine.
 */
public final class Rac1BlasterPose {

    /**
     * The name of the sequence of the weapon that is played while it is held.
     *
     * <p>The game measures it in the code of the game: the weapon blends to its sequence 1 when
     * it is held and back to it when it stops firing.
     */
    public static final String HELD_SEQUENCE = "sequence_1";

    /**
     * The name of the sequence of the weapon that is played while it fires.
     *
     * <p>The game measures it in the code of the game: the weapon blends to its sequence 4 when
     * it fires.
     */
    public static final String FIRE_SEQUENCE = "sequence_4";

    /** The name of the first sequence of the weapon, which is used when a sequence is missing. */
    private static final String FIRST_SEQUENCE = "sequence_0";

    /** The places of the vertices of the weapon. */
    private float[] positions = new float[0];

    /** The normals of the vertices of the weapon. */
    private float[] normals = new float[0];

    /** The box around the weapon while it is held, measured over the keys of its sequences. */
    private float[] bounds;

    /** The weapon the pose was built for. */
    private Rac1AnimatedAsset source;

    /**
     * Builds the pose of the weapon.
     *
     * @param blaster the weapon of the game
     * @param weight the share of the pose of the shot, from {@code 0} to {@code 1}
     * @param fireTime the moment of the sequence of the shot in seconds
     */
    public void update(Rac1AnimatedAsset blaster, float weight, float fireTime) {
        if (blaster == null) {
            this.positions = new float[0];
            this.normals = new float[0];
            return;
        }
        if (this.source != blaster) {
            this.source = blaster;
            this.bounds = measure(blaster);
        }
        Rac1Animation held = sequence(blaster, HELD_SEQUENCE);
        if (held == null) {
            held = sequence(blaster, FIRST_SEQUENCE);
        }
        Rac1Animation fire = sequence(blaster, FIRE_SEQUENCE);
        if (fire == null) {
            fire = sequence(blaster, FIRST_SEQUENCE);
        }
        if (held == null || fire == null) {
            this.positions = new float[0];
            this.normals = new float[0];
            return;
        }
        int vertices = blaster.mesh().positions().length;
        float[] heldPositions = new float[vertices];
        float[] heldNormals = new float[blaster.mesh().normals().length];
        float[] firePositions = new float[vertices];
        float[] fireNormals = new float[blaster.mesh().normals().length];
        evaluate(blaster, held, 0.0f, heldPositions, heldNormals);
        evaluate(blaster, fire, fireTime, firePositions, fireNormals);
        this.positions = new float[vertices];
        this.normals = new float[heldNormals.length];
        if (weight <= 0.0f) {
            System.arraycopy(heldPositions, 0, this.positions, 0, vertices);
            System.arraycopy(heldNormals, 0, this.normals, 0, heldNormals.length);
        } else if (weight >= 1.0f) {
            System.arraycopy(firePositions, 0, this.positions, 0, vertices);
            System.arraycopy(fireNormals, 0, this.normals, 0, fireNormals.length);
        } else {
            blend(heldPositions, heldNormals, firePositions, fireNormals, weight, this.positions,
                    this.normals);
        }
    }

    /**
     * The places of the vertices of the weapon.
     *
     * @return the places, three floats each
     */
    public float[] positions() {
        return this.positions;
    }

    /**
     * The normals of the vertices of the weapon.
     *
     * @return the normals, three floats each
     */
    public float[] normals() {
        return this.normals;
    }

    /**
     * The box around the weapon while it is held.
     *
     * <p>The box is measured over the keys of the sequence of the held weapon and of the sequence
     * of the shot, so that the box holds the weapon in every moment of the shot.
     *
     * @return the smallest and the greatest place of every axis, or {@code null} when the weapon
     *         has no triangle
     */
    public float[] bounds() {
        return this.bounds;
    }

    /**
     * Measures the box around the weapon over the keys of its sequences.
     *
     * @param blaster the weapon of the game
     * @return the smallest and the greatest place of every axis, or {@code null} when the weapon
     *         has no triangle
     */
    private static float[] measure(Rac1AnimatedAsset blaster) {
        Rac1Animation held = sequence(blaster, HELD_SEQUENCE);
        if (held == null) {
            held = sequence(blaster, FIRST_SEQUENCE);
        }
        Rac1Animation fire = sequence(blaster, FIRE_SEQUENCE);
        if (fire == null) {
            fire = sequence(blaster, FIRST_SEQUENCE);
        }
        if (held == null || fire == null) {
            return null;
        }
        float[] box = null;
        for (Rac1Animation animation : new Rac1Animation[] { held, fire }) {
            for (float time : keyTimes(animation)) {
                float[] positions = new float[blaster.mesh().positions().length];
                float[] normals = new float[blaster.mesh().normals().length];
                evaluate(blaster, animation, time, positions, normals);
                box = grow(box, positions);
            }
        }
        return box;
    }

    /**
     * Poses the weapon with one of its sequences.
     *
     * @param blaster the weapon of the game
     * @param animation the sequence of the weapon
     * @param time the moment of the sequence in seconds
     * @param positions the place the moved places are written to
     * @param normals the place the moved normals are written to
     */
    private static void evaluate(Rac1AnimatedAsset blaster, Rac1Animation animation, float time,
                                 float[] positions, float[] normals) {
        float[] matrices = Rac1AnimationEvaluator.evaluate(blaster.skeleton(), animation, time,
                1.0f);
        Rac1AnimationEvaluator.pose(blaster.mesh(), matrices, positions, normals);
    }

    /**
     * Blends the pose of the held weapon into the pose of the shot.
     *
     * @param heldPositions the places of the held weapon
     * @param heldNormals the normals of the held weapon
     * @param firePositions the places of the weapon that fires
     * @param fireNormals the normals of the weapon that fires
     * @param weight the share of the shot, from {@code 0} to {@code 1}
     * @param positions the place the blended places are written to
     * @param normals the place the blended normals are written to
     */
    private static void blend(float[] heldPositions, float[] heldNormals, float[] firePositions,
                               float[] fireNormals, float weight, float[] positions,
                               float[] normals) {
        float rest = 1.0f - weight;
        for (int index = 0; index < positions.length; index++) {
            positions[index] = heldPositions[index] * rest + firePositions[index] * weight;
        }
        for (int index = 0; index + 2 < normals.length; index += 3) {
            float x = heldNormals[index] * rest + fireNormals[index] * weight;
            float y = heldNormals[index + 1] * rest + fireNormals[index + 1] * weight;
            float z = heldNormals[index + 2] * rest + fireNormals[index + 2] * weight;
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length > 0.0f) {
                x /= length;
                y /= length;
                z /= length;
            }
            normals[index] = x;
            normals[index + 1] = y;
            normals[index + 2] = z;
        }
    }

    /**
     * The times of the keys of a sequence.
     *
     * @param animation the sequence
     * @return the times of the keys in seconds
     */
    private static float[] keyTimes(Rac1Animation animation) {
        float[] times = animation.times();
        return times.length > 0 ? times : new float[] { 0.0f };
    }

    /**
     * Grows a box so that it holds a set of places.
     *
     * @param box the box, or {@code null} when there is none yet
     * @param positions the places
     * @return the box
     */
    private static float[] grow(float[] box, float[] positions) {
        float[] measured = new float[] { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        for (int index = 0; index + 2 < positions.length; index += 3) {
            measured[0] = Math.min(measured[0], positions[index]);
            measured[3] = Math.max(measured[3], positions[index]);
            measured[1] = Math.min(measured[1], positions[index + 1]);
            measured[4] = Math.max(measured[4], positions[index + 1]);
            measured[2] = Math.min(measured[2], positions[index + 2]);
            measured[5] = Math.max(measured[5], positions[index + 2]);
        }
        if (box == null) {
            return measured;
        }
        for (int axis = 0; axis < 3; axis++) {
            box[axis] = Math.min(box[axis], measured[axis]);
            box[axis + 3] = Math.max(box[axis + 3], measured[axis + 3]);
        }
        return box;
    }

    /**
     * The sequence of a weapon with the given name.
     *
     * @param asset the weapon
     * @param name the name of the sequence
     * @return the sequence, or {@code null} when the weapon has no such sequence
     */
    private static Rac1Animation sequence(Rac1AnimatedAsset asset, String name) {
        if (asset == null) {
            return null;
        }
        for (Rac1Animation animation : asset.animations()) {
            if (animation.name().equals(name)) {
                return animation;
            }
        }
        return null;
    }
}
