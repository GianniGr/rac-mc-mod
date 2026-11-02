package com.github.giannigr.racmc.asset;

/**
 * The evaluator of the animation of the game.
 *
 * <p>The evaluator turns the keys of an animation sequence into the matrices of the joints and
 * moves the vertices of a mesh with them. It follows the rules of the evaluator of the game, as
 * they were measured by the ReRAC project (ISC); see
 * {@code docs/plan/moby_animation.md} of that project.
 *
 * <p>The game works in the row vectors: the rows of a matrix are the images of the axes. The
 * evaluator works in the column vectors of the game of blocks, so the matrices of the disc are
 * read the other way around.
 */
public final class Rac1AnimationEvaluator {

    /** The number of floats of a matrix. */
    public static final int MATRIX_SIZE = 16;

    private Rac1AnimationEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The matrices of the joints of a skeleton at a moment of an animation.
     *
     * <p>The matrices are the matrices of the game of blocks: they turn a point of the model of
     * the game into a point of the world of the game of blocks.
     *
     * @param skeleton the skeleton of the class
     * @param animation the animation sequence
     * @param time the moment of the animation in seconds
     * @param unitScale the number of the units of the game in a block
     * @return the matrices, sixteen floats per joint
     */
    public static float[] evaluate(Rac1Skeleton skeleton, Rac1Animation animation, float time,
                                   float unitScale) {
        float[] pose = evaluatePose(skeleton, animation, time);
        int joints = skeleton.jointCount();
        float[] inverseBind = skeleton.inverseBind();
        float[] matrices = new float[joints * MATRIX_SIZE];
        for (int joint = 0; joint < joints; joint++) {
            multiply(pose, joint * MATRIX_SIZE, inverseBind, joint * MATRIX_SIZE, matrices,
                    joint * MATRIX_SIZE);
            // The skeleton of the game works in the units of the model, the vertices of the mesh
            // in the units of the world.
            int at = joint * MATRIX_SIZE;
            matrices[at + 12] *= unitScale;
            matrices[at + 13] *= unitScale;
            matrices[at + 14] *= unitScale;
        }
        return matrices;
    }

    /**
     * The pose of the joints of a skeleton at a moment of an animation.
     *
     * <p>The pose of a joint is the product of the matrices of the joints of its chain. At the
     * first key of the first sequence of a class the pose is the pose in which the class was
     * modelled, so the pose and the inverse bind matrix of the joint cancel each other out.
     *
     * @param skeleton the skeleton of the class
     * @param animation the animation sequence
     * @param time the moment of the animation in seconds
     * @return the matrices, sixteen floats per joint
     */
    public static float[] evaluatePose(Rac1Skeleton skeleton, Rac1Animation animation, float time) {
        return evaluatePose(skeleton, animation, time, true);
    }

    /**
     * The pose of the joints of a skeleton at a moment of an animation, without the scales that
     * only reach the joint itself.
     *
     * <p>The game places the items of the hero with the pose of the joint they hang on, and it
     * leaves the scales that only reach the joint itself out of it.
     *
     * @param skeleton the skeleton of the class
     * @param animation the animation sequence
     * @param time the moment of the animation in seconds
     * @return the matrices, sixteen floats per joint
     */
    public static float[] evaluateChain(Rac1Skeleton skeleton, Rac1Animation animation, float time) {
        return evaluatePose(skeleton, animation, time, false);
    }

