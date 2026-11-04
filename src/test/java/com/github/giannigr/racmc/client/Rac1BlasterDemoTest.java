package com.github.giannigr.racmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.github.giannigr.racmc.asset.Rac1Skeleton;
import com.github.giannigr.racmc.asset.Rac1StaticAsset;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the pose of the demo of the Blaster.
 *
 * <p>The assets of the checks are built by hand: a hero with three joints and a sequence that
 * turns the third one, and a weapon with a sequence of its own for the pose it is held in and one
 * for the shot. No data of the game is used anywhere.
 *
 * <p>The sequences of the checks follow the rule of the reader of the game: the key at the end of
 * a sequence that repeats itself holds the pose of the first key, so the frame after the last one
 * is the first one again.
 */
class Rac1BlasterDemoTest {

    /** The number of the joints of the skeleton of the checks. */
    private static final int JOINTS = 3;

    /** The joint of the hand of the hero of the checks. */
    private static final int HAND = 1;

    /** The length of the sequence of the hero of the checks, in seconds. */
    private static final float DURATION = 0.5f;

    /** The length of the sequence of the shot of the weapon, in seconds. */
    private static final float SHOT = 0.3f;

    /** The size of the tolerance of the checks. */
    private static final float TOLERANCE = 1.0e-4f;

    /**
     * The skeleton of the checks: the first joint stands in the middle, the second one is the hand
     * of the hero and is a child of the first one, and the third one is a child of the first one
     * as well and moves while the sequence of the hero runs.
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
        // The hand of the hero stands one unit above the first joint, so its inverse bind matrix
        // moves the points of the model one unit down.
        inverseBind[16 + 30] = -1.0f;
        // The third joint stands one unit beside the first one, so its inverse bind matrix moves
        // the points of the model one unit back.
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
     * The hero of the checks: his sequence turns his third joint and repeats itself.
     *
     * @return the asset
     */
    private static Rac1AnimatedAsset hero() {
        // The key at the end of the sequence holds the pose of the first key again.
        Rac1Animation animation = sequence("ratchet_sequence_56",
                new float[] { 0.0f, DURATION / 3.0f, 2.0f * DURATION / 3.0f, DURATION },
                new float[] { 0.0f, 30.0f, 60.0f, 0.0f }, 2, true);
        return new Rac1AnimatedAsset(0, "moby_0", mesh(), skeleton(),
                new Rac1Animation[] { animation }, new Rac1Texture[] { texture() });
    }

    /**
     * The weapon of the checks: it is held still and it turns while it fires.
     *
     * @return the asset
     */
    private static Rac1AnimatedAsset weapon() {
        Rac1Animation held = sequence("sequence_1", new float[] { 0.0f }, new float[] { 0.0f },
                HAND, false);
        Rac1Animation fire = sequence("sequence_4", new float[] { 0.0f, SHOT / 3.0f,
                2.0f * SHOT / 3.0f, SHOT }, new float[] { 0.0f, 30.0f, 60.0f, 0.0f }, HAND, true);
        return new Rac1AnimatedAsset(168, "gadget_168", mesh(), skeleton(),
                new Rac1Animation[] { held, fire }, new Rac1Texture[] { texture() });
    }

    /**
     * The content of a cache of the checks.
     *
     * @return the content
     */
    private static Rac1CacheContent content() {
        Rac1Mesh mesh = new Rac1Mesh(new float[9], new float[9], new float[6], new int[3],
                new int[] { 0 });
        Rac1StaticAsset scenery = new Rac1StaticAsset(452, "tie_452", mesh,
                new Rac1Texture[] { texture() });
        return new Rac1CacheContent("test", scenery, hero(), weapon(), HAND);
    }

    /**
     * A texture of the checks.
     *
     * @return the texture
     */
    private static Rac1Texture texture() {
        return new Rac1Texture(2, 2, new byte[16]);
    }

    /**
     * The places of the vertices of the hero of the demo at the given moment.
     *
     * @param time the moment of the demo in seconds
     * @return the places, three floats each
     */
    private static float[] pose(float time) {
        Rac1BlasterDemo demo = new Rac1BlasterDemo();
        demo.update(content(), time);
        return demo.ratchetPositions();
    }

    /**
     * The places of the vertices of the weapon of the demo at the given moment.
     *
     * @param time the moment of the demo in seconds
     * @return the places, three floats each
     */
    private static float[] weapon(float time) {
        Rac1BlasterDemo demo = new Rac1BlasterDemo();
        demo.update(content(), time);
        return demo.blasterPositions();
    }

    /**
     * The biggest change of a set of places between two moments.
     *
     * @param first the places of the first moment
     * @param second the places of the second moment
     * @return the biggest change of a place
     */
    private static float change(float[] first, float[] second) {
        float biggest = 0.0f;
        for (int index = 0; index < first.length; index++) {
            biggest = Math.max(biggest, Math.abs(first[index] - second[index]));
        }
        return biggest;
    }

    @Test
    @DisplayName("The hero moves while his sequence runs")
    void theHeroMovesWhileHisSequenceRuns() {
        float[] first = pose(0.05f);
        float[] second = pose(0.2f);

        assertTrue(change(first, second) > TOLERANCE,
                "The hero does not move while his sequence runs");
    }

