package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * A mesh whose vertices follow the joints of a skeleton.
 *
 * <p>Every vertex of the mesh names up to three joints and the weight that every one of them has.
 * The weights of a vertex add up to 256, the scale of the format of the game.
 */
public final class Rac1SkinnedMesh {

    /** The number of joints that a vertex can follow. */
    public static final int JOINTS_PER_VERTEX = 3;

    /** The weights of the joints of a vertex add up to this value. */
    public static final int WEIGHT_SCALE = 256;

    /** The positions of the vertices, three floats each. */
    private final float[] positions;

    /** The normals of the vertices, three floats each. */
    private final float[] normals;

    /** The texture coordinates of the vertices, two floats each. */
    private final float[] uvs;

    /** The triangles, three vertex indices each. */
    private final int[] indices;

    /** The material of every triangle. */
    private final int[] materials;

    /** The joints of every vertex, three indices each. */
    private final int[] joints;

    /** The weights of the joints of every vertex, three floats each. */
    private final float[] weights;

    /**
     * Creates a mesh.
     *
     * @param positions the positions of the vertices
     * @param normals the normals of the vertices
     * @param uvs the texture coordinates of the vertices
     * @param indices the triangles
     * @param materials the material of every triangle
     * @param joints the joints of every vertex
     * @param weights the weights of the joints of every vertex
     */
    public Rac1SkinnedMesh(float[] positions, float[] normals, float[] uvs, int[] indices,
                           int[] materials, int[] joints, float[] weights) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.indices = indices;
        this.materials = materials;
        this.joints = joints;
        this.weights = weights;
    }

    /**
     * The number of vertices of the mesh.
     *
     * @return the number of vertices
     */
    public int vertexCount() {
        return positions.length / 3;
    }

    /**
     * The number of triangles of the mesh.
     *
     * @return the number of triangles
     */
    public int triangleCount() {
        return indices.length / 3;
    }

    /**
     * The positions of the vertices.
     *
     * @return the positions, three floats each
     */
    public float[] positions() {
        return positions;
    }

    /**
     * The normals of the vertices.
     *
     * @return the normals, three floats each
     */
    public float[] normals() {
        return normals;
    }

    /**
     * The texture coordinates of the vertices.
     *
     * @return the coordinates, two floats each
     */
    public float[] uvs() {
        return uvs;
    }

    /**
     * The triangles of the mesh.
     *
     * @return the triangles, three vertex indices each
     */
    public int[] indices() {
        return indices;
    }

    /**
     * The material of every triangle.
     *
     * @return the materials, one per triangle
     */
    public int[] materials() {
        return materials;
    }

    /**
     * The joints of every vertex.
     *
     * @return the joints, three indices each
     */
    public int[] joints() {
        return joints;
    }

    /**
     * The weights of the joints of every vertex.
     *
     * @return the weights, three floats each
     */
    public float[] weights() {
        return weights;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1SkinnedMesh mesh && Arrays.equals(positions, mesh.positions)
                && Arrays.equals(normals, mesh.normals) && Arrays.equals(uvs, mesh.uvs)
                && Arrays.equals(indices, mesh.indices) && Arrays.equals(materials, mesh.materials)
                && Arrays.equals(joints, mesh.joints) && Arrays.equals(weights, mesh.weights);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(positions);
        result = 31 * result + Arrays.hashCode(normals);
        result = 31 * result + Arrays.hashCode(uvs);
        result = 31 * result + Arrays.hashCode(indices);
        result = 31 * result + Arrays.hashCode(materials);
        result = 31 * result + Arrays.hashCode(joints);
        result = 31 * result + Arrays.hashCode(weights);
        return result;
    }
}
