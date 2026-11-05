package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The renderer of the meshes that were converted out of the disc of the game.
 *
 * <p>The geometry of the game is an arbitrary triangle mesh, so it cannot be drawn with the model
 * of a block or of a character. The renderer therefore draws the triangles of the mesh itself, with
 * the texture of the material of every triangle.
 *
 * <p>The triangles of a class of the game follow each other in the order of the packets of the
 * class, and the materials of the packets follow each other again and again: a mesh of a character
 * holds the materials 3, 0, 1, 0, 1, 0, 1, 0, 2 and so on. The renderer of the game of blocks holds
 * one buffer for every kind of triangle, and a buffer that was left while another kind of triangle
 * was written to it cannot be used any more - it throws {@code IllegalStateException: Not
 * building!}. The renderer therefore groups the triangles by their material, so that every kind
 * of triangle is written in one run, and it flushes the run before it writes the next one.
 *
 * <p>The kinds of triangles of the game of blocks are drawn in quads and not in triangles, so every
 * triangle of the mesh is written as a quad of four vertices, whose first vertex is written twice.
 * A triangle that is written as a list of three vertices only would be cut in two by the renderer
 * of the game of blocks: the first triangle of the quad would be the triangle of the mesh, but
 * the second one would join the last vertex of that triangle with the first vertex of the next
 * triangle of the mesh, and the model would be covered with thin shards.
 */
public final class Rac1MeshRenderer {

    /** The light that is used when the mesh is drawn. */
    public static final int FULL_LIGHT = 0xF000F0;

