package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the evaluator of the animation of the game.
 *
 * <p>The skeleton of the checks has two joints: the first one stands in the middle and the second
 * one is a child of it, one unit above it. The mesh of the checks has three vertices: one that
 * follows the first joint and two that follow the second one.
 *
 * <p>The game of the disc keeps the rows of its matrices, and the row 0 of a matrix is the image
 * of its first axis. The checks use that convention, so that a change of the convention in the
 * evaluator is noticed.
 */
class Rac1AnimationEvaluatorTest {

    /** The number of the joints of the skeleton of the checks. */
    private static final int JOINTS = 2;

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-4f;

    /**
     * The skeleton of the checks: the first joint stands in the middle, the second one is one
     * unit above it.
     *
     * @return the skeleton
     */
    private static Rac1Skeleton skeleton() {
        float[] inverseBind = new float[JOINTS * 16];
        // The first joint stands in the middle, so its inverse bind matrix is the identity.
        inverseBind[0] = 1.0f;
        inverseBind[5] = 1.0f;
        inverseBind[10] = 1.0f;
        inverseBind[15] = 1.0f;
        // The second joint is one unit above the first one, so its inverse bind matrix moves
        // the points of the model one unit down.
        inverseBind[16] = 1.0f;
        inverseBind[21] = 1.0f;
        inverseBind[26] = 1.0f;
        inverseBind[30] = -1.0f;
        inverseBind[31] = 1.0f;
        int[] parents = { -1, 0 };
        float[] rest = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        return new Rac1Skeleton(inverseBind, parents, rest);
    }

