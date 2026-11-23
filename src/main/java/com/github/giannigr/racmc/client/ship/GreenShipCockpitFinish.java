package com.github.giannigr.racmc.client.ship;

import com.github.giannigr.racmc.asset.Rac1Mesh;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.client.render.Rac1ClientTextures;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Mod-authored upholstery, pedal metal, and a low-profile footwell liner. No imported pixels. */
final class GreenShipCockpitFinish {
    static final AABB FLOOR = new AABB(-.875, .985, 1.235, .875, 1.015, 3.205);
    static final Rac1Mesh FLOOR_MESH = box(FLOOR, 2);
    static final Rac1Mesh CUSHION_MESH = box(GreenShipCockpitInterior.CUSHION, 0);
    static final Rac1Mesh PEDAL_MESH = box(GreenShipCockpitInterior.PEDAL, 1);

    // Starts on the first actual draw, not when the server loads shared geometry.
    private static ResourceLocation[] texture;

    private GreenShipCockpitFinish() {}

    static void draw(Rac1Mesh mesh, PoseStack pose, MultiBufferSource buffers, int light) {
        if (texture == null) {
            texture = new ResourceLocation[] {Rac1ClientTextures.registerClamped("green_ship_cockpit_finish", atlas())};
        }
        Rac1MeshRenderer.render(pose, buffers, mesh, texture, light, false);
    }

    static Rac1Texture atlas() {
        var pixels = new byte[32 * 32 * 4];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int tile = (x >> 4) | ((y >> 4) << 1);
                int u = x & 15, v = y & 15;
                int rgb = switch (tile) {
                    case 0 -> (v == 2 || v == 13) ? 0x747c48 : 0x535c39; // Taut olive upholstery and seams.
                    case 1 -> (v % 4 == 1 && u > 2 && u < 13) ? 0x495054 : 0x34393b; // Pedal tread.
                    case 2 -> (u == 2 || u == 13 || v == 2 || v == 13) ? 0x5a6245 : 0x3d4735; // Liner rim.
                    default -> 0x333a2c; // Recessed trim.
                };
                int at = (y * 32 + x) * 4;
                pixels[at] = (byte) 255;
                pixels[at + 1] = (byte) (rgb >> 16);
                pixels[at + 2] = (byte) (rgb >> 8);
                pixels[at + 3] = (byte) rgb;
            }
        }
        return new Rac1Texture(32, 32, pixels);
    }

    /** A normal lit mesh in the unchanged contact coordinates, with inset UVs. */
    private static Rac1Mesh box(AABB box, int finish) {
        int[][] faces = {{0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1},
                {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        int[][] normals = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1},
                {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        float[] pos = new float[72], norm = new float[72], uv = new float[48];
        int[] index = new int[36];
        for (int face = 0; face < 6; face++) {
            int tile = face == 1 || finish != 2 ? finish : 3;
            for (int i = 0; i < 4; i++) {
                int corner = faces[face][i], vertex = face * 4 + i;
                pos[vertex * 3] = (float) ((corner & 1) == 0 ? box.minX : box.maxX);
                pos[vertex * 3 + 1] = (float) ((corner & 2) == 0 ? box.minY : box.maxY);
                pos[vertex * 3 + 2] = (float) ((corner & 4) == 0 ? box.minZ : box.maxZ);
                for (int a = 0; a < 3; a++) norm[vertex * 3 + a] = normals[face][a];
                uv[vertex * 2] = ((tile & 1) * 16 + (i == 0 || i == 3 ? 1 : 15)) / 32f;
                uv[vertex * 2 + 1] = ((tile >> 1) * 16 + (i < 2 ? 1 : 15)) / 32f;
            }
            int base = face * 4;
            int[] split = {0, 1, 2, 0, 2, 3};
            for (int i = 0; i < split.length; i++) index[face * 6 + i] = base + split[i];
        }
        return new Rac1Mesh(pos, norm, uv, index, new int[12]);
    }
}
