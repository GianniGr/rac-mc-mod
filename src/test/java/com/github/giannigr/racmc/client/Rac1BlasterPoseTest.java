package com.github.giannigr.racmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1Texture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the pose of the Blaster as it is held in the hand.
 *
 * <p>The assets of the checks are built by hand: a weapon with three joints and two sequences,
 * one for the pose it is held in and one for the shot. No data of the game is used anywhere.
 */
class Rac1BlasterPoseTest {

    /** The number of the joints of the skeleton of the checks. */
    private static final int JOINTS = 3;

    /** The number of the joint of the hand of the checks. */
    private static final int HAND = 1;

    /** The length of the sequence of the shot of the checks, in seconds. */
    private static final float SHOT = 0.3f;

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-4f;

    @Test
    @DisplayName("The weapon is held with the sequence of the held weapon")
    void weaponIsHeldWithTheHeldSequence() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(weapon(), 0.0f, 0.0f);
        float[] expected = evaluate("sequence_1", 0.0f);
        assertEquals(expected.length, pose.positions().length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], pose.positions()[index], TOLERANCE);
        }
    }

    @Test
    @DisplayName("The weapon fires with the sequence of the shot")
    void weaponFiresWithTheShotSequence() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(weapon(), 1.0f, SHOT / 3.0f);
        float[] expected = evaluate("sequence_4", SHOT / 3.0f);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], pose.positions()[index], TOLERANCE);
        }
    }

    @Test
    @DisplayName("The weapon is blended between the two sequences")
    void weaponIsBlendedBetweenTheSequences() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(weapon(), 0.5f, SHOT / 3.0f);
        float[] held = evaluate("sequence_1", 0.0f);
        float[] fire = evaluate("sequence_4", SHOT / 3.0f);
        for (int index = 0; index < held.length; index++) {
            float expected = (held[index] + fire[index]) * 0.5f;
            assertEquals(expected, pose.positions()[index], TOLERANCE);
        }
    }

    @Test
    @DisplayName("The weapon keeps the size it was imported with")
    void weaponKeepsItsOwnSize() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(weapon(), 0.0f, 0.0f);
        float[] bounds = pose.bounds();
        assertNotNull(bounds);
        assertEquals(6, bounds.length);
        // The weapon of the checks is measured over the keys of both of its sequences, so that
        // the box holds the weapon in every moment of the shot.
        float[] expected = box();
        for (int index = 0; index < 6; index++) {
            assertEquals(expected[index], bounds[index], TOLERANCE);
        }
    }

    @Test
    @DisplayName("The size of the weapon does not change while it fires")
    void sizeOfTheWeaponDoesNotChange() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(weapon(), 0.0f, 0.0f);
        float[] bounds = pose.bounds();
        pose.update(weapon(), 1.0f, SHOT / 3.0f);
        assertEquals(6, bounds.length);
        for (int index = 0; index < 6; index++) {
            assertEquals(bounds[index], pose.bounds()[index], TOLERANCE);
        }
    }

    @Test
    @DisplayName("The weapon of a game without sequences is not drawn")
    void weaponWithoutSequencesIsNotDrawn() {
        Rac1BlasterPose pose = new Rac1BlasterPose();
        pose.update(null, 0.0f, 0.0f);
        assertEquals(0, pose.positions().length);
        assertEquals(0, pose.normals().length);
    }

    /**
     * The places of the vertices of the weapon with one of its sequences.
     *
     * @param name the name of the sequence
     * @param time the moment of the sequence in seconds
     * @return the places, three floats each
     */
    private static float[] evaluate(String name, float time) {
        Rac1AnimatedAsset asset = weapon();
        Rac1Animation animation = null;
        for (Rac1Animation candidate : asset.animations()) {
            if (candidate.name().equals(name)) {
                animation = candidate;
            }
        }
        float[] matrices = Rac1AnimationEvaluator.evaluate(asset.skeleton(), animation, time, 1.0f);
        float[] positions = new float[asset.mesh().positions().length];
        float[] normals = new float[asset.mesh().normals().length];
        Rac1AnimationEvaluator.pose(asset.mesh(), matrices, positions, normals);
        return positions;
    }

    /**
     * The box around the weapon over the keys of both of its sequences.
     *
     * @return the smallest and the greatest place of every axis
     */
    private static float[] box() {
        float[] box = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE };
        for (float[] positions : new float[][] { evaluate("sequence_1", 0.0f),
                evaluate("sequence_4", 0.0f), evaluate("sequence_4", SHOT / 3.0f),
                evaluate("sequence_4", 2.0f * SHOT / 3.0f) }) {
            for (int index = 0; index + 2 < positions.length; index += 3) {
                box[0] = Math.min(box[0], positions[index]);
                box[3] = Math.max(box[3], positions[index]);
                box[1] = Math.min(box[1], positions[index + 1]);
                box[4] = Math.max(box[4], positions[index + 1]);
                box[2] = Math.min(box[2], positions[index + 2]);
                box[5] = Math.max(box[5], positions[index + 2]);
            }
        }
        return box;
    }

    /**
     * The skeleton of the checks: the first joint stands in the middle, the second one is the hand
     * of the hero and is a child of the first one, and the third one is a child of the first one
     * as well.
     *
     * @return the skeleton
     */
    private static Rac1Skeleton skeleton() {
        float[] inverseBind = new float[JOINTS * 16];
        for (int joint = 0; joint < JOINTS; joint++) {
            inverseBind[joint * 16] = 1.0f;
            inverseBind[joint * 16 + 5] = 1.0f;
            inverseBind[joint * 16 + 10] = 1.0f;
            inverseBind[joint * 16 + 15] = 1.0f;
        }
        inverseBind[16 + 30] = -1.0f;
        inverseBind[32 + 12] = -1.0f;
        int[] parents = { -1, 0, 0 };
        float[] rest = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f };
        return new Rac1Skeleton(inverseBind, parents, rest);
    }

    /**
     * The mesh of the checks: three vertices, one that follows the first joint, one that follows
     * the joint of the hand and one that follows the third joint.
     *
     * @return the mesh
     */
    private static Rac1SkinnedMesh mesh() {
        float[] positions = { 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.5f, 0.0f, 0.0f };
        float[] normals = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };
        float[] uvs = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };
        int[] indices = { 0, 1, 2 };
        int[] materials = { 0 };
        int[] joints = { 0, 0, 0, 1, 0, 0, 2, 0, 0 };
        float[] weights = { 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f };
        return new Rac1SkinnedMesh(positions, normals, uvs, indices, materials, joints, weights);
    }

    /**
     * A sequence that turns a joint of the skeleton of the checks.
     *
     * @param name the name of the sequence
     * @param times the times of the keys in seconds
     * @param turns the turn of the joint of every key, in degrees
     * @param joint the number of the joint that is turned
     * @param loop whether the sequence repeats itself
     * @return the sequence
     */
    private static Rac1Animation sequence(String name, float[] times, float[] turns, int joint,
                                           boolean loop) {
        float[] rotations = new float[times.length * JOINTS * 4];
        for (int key = 0; key < times.length; key++) {
            for (int other = 0; other < JOINTS; other++) {
                rotations[(key * JOINTS + other) * 4 + 3] = 1.0f;
            }
            float half = (float) Math.sin(Math.toRadians(turns[key]) * 0.5);
            float cos = (float) Math.cos(Math.toRadians(turns[key]) * 0.5);
            rotations[(key * JOINTS + joint) * 4 + 2] = half;
            rotations[(key * JOINTS + joint) * 4 + 3] = cos;
        }
        return new Rac1Animation(name, times, loop, rotations, new int[0], new int[0],
                new float[0], new int[0], new int[0], new float[0], new boolean[0]);
    }

    /**
     * The weapon of the checks: it is held still and it turns while it fires.
     *
     * @return the asset
     */
    private static Rac1AnimatedAsset weapon() {
        Rac1Animation held = sequence(Rac1BlasterPose.HELD_SEQUENCE, new float[] { 0.0f },
                new float[] { 0.0f }, HAND, false);
        Rac1Animation fire = sequence(Rac1BlasterPose.FIRE_SEQUENCE,
                new float[] { 0.0f, SHOT / 3.0f, 2.0f * SHOT / 3.0f, SHOT },
                new float[] { 0.0f, 30.0f, 60.0f, 0.0f }, HAND, true);
        return new Rac1AnimatedAsset(168, "gadget_168", mesh(), skeleton(),
                new Rac1Animation[] { held, fire }, new Rac1Texture[] { texture() });
    }

    /**
     * A texture of the checks.
     *
     * @return the texture
     */
    private static Rac1Texture texture() {
        return new Rac1Texture(2, 2, new byte[16]);
    }
}
