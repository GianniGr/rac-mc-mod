package com.github.giannigr.racmc.client.bomb;

import java.util.List;

/** Arm-local skin geometry. +Y goes from shoulder toward hand, in model pixels. */
final class BombGloveCuff {
    private BombGloveCuff() {}

    // Vanilla PlayerModel pivots and bounds, not measurements of the imported glove.
    enum ArmFit {
        STANDARD_RIGHT(-3, 4, 2),
        STANDARD_LEFT(-1, 4, 2),
        SLIM_RIGHT(-2, 3, 2.5f),
        SLIM_LEFT(-1, 3, 2.5f);

        final float minX;
        final int width;
        final float pivotY;

        ArmFit(float minX, int width, float pivotY) {
            this.minX = minX;
            this.width = width;
            this.pivotY = pivotY;
        }

        float centerX() {
            return minX + width * 0.5f;
        }
    }

    record Vertex(float x, float y, float z, float u, float v) {
        Vertex atCuff(Vertex other, float cuffY) {
            float t = (cuffY - y) / (other.y - y);
            return new Vertex(lerp(x, other.x, t), cuffY, lerp(z, other.z, t),
                    lerp(u, other.u, t), lerp(v, other.v, t));
        }

        private static float lerp(float a, float b, float t) {
            return a + t * (b - a);
        }
    }

    /**
     * Clips one axis-aligned cuboid face (skin or sleeve) at the same physical cuff plane.
     * Preserves winding and the existing skin UV gradient; adds no cap at the seam.
     * Caller must use only the selected vanilla arm and sleeve, never an armor part.
     */
    static List<Vertex> upperFace(List<Vertex> face, float cuffY) {
        if (!Float.isFinite(cuffY) || cuffY <= -2 || cuffY >= 10 || face.size() != 4) {
            throw new IllegalArgumentException("Expected a cuboid quad and a cuff inside the base arm");
        }
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (Vertex vertex : face) {
            low = Math.min(low, vertex.y());
            high = Math.max(high, vertex.y());
        }
        if (high <= cuffY) return List.copyOf(face);
        if (low >= cuffY) return List.of();

        Vertex[] clipped = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            Vertex vertex = face.get(i);
            if (vertex.y() == low) {
                clipped[i] = vertex;
            } else if (vertex.y() == high) {
                Vertex prev = face.get((i + 3) % 4);
                Vertex next = face.get((i + 1) % 4);
                if ((prev.y() == low) == (next.y() == low)) {
                    throw new IllegalArgumentException("Expected a side face with two axial edges");
                }
                clipped[i] = (prev.y() == low ? prev : next).atCuff(vertex, cuffY);
            } else {
                throw new IllegalArgumentException("Expected an axis-aligned face");
            }
        }
        return List.of(clipped);
    }

    /** Keep the UV-mapped forearm strip on the wrist side of the eye rig's proximal clip. */
    static List<Vertex> distalFace(List<Vertex> face, float proximalY) {
        if (face.isEmpty()) return face;
        // Reflect Y only for clipping; reflect back before emitting so winding is unchanged.
        return upperFace(face.stream().map(BombGloveCuff::reflectY).toList(), -proximalY)
                .stream().map(BombGloveCuff::reflectY).toList();
    }

    private static Vertex reflectY(Vertex v) {
        return new Vertex(v.x, -v.y, v.z, v.u, v.v);
    }
}