    @Test
    @DisplayName("The pose of the hero is the same after a whole cycle of his sequence")
    void thePoseOfTheHeroIsTheSameAfterAWholeCycle() {
        // The cycle of a sequence that repeats itself is as long as its last key, because the key
        // at the end of the sequence holds the pose of the first key.
        float[] inside = pose(0.05f);
        float[] afterTheEnd = pose(0.05f + DURATION);
        for (int index = 0; index < inside.length; index++) {
            assertEquals(inside[index], afterTheEnd[index], TOLERANCE,
                    "The pose of the hero is not the same after a whole cycle");
        }

        // The hero has to move on in the second cycle as well.
        float[] secondCycle = pose(0.2f + DURATION);
        float[] secondInside = pose(0.2f);
        for (int index = 0; index < secondCycle.length; index++) {
            assertEquals(secondInside[index], secondCycle[index], TOLERANCE,
                    "The pose of the hero is not the same after a whole cycle");
        }
    }

    @Test
    @DisplayName("The hero does not stand still at the end of his cycle")
    void theHeroDoesNotStandStillAtTheEndOfHisCycle() {
        // A cycle that is made longer than the sequence would hold the pose of the first key for a
        // whole key interval at its end, and the hero would stand still once per cycle.
        int samples = 150;
        float[] previous = pose(0.0f);
        for (int sample = 1; sample <= samples; sample++) {
            float time = (float) sample / samples * (DURATION * 3.0f);
            float[] current = pose(time);
            assertTrue(change(previous, current) > TOLERANCE,
                    "The hero stands still at the moment " + time);
            previous = current;
        }
    }

    @Test
    @DisplayName("The hero is not torn apart when his sequence repeats itself")
    void theHeroIsNotTornApartWhenHisSequenceRepeatsItself() {
        // The game plays a sequence that repeats itself from its last key into its first key,
        // so the pose of the hero has to be the same on both sides of the end of the sequence.
        // A sequence that is played with a jump at its end would show it on the screen.
        float atTheEnd = change(pose(DURATION - 0.01f), pose(DURATION + 0.01f));

        assertTrue(atTheEnd < 0.15f,
                "The hero jumps when his sequence repeats itself: " + atTheEnd);
    }

    @Test
    @DisplayName("The weapon is placed on the joint of the hand without a matrix of its own")
    void theWeaponIsPlacedOnTheJointOfTheHand() {
        Rac1BlasterDemo demo = new Rac1BlasterDemo();
        demo.update(content(), 0.05f);

        // The place of the item of the hand is the pose of the joint of the hand, without the
        // scales that only reach the joint itself. The game adds no matrix of its own.
        Rac1AnimatedAsset hero = content().animatedAsset();
        float[] chain = Rac1AnimationEvaluator.evaluateChain(hero.skeleton(),
                hero.animations()[0], 0.05f);
        float[] attachment = demo.attachment();
        for (int index = 0; index < 16; index++) {
            assertEquals(chain[HAND * 16 + index], attachment[index], TOLERANCE,
                    "The weapon is not placed on the pose of the joint of the hand");
        }
    }

    @Test
    @DisplayName("The weapon fires once and is held still the rest of the time")
    void theWeaponFiresOnceAndIsHeldStillTheRestOfTheTime() {
        // The weapon is held with the sequence of the held weapon, which holds a single key and
        // keeps the weapon still.
        float[] held = weapon(1.0f);
        float[] still = weapon(1.5f);
        for (int index = 0; index < held.length; index++) {
            assertEquals(held[index], still[index], TOLERANCE,
                    "The weapon does not keep still while it is held");
        }

        // While it fires, the weapon plays the sequence of the shot, which turns its second
        // joint, so the vertices that follow that joint move.
        float[] firing = weapon(0.2f);
        assertTrue(change(held, firing) > TOLERANCE, "The weapon does not move while it fires");
    }

    @Test
    @DisplayName("The weapon fires the same way again after a shot")
    void theWeaponFiresTheSameWayAgainAfterAShot() {
        // The shot is played once, over the length of the sequence of the shot, and the weapon
        // goes back to the sequence of the held weapon afterwards.
        float[] first = weapon(0.2f);
        float[] second = weapon(0.2f + Rac1BlasterDemo.SHOT_INTERVAL);
        for (int index = 0; index < first.length; index++) {
            assertEquals(first[index], second[index], TOLERANCE,
                    "The weapon does not fire the same way again after a shot");
        }
    }

    @Test
    @DisplayName("The weapon is blended into the shot and out of it again")
    void theWeaponIsBlendedIntoTheShotAndOutOfItAgain() {
        // The weapon goes from the pose of the held weapon into the shot and back again, without
        // a jump at the end of the shot.
        float step = change(weapon(SHOT - 0.001f), weapon(SHOT + 0.001f));

        assertTrue(step < 0.1f, "The weapon jumps at the end of the shot: " + step);

        // The weapon is back in the pose of the held weapon when the shot is over.
        float[] held = weapon(1.0f);
        float[] afterTheShot = weapon(SHOT + 0.15f);
        assertTrue(change(held, afterTheShot) < TOLERANCE,
                "The weapon is not back in the pose of the held weapon after the shot");
    }
}
