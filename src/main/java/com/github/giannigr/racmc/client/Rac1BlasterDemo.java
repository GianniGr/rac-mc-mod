package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.cache.Rac1CacheContent;

/**
 * The pose of the demo of the game: the hero holds his Blaster and fires it.
 *
 * <p>The pose is built out of the imported assets only. The hero is posed with one of his own
 * imported animation sequences, the Blaster is posed with its own imported sequences, and the
 * Blaster is placed on the joint of the skeleton of the hero that the game hangs the items of the
 * hand on.
 *
 * <p>The game places the items of the hand with the pose of the joint they hang on: the item
 * itself brings the place of its grip with it, so no matrix of its own is needed. The pose of the
 * joint is the pose of the chain of the joint, without the scales that only reach the joint
 * itself, because the game leaves those scales out of the place of the items of the hand.
 *
 * <p>The box around the model is measured over the whole cycle of the sequence of the hero and
 * over the whole firing sequence of the Blaster, so that the model stands still on the screen
 * while the demo runs. A box that is measured in every frame would move the model on the screen,
 * because the Blaster moves in the hand while it fires.
 */
public final class Rac1BlasterDemo {

    /**
     * The name of the sequence of the hero that the demo plays.
     *
     * <p>It is the stance of the hero with the weapon in his hand. The game measures it in the
     * definitions of its items: the Blaster names the sequence 56 as the stance of the hero, the
     * sequence 57 as the layer of the arm and the sequence 62 as the layer of the arm while he
     * crouches. The stance is the pose in which the hero holds the weapon the way the game shows
     * it.
     */
    public static final String RATCHET_SEQUENCE = "ratchet_sequence_56";

    /**
     * The name of the sequence of the Blaster that the demo plays while the weapon is held.
     *
     * <p>The game measures it in the code of the game: the weapon blends to its sequence 1 when
     * it is held and back to it when it stops firing. The sequence of the held weapon holds a
     * single key, so the weapon keeps still while it is held.
     */
    public static final String BLASTER_HELD_SEQUENCE = "sequence_1";

    /**
     * The name of the sequence of the Blaster that the demo plays while the weapon fires.
     *
     * <p>The game measures it in the code of the game: the weapon blends to its sequence 4 when
     * it fires. The sequence of the shot is played once, over its own length, and the weapon goes
     * back to the sequence of the held weapon afterwards.
     */
    public static final String BLASTER_FIRE_SEQUENCE = "sequence_4";

    /** The seconds between two shots. */
    public static final float SHOT_INTERVAL = 2.5f;

    private float[] ratchetPositions = new float[0];
    private float[] ratchetNormals = new float[0];
    private float[] blasterPositions = new float[0];
    private float[] blasterNormals = new float[0];
    private float[] attachment = new float[16];

    /** The box around the model of the demo, the same for every moment of the demo. */
    private float[] framing;

    /** The content of the cache of the assets, when the demo was built. */
    private Rac1CacheContent content;

