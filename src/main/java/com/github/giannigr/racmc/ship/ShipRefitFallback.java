package com.github.giannigr.racmc.ship;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Mod-authored open-top silhouette when the verified mesh or windshield cannot be prepared. */
final class ShipRefitFallback {
    record Part(Block block, AABB bounds) {}

    private ShipRefitFallback() {}

    static List<Part> parts() {
        return List.of(part(Blocks.GREEN_CONCRETE, -1.10, .30, -3.90, 1.10, .96, 4.45),
                part(Blocks.LIME_CONCRETE, -.55, .37, 4.45, .55, .87, 5.00),
                part(Blocks.GREEN_CONCRETE, -2.88, .27, -3.30, -1.10, .93, 1.40),
                part(Blocks.GREEN_CONCRETE, 1.10, .27, -3.30, 2.88, .93, 1.40),
                part(Blocks.GRAY_CONCRETE, -2.35, .31, -4.24, -1.30, 1.22, -2.25),
                part(Blocks.GRAY_CONCRETE, 1.30, .31, -4.24, 2.35, 1.22, -2.25),
                part(Blocks.LIME_CONCRETE, -1.10, .96, .35, -.88, 2.28, 3.20),
                part(Blocks.LIME_CONCRETE, .88, .96, .35, 1.10, 2.28, 3.20),
                part(Blocks.GREEN_CONCRETE, -.88, .96, -.60, .88, 1.92, 1.23));
    }

    private static Part part(Block block, double x, double y, double z, double X, double Y, double Z) {
        return new Part(block, new AABB(x, y, z, X, Y, Z));
    }
}