    private static float[] evaluatePose(Rac1Skeleton skeleton, Rac1Animation animation, float time,
                                        boolean postScale) {
        int joints = skeleton.jointCount();
        float[] matrices = new float[joints * MATRIX_SIZE];

        int keyCount = animation.keyCount();
        float[] times = animation.times();
        int lower = 0;
        int upper = 0;
        if (keyCount == 1) {
            lower = 0;
            upper = 0;
        } else {
            upper = keyCount - 1;
            for (int index = 0; index < keyCount - 1; index++) {
                if (time >= times[index] && time <= times[index + 1]) {
                    lower = index;
                    upper = index + 1;
                    break;
                }
                if (time > times[index]) {
                    lower = index;
                }
            }
            if (time <= times[0]) {
                lower = 0;
                upper = 0;
            } else if (time >= times[keyCount - 1]) {
                lower = keyCount - 1;
                upper = keyCount - 1;
            }
        }
        float factor = 0.0f;
        if (upper != lower) {
            float span = times[upper] - times[lower];
            factor = span > 0.0f ? (time - times[lower]) / span : 0.0f;
            factor = Math.min(1.0f, Math.max(0.0f, factor));
        }

        float[] lowerTranslations = channels(animation, lower, skeleton, true);
        float[] upperTranslations = channels(animation, upper, skeleton, true);
        float[] translations = new float[joints * 3];
        float[] scales = new float[joints * 3];
        float[] postScales = new float[joints * 3];
        float weight = 1.0f - factor;
        for (int index = 0; index < joints * 3; index++) {
            translations[index] = lowerTranslations[index] * weight + upperTranslations[index] * factor;
            scales[index] = lowerTranslations[joints * 3 + index] * weight
                    + upperTranslations[joints * 3 + index] * factor;
            postScales[index] = lowerTranslations[joints * 6 + index] * weight
                    + upperTranslations[joints * 6 + index] * factor;
        }

        // The rotation of every joint.
        float[] rotations = animation.rotations();
        float[] rotation = new float[joints * 4];
        for (int joint = 0; joint < joints; joint++) {
            float x0 = rotations[lower * joints * 4 + joint * 4];
            float y0 = rotations[lower * joints * 4 + joint * 4 + 1];
            float z0 = rotations[lower * joints * 4 + joint * 4 + 2];
            float w0 = rotations[lower * joints * 4 + joint * 4 + 3];
            float x1 = rotations[upper * joints * 4 + joint * 4];
            float y1 = rotations[upper * joints * 4 + joint * 4 + 1];
            float z1 = rotations[upper * joints * 4 + joint * 4 + 2];
            float w1 = rotations[upper * joints * 4 + joint * 4 + 3];
            if (lower == upper) {
                rotation[joint * 4] = x0;
                rotation[joint * 4 + 1] = y0;
                rotation[joint * 4 + 2] = z0;
                rotation[joint * 4 + 3] = w0;
                continue;
            }
            float dot = x0 * x1 + y0 * y1 + z0 * z1 + w0 * w1;
            float sign = dot < 0.0f ? -1.0f : 1.0f;
            float rest = 1.0f - factor;
            float x = x0 * rest + x1 * sign * factor;
            float y = y0 * rest + y1 * sign * factor;
            float z = z0 * rest + z1 * sign * factor;
            float w = w0 * rest + w1 * sign * factor;
            float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            if (length > 0.0f) {
                x /= length;
                y /= length;
                z /= length;
                w /= length;
            }
            rotation[joint * 4] = x;
            rotation[joint * 4 + 1] = y;
            rotation[joint * 4 + 2] = z;
            rotation[joint * 4 + 3] = w;
        }

        // The local matrices and the chain of the joints.
        int[] parents = skeleton.parents();
        for (int joint = 0; joint < joints; joint++) {
            float[] local = localMatrix(rotation, joint, translations, scales);
            int parent = parents[joint];
            if (parent < 0) {
                System.arraycopy(local, 0, matrices, joint * MATRIX_SIZE, MATRIX_SIZE);
            } else {
                multiply(matrices, parent * MATRIX_SIZE, local, 0, matrices, joint * MATRIX_SIZE);
            }
        }
        // The scales that only reach the joint itself are applied after the whole chain.
        if (postScale) {
            for (int joint = 0; joint < joints; joint++) {
                float sx = postScales[joint * 3];
                float sy = postScales[joint * 3 + 1];
                float sz = postScales[joint * 3 + 2];
                if (sx == 1.0f && sy == 1.0f && sz == 1.0f) {
                    continue;
                }
                int at = joint * MATRIX_SIZE;
                for (int row = 0; row < 3; row++) {
                    matrices[at + row] *= sx;
                    matrices[at + 4 + row] *= sy;
                    matrices[at + 8 + row] *= sz;
                }
            }
        }
        // The skinning matrices of the joints are built by the caller.
        return matrices;
    }

    /**
     * Moves the vertices of a mesh with the matrices of the joints.
     *
     * @param mesh the mesh of the class
     * @param matrices the matrices of the joints
     * @param positions the place the moved positions are written to
     * @param normals the place the moved normals are written to
     */
    public static void pose(Rac1SkinnedMesh mesh, float[] matrices, float[] positions,
                            float[] normals) {
        float[] source = mesh.positions();
        float[] sourceNormals = mesh.normals();
        int[] joints = mesh.joints();
        float[] weights = mesh.weights();
        int count = mesh.vertexCount();
        for (int vertex = 0; vertex < count; vertex++) {
            float x = source[vertex * 3];
            float y = source[vertex * 3 + 1];
            float z = source[vertex * 3 + 2];
            float nx = sourceNormals[vertex * 3];
            float ny = sourceNormals[vertex * 3 + 1];
            float nz = sourceNormals[vertex * 3 + 2];
            float px = 0.0f;
            float py = 0.0f;
            float pz = 0.0f;
            float mx = 0.0f;
            float my = 0.0f;
            float mz = 0.0f;
            for (int influence = 0; influence < Rac1SkinnedMesh.JOINTS_PER_VERTEX; influence++) {
                float weight = weights[vertex * 3 + influence];
                if (weight == 0.0f) {
                    continue;
                }
                int joint = joints[vertex * 3 + influence];
                int at = joint * MATRIX_SIZE;
                px += weight * (matrices[at] * x + matrices[at + 4] * y + matrices[at + 8] * z
                        + matrices[at + 12]);
                py += weight * (matrices[at + 1] * x + matrices[at + 5] * y + matrices[at + 9] * z
                        + matrices[at + 13]);
                pz += weight * (matrices[at + 2] * x + matrices[at + 6] * y + matrices[at + 10] * z
                        + matrices[at + 14]);
                mx += weight * (matrices[at] * nx + matrices[at + 4] * ny + matrices[at + 8] * nz);
                my += weight * (matrices[at + 1] * nx + matrices[at + 5] * ny + matrices[at + 9] * nz);
                mz += weight * (matrices[at + 2] * nx + matrices[at + 6] * ny + matrices[at + 10] * nz);
            }
            positions[vertex * 3] = px;
            positions[vertex * 3 + 1] = py;
            positions[vertex * 3 + 2] = pz;
            float length = (float) Math.sqrt(mx * mx + my * my + mz * mz);
            if (length > 0.0f) {
                mx /= length;
                my /= length;
                mz /= length;
            }
            normals[vertex * 3] = mx;
            normals[vertex * 3 + 1] = my;
            normals[vertex * 3 + 2] = mz;
        }
    }