    /**
     * Builds the pose of the demo at the given moment.
     *
     * @param content the content of the cache of the assets
     * @param time the moment of the demo in seconds
     */
    public void update(Rac1CacheContent content, float time) {
        this.content = content;
        Rac1AnimatedAsset hero = content.animatedAsset();
        Rac1AnimatedAsset blaster = content.blaster();

        Rac1Animation heroSequence = sequence(hero, RATCHET_SEQUENCE);
        if (heroSequence == null) {
            heroSequence = sequence(hero, "ratchet_sequence_0");
        }
        if (heroSequence == null) {
            heroSequence = sequence(hero, "sequence_0");
        }
        if (heroSequence == null) {
            return;
        }
        // The sequence of the hero repeats itself. The evaluator plays it from its last key
        // into its first key, so the time of the demo can be given to it as it is.
        float[] heroMatrices = Rac1AnimationEvaluator.evaluate(hero.skeleton(), heroSequence,
                time, 1.0f);
        ratchetPositions = new float[hero.mesh().positions().length];
        ratchetNormals = new float[hero.mesh().normals().length];
        Rac1AnimationEvaluator.pose(hero.mesh(), heroMatrices, ratchetPositions, ratchetNormals);

        // The item hangs on the joint of the hand. The game leaves the scales that only reach
        // the joint itself out of the pose of the chain, and it adds no matrix of its own: the
        // place of the grip of the item is the place of the joint.
        float[] chain = Rac1AnimationEvaluator.evaluateChain(hero.skeleton(), heroSequence,
                time);
        int joint = content.handJoint();
        attachment = identity();
        if (joint >= 0 && joint * 16 + 15 < chain.length) {
            System.arraycopy(chain, joint * 16, attachment, 0, 16);
        }

        Rac1Animation held = sequence(blaster, BLASTER_HELD_SEQUENCE);
        if (held == null) {
            held = sequence(blaster, "sequence_0");
        }
        Rac1Animation fire = sequence(blaster, BLASTER_FIRE_SEQUENCE);
        if (fire == null) {
            fire = sequence(blaster, "sequence_0");
        }
        if (held == null || fire == null) {
            return;
        }

        // The weapon is held with the sequence of the held weapon and fires with the sequence of
        // the shot. The shot is played once, over the length of the sequence of the shot, and the
        // weapon is blended into the shot and out of it again.
        float shot = time % SHOT_INTERVAL;
        float fireDuration = duration(fire);
        float blend = blend(fire);
        float weight = 0.0f;
        float fireTime = 0.0f;
        if (fireDuration > 0.0f && shot < fireDuration + blend) {
            fireTime = shot;
            weight = 1.0f;
            if (shot < blend) {
                weight = blend > 0.0f ? shot / blend : 1.0f;
            } else if (shot > fireDuration) {
                weight = blend > 0.0f ? (fireDuration + blend - shot) / blend : 0.0f;
            }
        }

        blasterPositions = new float[blaster.mesh().positions().length];
        blasterNormals = new float[blaster.mesh().normals().length];
        float[] heldPositions = new float[blasterPositions.length];
        float[] heldNormals = new float[blasterNormals.length];
        evaluate(blaster, held, time, heldPositions, heldNormals);
        if (weight <= 0.0f) {
            System.arraycopy(heldPositions, 0, blasterPositions, 0, blasterPositions.length);
            System.arraycopy(heldNormals, 0, blasterNormals, 0, blasterNormals.length);
        } else if (weight >= 1.0f) {
            evaluate(blaster, fire, fireTime, blasterPositions, blasterNormals);
        } else {
            float[] firePositions = new float[blasterPositions.length];
            float[] fireNormals = new float[blasterNormals.length];
            evaluate(blaster, fire, fireTime, firePositions, fireNormals);
            blend(heldPositions, heldNormals, firePositions, fireNormals, weight,
                    blasterPositions, blasterNormals);
        }

        // The weapon is placed on the joint of the hand of the hero.
        for (int vertex = 0; vertex < blasterPositions.length / 3; vertex++) {
            float x = blasterPositions[vertex * 3];
            float y = blasterPositions[vertex * 3 + 1];
            float z = blasterPositions[vertex * 3 + 2];
            blasterPositions[vertex * 3] = attachment[0] * x + attachment[4] * y
                    + attachment[8] * z + attachment[12];
            blasterPositions[vertex * 3 + 1] = attachment[1] * x + attachment[5] * y
                    + attachment[9] * z + attachment[13];
            blasterPositions[vertex * 3 + 2] = attachment[2] * x + attachment[6] * y
                    + attachment[10] * z + attachment[14];
            float nx = blasterNormals[vertex * 3];
            float ny = blasterNormals[vertex * 3 + 1];
            float nz = blasterNormals[vertex * 3 + 2];
            blasterNormals[vertex * 3] = attachment[0] * nx + attachment[4] * ny
                    + attachment[8] * nz;
            blasterNormals[vertex * 3 + 1] = attachment[1] * nx + attachment[5] * ny
                    + attachment[9] * nz;
            blasterNormals[vertex * 3 + 2] = attachment[2] * nx + attachment[6] * ny
                    + attachment[10] * nz;
        }
    }

