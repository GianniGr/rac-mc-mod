package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The renderer of the meshes that were converted out of the disc of the game.
 *
 * <p>The geometry of the game is an arbitrary triangle mesh, so it cannot be drawn with the model
 * of a block or of a character. The renderer therefore draws the triangles of the mesh itself, with
 * the texture of the material of every triangle.
 */
public final class Rac1MeshRenderer {

    /** The light that is used when the mesh is drawn. */
    public static final int FULL_LIGHT = 0xF000F0;

    private Rac1MeshRenderer() {
        throw new UnsupportedOperationException("Utility class");
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
                mesh.materials(), textures);
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
                textures);
    }

    private static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, float[] positions,
                               float[] normals, float[] uvs, int[] indices, int[] materials,
                               ResourceLocation[] textures) {
        Map<Integer, VertexConsumer> consumers = new HashMap<>();
        Map<Integer, RenderType> types = new HashMap<>();
        for (int triangle = 0; triangle < indices.length / 3; triangle++) {
            int material = materials[triangle];
            VertexConsumer consumer = consumers.get(material);
            if (consumer == null) {
                ResourceLocation texture = material < textures.length ? textures[material] : null;
                RenderType type = texture == null ? RenderType.ENTITY_CUTOUT.apply(
                        ResourceLocation.withDefaultNamespace("missing")) : RenderType.ENTITY_CUTOUT
                        .apply(texture);
                consumer = buffers.getBuffer(type);
                consumers.put(material, consumer);
                types.put(material, type);
            }
            for (int corner = 0; corner < 3; corner++) {
                int vertex = indices[triangle * 3 + corner];
                consumer.addVertex(pose.last(), positions[vertex * 3], positions[vertex * 3 + 1],
                                positions[vertex * 3 + 2])
                        .setColor(255, 255, 255, 255)
                        .setUv(uvs[vertex * 2], uvs[vertex * 2 + 1])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_LIGHT)
                        .setNormal(pose.last(), normals[vertex * 3], normals[vertex * 3 + 1],
                                normals[vertex * 3 + 2]);
            }
        }
        for (RenderType type : types.values()) {
            buffers.endBatch(type);
        }
    }
}
