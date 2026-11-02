package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.cache.Rac1CacheContent;

/**
 * The pose of the demo of the game: the hero holds his Blaster and fires it.
 *
 * <p>The pose is built out of the imported assets only. The hero is posed with one of his own
 * imported animation sequences, the Blaster is posed with its own imported firing sequence, and the
 * Blaster is placed on the joint of the skeleton of the hero that the game hangs the items of the
 * hand on.
 *
 * <p>The game places the items of the hand with the pose of that joint and with a matrix of its
 * own for every item, which holds the grip of the item. That matrix lives in the code of the game
 * and not in the data of the disc, so the demo uses the pose of the joint alone.
 */
public final class Rac1BlasterDemo {

    /** The name of the sequence of the hero that the demo plays. */
    public static final String RATCHET_SEQUENCE = "ratchet_sequence_0";

    /**
     * The name of the sequence of the Blaster that the demo plays.
     *
     * <p>The firing sequence of the Blaster is the fourth one. The ReRAC project (ISC) measured
     * it in the code of the game: the weapon blends to its sequence 4 when it fires and back to
     * its sequence 1 when it is held, see {@code docs/plan/gadgets.md} of that project.
     */
    public static final String BLASTER_FIRE_SEQUENCE = "sequence_4";

    /** The seconds between two shots. */
    public static final float SHOT_INTERVAL = 2.5f;

    private float[] ratchetPositions = new float[0];
    private float[] ratchetNormals = new float[0];
    private float[] blasterPositions = new float[0];
    private float[] blasterNormals = new float[0];
    private float[] attachment = new float[16];

    /** The box around the model of the demo. */
    private float[] bounds;

    /**
     * Builds the pose of the demo at the given moment.
     *
     * @param content the content of the cache of the assets
     * @param time the moment of the demo in seconds
     */
    public void update(Rac1CacheContent content, float time) {
        Rac1AnimatedAsset hero = content.animatedAsset();
        Rac1AnimatedAsset blaster = content.blaster();

        Rac1Animation heroSequence = sequence(hero, RATCHET_SEQUENCE);
        if (heroSequence == null) {
            heroSequence = sequence(hero, "sequence_0");
        }
        if (heroSequence == null) {
            return;
        }
        float[] heroMatrices = Rac1AnimationEvaluator.evaluate(hero.skeleton(), heroSequence, time, 1.0f);
        ratchetPositions = new float[hero.mesh().positions().length];
        ratchetNormals = new float[hero.mesh().normals().length];
        Rac1AnimationEvaluator.pose(hero.mesh(), heroMatrices, ratchetPositions, ratchetNormals);

        // The item hangs on the joint of the hand. The game leaves the scales that only reach the
        // joint itself out of the pose of the chain.
        float[] chain = Rac1AnimationEvaluator.evaluateChain(hero.skeleton(), heroSequence, time);
        int joint = content.handJoint();
        attachment = new float[16];
        attachment[0] = 1.0f;
        attachment[5] = 1.0f;
        attachment[10] = 1.0f;
        attachment[15] = 1.0f;
        if (joint >= 0 && joint * 16 + 15 < chain.length) {
            // The place of the joint of the hand in the model of the hero is the place the game
            // hangs the item of the hand on, and the demo takes it from the data of the disc.
            attachment[12] = chain[joint * 16 + 12];
            attachment[13] = chain[joint * 16 + 13];
            attachment[14] = chain[joint * 16 + 14];
            // The game turns every item of the hand with a matrix of its own, which holds the
            // grip of the item. That matrix lives in the code of the game and not in the data of
            // the disc, so the demo cannot use it and does not guess it either.
            //
            // The weapons of the game are modelled in the frame of the hero: their barrel points
            // the way the hero faces and their back is up. The demo therefore turns the weapon
            // into the frame of the model of the hero and keeps the place of the joint of the
            // hand. The weapon therefore points the way the hero faces and follows the hand of
            // the hero while the animation runs.
        }

        Rac1Animation fireSequence = sequence(blaster, BLASTER_FIRE_SEQUENCE);
        if (fireSequence == null) {
            fireSequence = sequence(blaster, "sequence_0");
        }
        if (fireSequence == null) {
            return;
        }
        // The weapon fires over and over again, every few seconds.
        float shot = time % SHOT_INTERVAL;
        float duration = fireSequence.times().length > 0
                ? fireSequence.times()[fireSequence.times().length - 1] : 0.0f;
        float fireTime = duration > 0.0f ? shot % duration : 0.0f;
        float[] blasterMatrices = Rac1AnimationEvaluator.evaluate(blaster.skeleton(), fireSequence,
                fireTime, 1.0f);
        blasterPositions = new float[blaster.mesh().positions().length];
        blasterNormals = new float[blaster.mesh().normals().length];
        Rac1AnimationEvaluator.pose(blaster.mesh(), blasterMatrices, blasterPositions,
                blasterNormals);
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
        measure();
    }

    /**
     * Measures the model of the demo, so that the screen can draw it in the middle of its area.
     *
     * <p>The measurement holds the hero and the Blaster, because the Blaster is held in the hand
     * of the hero and is part of the picture.
     */
    private void measure() {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (float[] positions : new float[][] { ratchetPositions, blasterPositions }) {
            for (int index = 0; index + 2 < positions.length; index += 3) {
                minX = Math.min(minX, positions[index]);
                maxX = Math.max(maxX, positions[index]);
                minY = Math.min(minY, positions[index + 1]);
                maxY = Math.max(maxY, positions[index + 1]);
                minZ = Math.min(minZ, positions[index + 2]);
                maxZ = Math.max(maxZ, positions[index + 2]);
                any = true;
            }
        }
        bounds = any ? new float[] { minX, minY, minZ, maxX, maxY, maxZ } : null;
    }

    /**
     * The box around the model of the demo.
     *
     * @return the smallest and the greatest place of every axis, or {@code null} when there is
     *         no model yet
     */
    public float[] bounds() {
        return bounds;
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