    /**
     * Poses the weapon of the game with one of its sequences.
     *
     * @param blaster the weapon
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
     * The length of a sequence in seconds.
     *
     * @param animation the sequence
     * @return the time of its last key, or {@code 0} when it has no key
     */
    private static float duration(Rac1Animation animation) {
        float[] times = animation.times();
        return times.length > 0 ? times[times.length - 1] : 0.0f;
    }

    /**
     * The length of the blend between the held weapon and the weapon that fires.
     *
     * <p>The blend is as long as the first interval of the sequence of the shot. The game blends
     * the sequence of an item over a few ticks, and the first interval of the sequence of the shot
     * is the length of such a blend in the data of the game.
     *
     * @param animation the sequence of the shot
     * @return the length of the blend in seconds
     */
    private static float blend(Rac1Animation animation) {
        float[] times = animation.times();
        if (times.length < 2) {
            return 0.0f;
        }
        float length = times[1] - times[0];
        return length > 0.0f ? length : 0.0f;
    }

    /**
     * The box around the model of the demo, the same for every moment of the demo.
     *
     * <p>The box is measured over the whole cycle of the sequence of the hero and over the whole
     * firing sequence of the Blaster, so that the model stands still on the screen while the
     * demo runs.
     *
     * @return the smallest and the greatest place of every axis, or {@code null} when there is no
     *         model yet
     */
    public float[] framing() {
        if (this.framing == null && this.content != null) {
            this.framing = measure();
        }
        return this.framing;
    }

