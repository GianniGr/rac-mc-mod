package com.github.giannigr.racmc.client.render;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1CrateBlock;
import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.client.RacMcClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Standard lit/colliding TerrainParticles with a local-cache material sheet, not PS2 effects. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1BlockDebris {
    private static final Rac1DebrisMaterials<ResourceLocation> MATERIALS =
            new Rac1DebrisMaterials<>(new Rac1DebrisMaterials.Upload<>() {
                public ResourceLocation register(String name, com.github.giannigr.racmc.asset.Rac1Texture texture) {
                    return Rac1ClientTextures.register(name, texture);
                }
                public void release(ResourceLocation texture) { Rac1ClientTextures.release(texture); }
            });
    // Stable keys/types across cache generations: no per-particle GPU upload or render-type growth.
    private static final Map<String, Sheet> SHEETS = new HashMap<>();
    private Rac1BlockDebris() {}

    @EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    @SuppressWarnings("removal")
    public static final class Registration {
        @SubscribeEvent
        public static void extensions(RegisterClientExtensionsEvent event) {
            var blocks = new java.util.ArrayList<net.minecraft.world.level.block.Block>();
            RacMcBlocks.crates().values().forEach(block -> blocks.add(block.get()));
            blocks.add(RacMcBlocks.VENDOR.get());
            event.registerBlock(EXTENSION, blocks.toArray(net.minecraft.world.level.block.Block[]::new));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) { refresh(); }

    public static void clear() {
        MATERIALS.clear();
        SHEETS.values().forEach(sheet -> sheet.binding = null);
    }

    private static void refresh() { MATERIALS.source(RacMcClient.assets().content().orElse(null)); }

    private static String key(BlockState state) {
        return state.getBlock() instanceof Rac1CrateBlock crate
                ? crate.definition().key() : "gadgetron_vendor";
    }

    private static final IClientBlockExtensions EXTENSION = new IClientBlockExtensions() {
        @Override public boolean addHitEffects(BlockState state, Level world, HitResult target, ParticleEngine engine) {
            refresh();
            if (!(world instanceof ClientLevel level) || !(target instanceof BlockHitResult hit)) return true;
            BlockPos pos = hit.getBlockPos();
            if (state.getShape(level, pos).isEmpty()) return true;
            var bounds = state.getShape(level, pos).bounds();
            double x = bounds.minX + level.random.nextDouble() * bounds.getXsize();
            double y = bounds.minY + level.random.nextDouble() * bounds.getYsize();
            double z = bounds.minZ + level.random.nextDouble() * bounds.getZsize();
            switch (hit.getDirection()) {
                case DOWN -> y = bounds.minY - .1;
                case UP -> y = bounds.maxY + .1;
                case NORTH -> z = bounds.minZ - .1;
                case SOUTH -> z = bounds.maxZ + .1;
                case WEST -> x = bounds.minX - .1;
                case EAST -> x = bounds.maxX + .1;
            }
            var binding = MATERIALS.of(key(state), hit.getDirection(), level.random.nextDouble());
            if (binding != null) engine.add(new Debris(level, pos, state, x, y, z, 0, 0, 0, binding)
                    .setPower(.2f).scale(.6f));
            return true; // Invisible block models skip vanilla crack(); never fall back to iron/white.
        }

        @Override public boolean addDestroyEffects(BlockState state, Level world, BlockPos pos, ParticleEngine engine) {
            refresh();
            if (!(world instanceof ClientLevel level) || !state.shouldSpawnTerrainParticles()) return true;
            state.getShape(level, pos).forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                double dx = Math.min(1, x1 - x0), dy = Math.min(1, y1 - y0), dz = Math.min(1, z1 - z0);
                int nx = Math.max(2, (int) Math.ceil(dx * 4));
                int ny = Math.max(2, (int) Math.ceil(dy * 4));
                int nz = Math.max(2, (int) Math.ceil(dz * 4));
                for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                    var binding = MATERIALS.of(key(state), null, level.random.nextDouble());
                    if (binding == null) continue;
                    double fx = (x + .5) / nx, fy = (y + .5) / ny, fz = (z + .5) / nz;
                    engine.add(new Debris(level, pos, state, x0 + fx * dx, y0 + fy * dy, z0 + fz * dz,
                            fx - .5, fy - .5, fz - .5, binding));
                }
            });
            return true;
        }
    };

    private static final class Sheet implements ParticleRenderType {
        Rac1DebrisMaterials.Binding<ResourceLocation> binding;
        @Override public com.mojang.blaze3d.vertex.BufferBuilder begin(
                com.mojang.blaze3d.vertex.Tesselator builder,
                net.minecraft.client.renderer.texture.TextureManager manager) {
            if (!MATERIALS.current(binding)) return null;
            // Same state/vertex format as vanilla TERRAIN_SHEET; only Sampler0 is different.
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, binding.texture);
            return builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
    }

    private static final class Debris extends TerrainParticle {
        private final Rac1DebrisMaterials.Binding<ResourceLocation> binding;
        private final Sheet sheet;
        Debris(ClientLevel level, BlockPos pos, BlockState state, double x, double y, double z,
               double vx, double vy, double vz, Rac1DebrisMaterials.Binding<ResourceLocation> binding) {
            super(level, pos.getX() + x, pos.getY() + y, pos.getZ() + z, vx, vy, vz, state, pos);
            this.binding = binding;
            sheet = SHEETS.computeIfAbsent(binding.key, unused -> new Sheet());
            sheet.binding = binding;
            setColor(.6f, .6f, .6f); // Vanilla terrain shade, no biome/flat-color replacement.
        }
        @Override public ParticleRenderType getRenderType() { return sheet; }
        // Standalone material texture coordinates, deliberately NOT global block-atlas UVs.
        @Override protected float getU0() { return binding.patch.u1(); }
        @Override protected float getU1() { return binding.patch.u0(); }
        @Override protected float getV0() { return binding.patch.v0(); }
        @Override protected float getV1() { return binding.patch.v1(); }
        @Override public void tick() {
            if (!MATERIALS.current(binding)) remove(); else super.tick();
        }
        @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer vertices, Camera camera, float partial) {
            if (MATERIALS.current(binding)) super.render(vertices, camera, partial);
        }
    }
}