    private static float[] channels(Rac1Animation animation, int key, Rac1Skeleton skeleton,
                                    boolean unused) {
        int joints = skeleton.jointCount();
        float[] result = new float[joints * 9];
        for (int joint = 0; joint < joints; joint++) {
            System.arraycopy(skeleton.restTranslations(), joint * 3, result, joint * 3, 3);
            for (int channel = 1; channel <= 2; channel++) {
                int base = joints * 3 * channel + joint * 3;
                result[base] = 1.0f;
                result[base + 1] = 1.0f;
                result[base + 2] = 1.0f;
            }
        }
        int[] keys = animation.translationKeys();
        int[] jointsOf = animation.translationJoints();
        float[] values = animation.translationValues();
        for (int index = 0; index < keys.length; index++) {
            if (keys[index] != key) {
                continue;
            }
            int joint = jointsOf[index];
            if (joint * 3 + 2 >= joints * 3) {
                continue;
            }
            result[joint * 3] = values[index * 3];
            result[joint * 3 + 1] = values[index * 3 + 1];
            result[joint * 3 + 2] = values[index * 3 + 2];
        }
        int[] scaleKeys = animation.scaleKeys();
        int[] scaleJoints = animation.scaleJoints();
        float[] scaleValues = animation.scaleValues();
        boolean[] inherited = animation.scaleInherited();
        for (int index = 0; index < scaleKeys.length; index++) {
            if (scaleKeys[index] != key) {
                continue;
            }
            int joint = scaleJoints[index];
            if (joint * 3 + 2 >= joints * 3) {
                continue;
            }
            int base = (inherited[index] ? 1 : 2) * joints * 3 + joint * 3;
            result[base] = scaleValues[index * 3];
            result[base + 1] = scaleValues[index * 3 + 1];
            result[base + 2] = scaleValues[index * 3 + 2];
        }
        return result;
    }

    private static float[] localMatrix(float[] rotation, int joint, float[] translations,
                                       float[] scales) {
        float x = rotation[joint * 4];
        float y = rotation[joint * 4 + 1];
        float z = rotation[joint * 4 + 2];
        float w = rotation[joint * 4 + 3];
        float[] matrix = new float[MATRIX_SIZE];
        // The game builds the rows of its matrix from the quaternion: the row 0 is the image of
        // the first axis. The matrix of the game of blocks holds the columns of that matrix, so
        // the rotation of the quaternion is used the other way around.
        matrix[0] = 1.0f - 2.0f * (y * y + z * z);
        matrix[4] = 2.0f * (x * y + z * w);
        matrix[8] = 2.0f * (x * z - y * w);
        matrix[1] = 2.0f * (x * y - z * w);
        matrix[5] = 1.0f - 2.0f * (x * x + z * z);
        matrix[9] = 2.0f * (y * z + x * w);
        matrix[2] = 2.0f * (x * z + y * w);
        matrix[6] = 2.0f * (y * z - x * w);
        matrix[10] = 1.0f - 2.0f * (x * x + y * y);
        matrix[15] = 1.0f;
        for (int row = 0; row < 3; row++) {
            matrix[row] *= scales[joint * 3];
            matrix[4 + row] *= scales[joint * 3 + 1];
            matrix[8 + row] *= scales[joint * 3 + 2];
        }
        matrix[12] = translations[joint * 3];
        matrix[13] = translations[joint * 3 + 1];
        matrix[14] = translations[joint * 3 + 2];
        return matrix;
    }

    private static void multiply(float[] left, int leftAt, float[] right, int rightAt,
                                 float[] target, int targetAt) {
        float[] result = new float[MATRIX_SIZE];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                float sum = 0.0f;
                for (int step = 0; step < 4; step++) {
                    // The matrices are stored in the order of their columns.
                    sum += left[leftAt + step * 4 + row] * right[rightAt + column * 4 + step];
                }
                result[column * 4 + row] = sum;
            }
        }
        System.arraycopy(result, 0, target, targetAt, MATRIX_SIZE);
    }
}
