package com.github.giannigr.racmc.asset;

import java.util.Arrays;

/**
 * A mesh that was converted from the disc of the game.
 *
 * <p>The mesh holds the vertices of the geometry and the triangles that use them. The positions
 * are already in the coordinate system of the game: the units of the game and its Z-up world.
 */
public final class Rac1Mesh {

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

    /**
     * Creates a mesh.
     *
     * @param positions the positions of the vertices
     * @param normals the normals of the vertices
     * @param uvs the texture coordinates of the vertices
     * @param indices the triangles
     * @param materials the material of every triangle
     */
    public Rac1Mesh(float[] positions, float[] normals, float[] uvs, int[] indices, int[] materials) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.indices = indices;
        this.materials = materials;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Rac1Mesh mesh && Arrays.equals(positions, mesh.positions)
                && Arrays.equals(normals, mesh.normals) && Arrays.equals(uvs, mesh.uvs)
                && Arrays.equals(indices, mesh.indices) && Arrays.equals(materials, mesh.materials);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(positions);
        result = 31 * result + Arrays.hashCode(normals);
        result = 31 * result + Arrays.hashCode(uvs);
        result = 31 * result + Arrays.hashCode(indices);
        result = 31 * result + Arrays.hashCode(materials);
        return result;
    }
}