    /**
     * Measures the box around the model of the demo.
     *
     * @return the smallest and the greatest place of every axis
     */
    private float[] measure() {
        Rac1AnimatedAsset hero = this.content.animatedAsset();
        Rac1AnimatedAsset blaster = this.content.blaster();
        Rac1Animation heroSequence = sequence(hero, RATCHET_SEQUENCE);
        if (heroSequence == null) {
            heroSequence = sequence(hero, "ratchet_sequence_0");
        }
        if (heroSequence == null) {
            heroSequence = sequence(hero, "sequence_0");
        }
        Rac1Animation held = sequence(blaster, BLASTER_HELD_SEQUENCE);
        if (held == null) {
            held = sequence(blaster, "sequence_0");
        }
        Rac1Animation fire = sequence(blaster, BLASTER_FIRE_SEQUENCE);
        if (fire == null) {
            fire = sequence(blaster, "sequence_0");
        }
        if (heroSequence == null || held == null || fire == null) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        // The box of the Blaster in its own frame, over the keys of the sequence of the shot and
        // over the keys of the sequence of the held weapon.
        float[] blasterBox = new float[] { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        for (Rac1Animation animation : new Rac1Animation[] { held, fire }) {
            for (float time : keyTimes(animation)) {
                float[] positions = new float[blaster.mesh().positions().length];
                float[] normals = new float[blaster.mesh().normals().length];
                evaluate(blaster, animation, time, positions, normals);
                blasterBox = grow(blasterBox, positions);
            }
        }

        // The box of the hero and the box of the Blaster, which hangs on his hand, over the
        // whole cycle of his sequence.
        for (float time : keyTimes(heroSequence)) {
            float[] matrices = Rac1AnimationEvaluator.evaluate(hero.skeleton(), heroSequence,
                    time, 1.0f);
            float[] positions = new float[hero.mesh().positions().length];
            float[] normals = new float[hero.mesh().normals().length];
            Rac1AnimationEvaluator.pose(hero.mesh(), matrices, positions, normals);
            minX = Math.min(minX, box(positions)[0]);
            minY = Math.min(minY, box(positions)[1]);
            minZ = Math.min(minZ, box(positions)[2]);
            maxX = Math.max(maxX, box(positions)[3]);
            maxY = Math.max(maxY, box(positions)[4]);
            maxZ = Math.max(maxZ, box(positions)[5]);

            float[] chain = Rac1AnimationEvaluator.evaluateChain(hero.skeleton(), heroSequence,
                    time);
            int joint = this.content.handJoint();
            if (joint >= 0 && joint * 16 + 15 < chain.length) {
                float[] attachment = new float[16];
                System.arraycopy(chain, joint * 16, attachment, 0, 16);
                float[] weapon = new float[24];
                for (int corner = 0; corner < 8; corner++) {
                    float x = (corner & 1) == 0 ? blasterBox[0] : blasterBox[3];
                    float y = (corner & 2) == 0 ? blasterBox[1] : blasterBox[4];
                    float z = (corner & 4) == 0 ? blasterBox[2] : blasterBox[5];
                    weapon[corner * 3] = attachment[0] * x + attachment[4] * y + attachment[8] * z
                            + attachment[12];
                    weapon[corner * 3 + 1] = attachment[1] * x + attachment[5] * y
                            + attachment[9] * z + attachment[13];
                    weapon[corner * 3 + 2] = attachment[2] * x + attachment[6] * y
                            + attachment[10] * z + attachment[14];
                }
                minX = Math.min(minX, box(weapon)[0]);
                minY = Math.min(minY, box(weapon)[1]);
                minZ = Math.min(minZ, box(weapon)[2]);
                maxX = Math.max(maxX, box(weapon)[3]);
                maxY = Math.max(maxY, box(weapon)[4]);
                maxZ = Math.max(maxZ, box(weapon)[5]);
            }
        }
        if (minX > maxX) {
            return null;
        }
        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * The times of the keys of a sequence.
     *
     * <p>The box around the model is measured at the keys of the sequences, so that the pose of
     * the model is measured everywhere it can be.
     *
     * @param animation the sequence
     * @return the times of the keys in seconds
     */
    private static float[] keyTimes(Rac1Animation animation) {
        float[] times = animation.times();
        if (times.length == 0) {
            return new float[] { 0.0f };
        }
        return times;
    }

    /**
     * The box around a set of places.
     *
     * @param positions the places
     * @return the smallest and the greatest place of every axis
     */
    private static float[] box(float[] positions) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int index = 0; index + 2 < positions.length; index += 3) {
            minX = Math.min(minX, positions[index]);
            maxX = Math.max(maxX, positions[index]);
            minY = Math.min(minY, positions[index + 1]);
            maxY = Math.max(maxY, positions[index + 1]);
            minZ = Math.min(minZ, positions[index + 2]);
            maxZ = Math.max(maxZ, positions[index + 2]);
        }
        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * Grows a box so that it holds a set of places.
     *
     * @param box the box
     * @param positions the places
     * @return the box
     */
    private static float[] grow(float[] box, float[] positions) {
        float[] measured = box(positions);
        for (int axis = 0; axis < 3; axis++) {
            box[axis] = Math.min(box[axis], measured[axis]);
            box[axis + 3] = Math.max(box[axis + 3], measured[axis + 3]);
        }
        return box;
    }

    /**
     * The places of the vertices of the hero.
     *
     * @return the places, three floats each
     */
    public float[] ratchetPositions() {
        return ratchetPositions;
    }

    /**
     * The normals of the vertices of the hero.
     *
     * @return the normals, three floats each
     */
    public float[] ratchetNormals() {
        return ratchetNormals;
    }

    /**
     * The places of the vertices of the Blaster, already placed on the hand of the hero.
     *
     * @return the places, three floats each
     */
    public float[] blasterPositions() {
        return blasterPositions;
    }

    /**
     * The normals of the vertices of the Blaster.
     *
     * @return the normals, three floats each
     */
    public float[] blasterNormals() {
        return blasterNormals;
    }

    /**
     * The matrix that places the Blaster on the hand of the hero.
     *
     * @return the matrix, sixteen floats in the order of its columns
     */
    public float[] attachment() {
        return attachment;
    }

    /**
     * The matrix that does nothing.
     *
     * @return the matrix, sixteen floats in the order of its columns
     */
    private static float[] identity() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    /**
     * The sequence of an asset with the given name.
     *
     * @param asset the asset
     * @param name the name of the sequence
     * @return the sequence, or {@code null} when the asset has no such sequence
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