    /** The kind of triangle that is used when a material has no texture. */
    private static final RenderType MISSING = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("missing"));

    /**
     * The kinds of triangles of the textures of the game.
     *
     * <p>The renderer of the game of blocks keeps its buffers in a map that is keyed by the kind of
     * the triangle, so a new kind of triangle for every frame would fill that map with kinds that
     * are never used again. The kinds are therefore kept for the lifetime of the game.
     */
    private static final Map<ResourceLocation, RenderType> TYPES = new ConcurrentHashMap<>();

    private Rac1MeshRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A run of the triangles of a mesh that share a material.
     *
     * @param slot the number of the texture of the run, or {@code -1} when the material has none
     * @param triangles the numbers of the triangles of the run, in the order of the mesh
     */
    public record MaterialRun(int slot, int[] triangles) {
    }

    /**
     * The runs of the triangles of a mesh that share a material.
     *
     * <p>The runs cover every triangle of the mesh exactly once and follow each other in the order
     * of the materials. A triangle whose material is not a texture of the mesh belongs to the run
     * of the missing texture, so that a material that is out of range cannot make the renderer
     * read outside of the array of the textures.
     *
     * @param indices the indices of the vertices of the triangles
     * @param materials the material of every triangle
     * @param textureCount the number of the textures of the mesh
     * @return the runs, in the order of the materials
     */
    public static List<MaterialRun> materialRuns(int[] indices, int[] materials, int textureCount) {
        int triangles = indices.length / 3;
        Map<Integer, List<Integer>> runs = new LinkedHashMap<>();
        for (int triangle = 0; triangle < triangles; triangle++) {
            int slot = slot(materials, triangle, textureCount);
            runs.computeIfAbsent(slot, key -> new ArrayList<>()).add(triangle);
        }
        List<MaterialRun> result = new ArrayList<>(runs.size());
        for (Map.Entry<Integer, List<Integer>> entry : runs.entrySet()) {
            int[] numbers = new int[entry.getValue().size()];
            for (int index = 0; index < numbers.length; index++) {
                numbers[index] = entry.getValue().get(index);
            }
            result.add(new MaterialRun(entry.getKey(), numbers));
        }
        return result;
    }

    /**
     * The number of the texture of a triangle.
     *
     * @param materials the material of every triangle
     * @param triangle the number of the triangle
     * @param textureCount the number of the textures of the mesh
     * @return the number of the texture, or {@code -1} when the material names none
     */
    private static int slot(int[] materials, int triangle, int textureCount) {
        if (triangle < 0 || triangle >= materials.length) {
            return -1;
        }
        int material = materials[triangle];
        if (material < 0 || material >= textureCount) {
            return -1;
        }
        return material;
    }

    /**
     * Draws a mesh.
     *
     * @param pose the place of the mesh
     * @param buffers the buffers the triangles are written into
     * @param mesh the mesh
     * @param textures the place of the texture of every material
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Rac1Mesh mesh,
                               ResourceLocation[] textures) {
        render(pose, buffers, mesh.positions(), mesh.normals(), mesh.uvs(), mesh.indices(),
                mesh.materials(), textures, FULL_LIGHT);
    }

    /**
     * Draws a mesh with a light of its own.
     *
     * @param pose the place of the mesh
     * @param buffers the buffers the triangles are written into
     * @param mesh the mesh
     * @param textures the place of the texture of every material
     * @param light the light the mesh is drawn with
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Rac1Mesh mesh,
                               ResourceLocation[] textures, int light) {
        render(pose, buffers, mesh.positions(), mesh.normals(), mesh.uvs(), mesh.indices(),
                mesh.materials(), textures, light);
    }

    /**
     * Draws a mesh that follows the joints of a skeleton.
     *
     * @param pose the place of the mesh
     * @param buffers the buffers the triangles are written into
     * @param mesh the mesh
     * @param positions the places of the vertices
     * @param normals the normals of the vertices
     * @param textures the place of the texture of every material
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Rac1SkinnedMesh mesh,
                               float[] positions, float[] normals, ResourceLocation[] textures) {
        render(pose, buffers, positions, normals, mesh.uvs(), mesh.indices(), mesh.materials(),
                textures, FULL_LIGHT);
    }

    /**
     * Draws a mesh that follows the joints of a skeleton, with a light of its own.
     *
     * @param pose the place of the mesh
     * @param buffers the buffers the triangles are written into
     * @param mesh the mesh
     * @param positions the places of the vertices
     * @param normals the normals of the vertices
     * @param textures the place of the texture of every material
     * @param light the light the mesh is drawn with
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Rac1SkinnedMesh mesh,
                               float[] positions, float[] normals, ResourceLocation[] textures,
                               int light) {
        render(pose, buffers, positions, normals, mesh.uvs(), mesh.indices(), mesh.materials(),
                textures, light);
    }

    private static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, float[] positions,
                               float[] normals, float[] uvs, int[] indices, int[] materials,
                               ResourceLocation[] textures, int light) {
        int vertices = positions.length / 3;
        for (MaterialRun run : materialRuns(indices, materials, textures.length)) {
            ResourceLocation texture = run.slot() < 0 ? null : textures[run.slot()];
            RenderType type = typeOf(texture);
            // The buffer of the kind of the triangle is asked for again for every run, because the
            // renderer of the game of blocks closes a buffer as soon as another kind of triangle
            // is written to it.
            VertexConsumer consumer = buffers.getBuffer(type);
            for (int triangle : run.triangles()) {
                if (!writable(indices, triangle, vertices)) {
                    continue;
                }
                writeTriangle(consumer, pose, positions, normals, uvs, indices, triangle, light);
            }
            // The run is finished, so the buffer can be closed before the next kind of triangle
            // is written.
            buffers.endBatch(type);
        }
    }

    /**
     * Writes the vertices of one triangle of a mesh into a buffer.
     *
     * <p>The kinds of triangles of the game of blocks are drawn in quads: they take four vertices
     * for every triangle of the mesh and cut them into two triangles, the first one out of the
     * vertices one, two and three, the second one out of the vertices three, four and one. The
     * mesh of the game is a list of triangles, so the first vertex of the triangle is written a
     * second time to close the quad. The second triangle of the quad then has the first vertex
     * of the triangle twice and is flat, and it is not seen.
     *
     * @param consumer the buffer the vertices are written into
     * @param pose the place of the mesh
     * @param positions the places of the vertices
     * @param normals the normals of the vertices
     * @param uvs the texture coordinates of the vertices
     * @param indices the indices of the vertices of the triangles
     * @param triangle the number of the triangle
     */
    static void writeTriangle(VertexConsumer consumer, PoseStack pose, float[] positions,
                               float[] normals, float[] uvs, int[] indices, int triangle) {
        writeTriangle(consumer, pose, positions, normals, uvs, indices, triangle, FULL_LIGHT);
    }

    /**
     * Writes the vertices of one triangle of a mesh into a buffer, with a light of its own.
     *
     * @param consumer the buffer the vertices are written into
     * @param pose the place of the mesh
     * @param positions the places of the vertices
     * @param normals the normals of the vertices
     * @param uvs the texture coordinates of the vertices
     * @param indices the indices of the vertices of the triangles
     * @param triangle the number of the triangle
     * @param light the light the triangle is drawn with
     */
    static void writeTriangle(VertexConsumer consumer, PoseStack pose, float[] positions,
                               float[] normals, float[] uvs, int[] indices, int triangle,
                               int light) {
        for (int corner = 0; corner < 4; corner++) {
            int source = corner % 3;
            int vertex = indices[triangle * 3 + source];
            consumer.addVertex(pose.last(), positions[vertex * 3], positions[vertex * 3 + 1],
                    positions[vertex * 3 + 2])
                    .setColor(255, 255, 255, 255)
                    .setUv(uvs[vertex * 2], uvs[vertex * 2 + 1])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose.last(), normals[vertex * 3], normals[vertex * 3 + 1],
                            normals[vertex * 3 + 2]);
        }
    }

    /**
     * Whether a triangle can be drawn.
     *
     * @param indices the indices of the vertices of the triangles
     * @param triangle the number of the triangle
     * @param vertices the number of the vertices of the mesh
     * @return {@code true} when every vertex of the triangle is a vertex of the mesh
     */
    private static boolean writable(int[] indices, int triangle, int vertices) {
        for (int corner = 0; corner < 3; corner++) {
            int at = triangle * 3 + corner;
            if (at >= indices.length) {
                return false;
            }
            int vertex = indices[at];
            if (vertex < 0 || vertex >= vertices) {
                return false;
            }
        }
        return true;
    }

    /**
     * The kind of triangle of a texture.
     *
     * <p>The game does not throw away the back faces of its geometry: the triangles of the
     * classes come out of the strips of the packets in the order of the strips, and their facing
     * is not consistent. The renderer therefore draws the triangles on both sides, the way the
     * game draws them.
     *
     * @param texture the place of the texture, or {@code null} when the material has none
     * @return the kind of the triangle
     */
    private static RenderType typeOf(ResourceLocation texture) {
        if (texture == null) {
            return MISSING;
        }
        return TYPES.computeIfAbsent(texture,
                location -> RenderType.entityCutoutNoCull(location));
    }
}