    /**
     * The mesh of the checks.
     *
     * @return the mesh
     */
    private static Rac1SkinnedMesh mesh() {
        float[] positions = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f };
        float[] normals = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };
        float[] uvs = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f };
        int[] indices = { 0, 1, 2 };
        int[] materials = { 0 };
        int[] joints = { 0, 0, 0, 1, 0, 0, 1, 0, 0 };
        float[] weights = { 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f };
        return new Rac1SkinnedMesh(positions, normals, uvs, indices, materials, joints, weights);
    }

    /**
     * An animation whose keys hold the given rotations.
     *
     * @param rotations the quaternion of every joint of the key
     * @return the animation
     */
    private static Rac1Animation animation(float[] rotations) {
        return new Rac1Animation("test", new float[] { 0.0f }, false, rotations, new int[0],
                new int[0], new float[0], new int[0], new int[0], new float[0], new boolean[0]);
    }

    /**
     * The rows of the rotation of a quaternion, the way the game of the disc builds them.
     *
     * @param x the first component of the quaternion
     * @param y the second component of the quaternion
     * @param z the third component of the quaternion
     * @param w the fourth component of the quaternion
     * @return the three rows, nine numbers
     */
    private static float[] gameRows(float x, float y, float z, float w) {
        return new float[] {
                1.0f - 2.0f * (y * y + z * z), 2.0f * (x * y - z * w), 2.0f * (x * z + y * w),
                2.0f * (x * y + z * w), 1.0f - 2.0f * (x * x + z * z), 2.0f * (y * z - x * w),
                2.0f * (x * z - y * w), 2.0f * (y * z + x * w), 1.0f - 2.0f * (x * x + y * y) };
    }

    /**
     * Moves a point with the rows of the game of the disc.
     *
     * @param rows the rows of the rotation
     * @param x the first coordinate of the point
     * @param y the second coordinate of the point
     * @param z the third coordinate of the point
     * @return the three coordinates of the moved point
     */
    private static float[] move(float[] rows, float x, float y, float z) {
        return new float[] {
                rows[0] * x + rows[3] * y + rows[6] * z,
                rows[1] * x + rows[4] * y + rows[7] * z,
                rows[2] * x + rows[5] * y + rows[8] * z };
    }

    @Test
    @DisplayName("The skinning matrices of the bind pose are the identity")
    void theSkinningMatricesOfTheBindPoseAreTheIdentity() {
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation(rotations), 0.0f,
                1.0f);

        for (int joint = 0; joint < JOINTS; joint++) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    float expected = row == column ? 1.0f : 0.0f;
                    assertEquals(expected, matrices[joint * 16 + column * 4 + row], TOLERANCE,
                            "The skinning matrix of the joint " + joint + " is not the identity");
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(0.0f, matrices[joint * 16 + 12 + axis], TOLERANCE,
                        "The skinning matrix of the joint " + joint + " moves the model");
            }
        }
    }

    @Test
    @DisplayName("The vertices of the bind pose do not move")
    void theVerticesOfTheBindPoseDoNotMove() {
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        Rac1SkinnedMesh mesh = mesh();

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation(rotations), 0.0f,
                1.0f);
        float[] positions = new float[mesh.positions().length];
        float[] normals = new float[mesh.normals().length];
        Rac1AnimationEvaluator.pose(mesh, matrices, positions, normals);

        for (int index = 0; index < mesh.positions().length; index++) {
            assertEquals(mesh.positions()[index], positions[index], TOLERANCE,
                    "The vertex " + (index / 3) + " moved in the bind pose");
        }
    }

    @Test
    @DisplayName("A key that turns a joint turns the vertices that follow it")
    void aKeyThatTurnsAJointTurnsItsVertices() {
        // A turn of ninety degrees around the third axis.
        float half = (float) Math.sqrt(0.5);
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, half, half };
        Rac1SkinnedMesh mesh = mesh();

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation(rotations), 0.0f,
                1.0f);
        float[] positions = new float[mesh.positions().length];
        float[] normals = new float[mesh.normals().length];
        Rac1AnimationEvaluator.pose(mesh, matrices, positions, normals);

        // The second joint stands one unit above the first one, so the vertices that follow it
        // are turned around that place.
        float[] rows = gameRows(0.0f, 0.0f, half, half);
        float[] expected = move(rows, 1.0f, 0.0f, 0.0f);
        expected[2] += 1.0f;
        assertEquals(expected[0], positions[3], TOLERANCE, "The first vertex was not turned");
        assertEquals(expected[1], positions[4], TOLERANCE, "The first vertex was not turned");
        assertEquals(expected[2], positions[5], TOLERANCE, "The first vertex was not turned");

        // The vertex that follows the first joint does not move.
        assertEquals(0.0f, positions[0], TOLERANCE);
        assertEquals(0.0f, positions[1], TOLERANCE);
        assertEquals(0.0f, positions[2], TOLERANCE);
    }

    @Test
    @DisplayName("The chain of the joints is followed")
    void theChainOfTheJointsIsFollowed() {
        // The first joint is turned, the second one is not, so the second one is turned as well.
        float half = (float) Math.sqrt(0.5);
        float[] rotations = { 0.0f, 0.0f, half, half, 0.0f, 0.0f, 0.0f, 1.0f };

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation(rotations), 0.0f,
                1.0f);

        float[] rows = gameRows(0.0f, 0.0f, half, half);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                // The matrix of the game of blocks holds the columns of the matrix of the game.
                assertEquals(rows[row * 3 + column], matrices[16 + row * 4 + column], TOLERANCE,
                        "The second joint does not follow the first one");
            }
        }
    }

    @Test
    @DisplayName("A scale that only reaches the joint itself does not reach its children")
    void aScaleThatOnlyReachesTheJointDoesNotReachItsChildren() {
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        // The first joint is made twice as big, the scale does not reach the second joint.
        Rac1Animation animation = new Rac1Animation("test", new float[] { 0.0f }, false, rotations,
                new int[0], new int[0], new float[0], new int[] { 0 }, new int[] { 0 },
                new float[] { 2.0f, 2.0f, 2.0f }, new boolean[] { false });

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 0.0f, 1.0f);

        assertEquals(2.0f, matrices[0], TOLERANCE, "The scale did not reach the joint");
        assertEquals(1.0f, matrices[16], TOLERANCE, "The scale reached the child of the joint");
    }

    @Test
    @DisplayName("A scale that reaches the joint itself is part of the local matrix")
    void aScaleThatReachesTheJointItselfIsPartOfTheLocalMatrix() {
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        Rac1Animation animation = new Rac1Animation("test", new float[] { 0.0f }, false, rotations,
                new int[0], new int[0], new float[0], new int[] { 0 }, new int[] { 0 },
                new float[] { 2.0f, 1.0f, 1.0f }, new boolean[] { true });

        float[] matrices = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 0.0f, 1.0f);

        assertEquals(2.0f, matrices[0], TOLERANCE, "The scale did not reach the joint");
        assertEquals(1.0f, matrices[5], TOLERANCE);
        assertEquals(1.0f, matrices[10], TOLERANCE);
    }

    @Test
    @DisplayName("The pose of the chain does not hold the scales that only reach the joint")
    void thePoseOfTheChainDoesNotHoldTheScales() {
        float[] rotations = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
        Rac1Animation animation = new Rac1Animation("test", new float[] { 0.0f }, false, rotations,
                new int[0], new int[0], new float[0], new int[] { 0 }, new int[] { 0 },
                new float[] { 2.0f, 2.0f, 2.0f }, new boolean[] { false });

        float[] pose = Rac1AnimationEvaluator.evaluateChain(skeleton(), animation, 0.0f);

        assertEquals(1.0f, pose[0], TOLERANCE,
                "The scale of the chain would move the items of the hand of the hero");
    }

    @Test
    @DisplayName("The keys of an animation are read in the order of their times")
    void theKeysAreReadInTheOrderOfTheirTimes() {
        float half = (float) Math.sqrt(0.5);
        int keyLength = JOINTS * 4;
        float[] rotations = new float[2 * keyLength];
        // The first joint stands still in both keys.
        rotations[3] = 1.0f;
        rotations[keyLength + 3] = 1.0f;
        // The first key turns the second joint.
        rotations[keyLength - 2] = half;
        rotations[keyLength - 1] = half;
        // The second key does not turn it.
        rotations[2 * keyLength - 1] = 1.0f;
        Rac1Animation animation = new Rac1Animation("test", new float[] { 0.0f, 1.0f }, false,
                rotations, new int[0], new int[0], new float[0], new int[0], new int[0],
                new float[0], new boolean[0]);

        float[] first = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 0.0f, 1.0f);
        float[] second = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 1.0f, 1.0f);

        assertTrue(Math.abs(first[16] - second[16]) > 0.1f,
                "The joint was not turned in the first key");
        assertEquals(1.0f, second[16], TOLERANCE, "The joint was still turned in the second key");
    }

    /**
     * A sequence that repeats itself, as the reader of the game reads it.
     *
     * <p>The reader of the game adds a key at the end of a sequence that repeats itself, and that
     * key holds the pose of the first key, so the frame after the last one is the first one again.
     * The keys of the sequence of the checks are at the times 0, 0.25 and 0.5 seconds, the last
     * one holds the pose of the first one, and the second joint is turned a little more in every
     * key.
     *
     * @return the sequence
     */
    private static Rac1Animation loop() {
        int keyLength = JOINTS * 4;
        float[] rotations = new float[3 * keyLength];
        for (int key = 0; key < 3; key++) {
            rotations[key * keyLength + 3] = 1.0f;
            float angle = (float) Math.toRadians(30.0f * key);
            rotations[key * keyLength + keyLength - 2] = (float) Math.sin(angle * 0.5);
            rotations[key * keyLength + keyLength - 1] = (float) Math.cos(angle * 0.5);
        }
        return new Rac1Animation("test", new float[] { 0.0f, 0.25f, 0.5f }, true, rotations,
                new int[0], new int[0], new float[0], new int[0], new int[0], new float[0],
                new boolean[0]);
    }

    @Test
    @DisplayName("A sequence that repeats itself repeats itself over its last key")
    void aSequenceThatRepeatsItselfRepeatsItselfOverItsLastKey() {
        // The cycle of the sequence is as long as its last key, because the key at the end of the
        // sequence holds the pose of the first key.
        float[] inside = Rac1AnimationEvaluator.evaluate(skeleton(), loop(), 0.05f, 1.0f);
        float[] afterTheEnd = Rac1AnimationEvaluator.evaluate(skeleton(), loop(), 0.55f, 1.0f);

        for (int index = 0; index < inside.length; index++) {
            assertEquals(inside[index], afterTheEnd[index], TOLERANCE,
                    "The pose is not the same after a whole cycle of the sequence");
        }
    }

    @Test
    @DisplayName("A sequence that repeats itself does not stand still at the end of its cycle")
    void aSequenceThatRepeatsItselfDoesNotStandStillAtTheEndOfItsCycle() {
        // A cycle that was made longer than the sequence would hold the pose of the first key for
        // a whole key interval at its end.
        float previous = Rac1AnimationEvaluator.evaluate(skeleton(), loop(), 0.45f, 1.0f)[16];
        float current = Rac1AnimationEvaluator.evaluate(skeleton(), loop(), 0.475f, 1.0f)[16];

        assertTrue(Math.abs(previous - current) > TOLERANCE,
                "The joint stands still at the end of the cycle of the sequence");
    }

    @Test
    @DisplayName("A sequence that does not repeat itself holds its last key")
    void aSequenceThatDoesNotRepeatItselfHoldsItsLastKey() {
        // The key at the end of the sequence turns the second joint, the keys before it do not.
        int keyLength = JOINTS * 4;
        float[] rotations = new float[2 * keyLength];
        rotations[3] = 1.0f;
        rotations[keyLength + 3] = 1.0f;
        float half = (float) Math.sqrt(0.5);
        rotations[keyLength - 2] = half;
        rotations[keyLength - 1] = half;
        Rac1Animation animation = new Rac1Animation("test", new float[] { 0.0f, 1.0f }, false,
                rotations, new int[0], new int[0], new float[0], new int[0], new int[0],
                new float[0], new boolean[0]);

        float[] last = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 1.0f, 1.0f);
        float[] afterTheEnd = Rac1AnimationEvaluator.evaluate(skeleton(), animation, 5.0f, 1.0f);

        for (int index = 0; index < last.length; index++) {
            assertEquals(last[index], afterTheEnd[index], TOLERANCE,
                    "The sequence does not hold its last key");
        }
    }
}
