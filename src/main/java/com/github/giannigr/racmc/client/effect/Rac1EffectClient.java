package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.effect.*;
import com.github.giannigr.racmc.cache.effect.Rac1EffectCacheStore;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.effect.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.item.Rac1BlasterArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Optional client-local provider. No media gate changes, proprietary server bytes or sound calls.
 * Existing arrow graphics remain the fallback until a complete local GPU generation is ready. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1EffectClient {
    private record Cpu(String sha1, EffectClock.Profile profile, List<EffectGpuCatalog.CpuTexture> textures,
                       List<EffectMeshAsset> meshes, NanotechTables nanotech, Rac1BlastTables blast) {}
    private static CompletableFuture<Cpu> loading;
    private static Path selected;
    private static long generation;
    private static long requestGeneration;
    private static EffectGpuCatalog catalog;
    private static EffectPlaybackSession session;
    private static Object world;
    private static boolean installed;
    private static boolean failed;
    private static NanotechTables nanotech;
    private static String discSha1;
    private static final Map<net.minecraft.core.BlockPos, Long> health = new HashMap<>();
    private static long nanoStep = -1;
    private Rac1EffectClient() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!installed) {
            Rac1EffectNetwork.receiver(Rac1EffectClient::receive);
            installed = true;
        }
        Path path = RacMcClient.media().isReady() ? RacMcClient.media().configuredPath().orElse(null) : null;
        if (!Objects.equals(path, selected)) {
            invalidate();
            selected = path;
            failed = false;
        }
        if (minecraft.level != world) {
            health.clear();
            nanoStep = -1;
            world = minecraft.level;
            if (session != null) {
                if (minecraft.level == null) session.clear();
                else session.beginWorld(minecraft.level.dimension().location().toString(), generation);
            }
        }
        if (selected != null && catalog == null && loading == null && !failed) {
            Path source = selected;
            requestGeneration = generation;
            loading = CompletableFuture.supplyAsync(() -> load(source));
        }
        if (loading != null && loading.isDone()) {
            var completed = loading;
            loading = null;
            try {
                Cpu cpu = completed.join();
                if (requestGeneration != generation) return;
                catalog = new EffectGpuCatalog(generation, cpu.textures(), cpu.meshes());
                nanotech = cpu.nanotech();
                discSha1 = cpu.sha1();
                session = new EffectPlaybackSession(cpu.sha1(), cpu.profile(), 2048);
                if (cpu.blast() != null) session.blastTables(cpu.blast());
                if (minecraft.level != null)
                    session.beginWorld(minecraft.level.dimension().location().toString(), generation);
                RacMcMod.LOGGER.info("Optional original effects ready: level1 local catalog generation {}.", generation);
            } catch (RuntimeException failure) {
                failed = requestGeneration == generation;
                RacMcMod.LOGGER.warn("Optional original effects unavailable; existing graphics retained: {}", failure.toString());
            }
        }
    }
    private static Cpu load(Path path) {
        try (var inspection = new Rac1DiscValidator().inspect(path)) {
            if (inspection.image() == null) throw new IllegalArgumentException("Effect ISO validation failed");
            var image = inspection.image();
            boolean ntsc = image.revision() == Rac1DiscRevision.NTSC_U_V1_00;
            String sha1 = ntsc ? "aee9275384f739c9b24cd5de6f7e200292db7b01"
                    : "ef304c641c7cd426ba492707dda5974679befbc0";
            var assets = Rac1LevelAssets.load(image, 1);
            var store = new Rac1EffectCacheStore(FMLPaths.CONFIGDIR.get().resolve("racmc/effects-cache"));
            EffectBanks banks;
            try { banks = store.read(sha1, 1).orElse(null); }
            catch (java.io.IOException invalid) { banks = null; }
            if (banks == null) {
                banks = Rac1EffectBankImporter.read(assets, sha1);
                try { store.write(banks); }
                catch (java.io.IOException cacheFailure) {
                    RacMcMod.LOGGER.debug("Effects cache publication unavailable: {}", cacheFailure.toString());
                }
            }
            List<EffectMeshAsset> meshes = new ArrayList<>();
            for (int id : new int[]{112, 348, 349, 357, 358, 363, 364})
                meshes.add(Rac1EffectMeshImporter.read(assets, sha1, id));
            NanotechTables tables = null;
            try { tables = Rac1NanotechImporter.read(image); }
            catch (Exception unavailable) {
                RacMcMod.LOGGER.warn("Intact nanotech overlay unavailable; no substitute geometry: {}", unavailable.toString());
            }
            Rac1BlastTables blast = null;
            try { blast = Rac1BlastTables.read(image); }
            catch (Exception unavailable) { RacMcMod.LOGGER.warn("Original blast tables unavailable: {}", unavailable.toString()); }
            return new Cpu(sha1, ntsc ? EffectClock.Profile.NTSC : EffectClock.Profile.PAL,
                    EffectGpuCatalog.decode(banks), List.copyOf(meshes), tables, blast);
        } catch (Exception failure) { throw new CompletionException(failure); }
    }
    private static void invalidate() {
        generation++;
        if (session != null) session.clear();
        session = null;
        if (catalog != null) catalog.close();
        catalog = null;
        nanotech = null;
        health.clear();
        nanoStep = -1;
        // Do not cancel the underlying read mid-ISO; let it finish and discard before any upload.
        // Its requestGeneration prevents stale completion attaching after a disc switch.
    }
    public static void reload() {
        invalidate();
        failed = false;
    }
    private static void receive(EffectSignal signal) {
        var minecraft = Minecraft.getInstance();
        if (session == null || minecraft.level == null || minecraft.player == null) return;
        // Exact currently loaded dimension is the client whitelist. No cross-world replay queue.
        if (!minecraft.level.dimension().location().toString().equals(signal.dimension())) return;
        session.receive(signal, minecraft.level.getGameTime(),
                Rac1EffectTriggers.point(minecraft.gameRenderer.getMainCamera().getPosition()));
    }
    public static boolean ownsProjectile(Rac1BlasterArrow arrow) {
        return catalog != null && session != null && Minecraft.getInstance().level == arrow.level()
                && session.ownsProjectile(arrow.getUUID());
    }
    /** Renderer supplies visibility only, never release/pickup/healing state. */
    public static void healthVisible(net.minecraft.core.BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null && (health.containsKey(pos) || health.size() < 128))
            health.put(pos.immutable(), level.getGameTime());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || session == null || catalog == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<EffectPlaybackSession.Source> sources = new ArrayList<>();
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (sources.size() >= 512) break;
            if (entity instanceof Rac1BlasterArrow arrow && arrow.visualFlying()
                    && !com.github.giannigr.racmc.item.Rac1ArrowVisibility.hidden(arrow.tickCount, partial)) {
                sources.add(new EffectPlaybackSession.Source(arrow.getUUID(),
                        Rac1EffectTriggers.point(arrow.getPosition(partial)),
                        Rac1EffectTriggers.point(arrow.getDeltaMovement().normalize()),
                        (int) Math.min(600, arrow.tickCount * session.runtime().profile().hz() / 20)));
            }
        }
        long tick = minecraft.level.getGameTime();
        var camera = Rac1EffectTriggers.point(event.getCamera().getPosition());
        health.entrySet().removeIf(entry -> {
            boolean remove = entry.getValue() < tick - 2
                    || !(minecraft.level.getBlockState(entry.getKey()).getBlock()
                    instanceof com.github.giannigr.racmc.block.Rac1CrateBlock block)
                    || block.definition().classNumber() != 501;
            if (remove) session.runtime().stopOwner(new UUID(0, entry.getKey().asLong()));
            return remove;
        });
        double updates = (tick + (double) partial) / 20 * session.runtime().profile().hz();
        List<net.minecraft.core.BlockPos> orderedHealth = new ArrayList<>(health.keySet());
        orderedHealth.sort(Comparator.comparingDouble((net.minecraft.core.BlockPos pos) ->
                new EffectSignal.Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5).distanceSquared(camera))
                .thenComparingLong(net.minecraft.core.BlockPos::asLong));
        // Nearest sixteen intact controllers, then draw their normal overlays back-to-front.
        if (orderedHealth.size() > 16) orderedHealth.subList(16, orderedHealth.size()).clear();
        Collections.reverse(orderedHealth);
        if (nanoStep < 0) nanoStep = (long) updates;
        session.collisionContext(camera, (old, next, radius) -> {
            // Cosmetic voxel-world adaptation: conservative center sweep extended by radius.
            // This is not original PS2 world/moby sphere collision and never mutates the level.
            var a = new net.minecraft.world.phys.Vec3(old.x(), old.y(), old.z());
            var b = new net.minecraft.world.phys.Vec3(next.x(), next.y(), next.z());
            var delta = b.subtract(a).normalize().scale(radius);
            var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(a, b.add(delta),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
            var face = hit.getDirection();
            var normal = new EffectSignal.Point(face.getStepX(), face.getStepY(), face.getStepZ());
            return new Rac1ProceduralEffects.Hit(Rac1EffectTriggers.point(hit.getLocation())
                    .add(normal.x() * radius, normal.y() * radius, normal.z() * radius), normal);
        });
        var advance = session.advance((tick + (double) partial) / 20, minecraft.isPaused(), tick, sources, () -> {
            nanoStep++;
            if (nanotech == null) return;
            for (var pos : orderedHealth) {
                if (session.runtime().remaining() < 128) break; // reserve room for shots/blasts
                var base = new EffectSignal.Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                if (base.distanceSquared(camera) > 64 * 64) continue;
                int life = session.runtime().profile().ticks(base.distanceSquared(camera) > 100 ? 5 : 40);
                int fadeStep = Math.max(1, 127 / life), actualLife = (127 + fadeStep - 1) / fadeStep;
                int orb = 0;
                for (var point : Rac1NanotechVisuals.orbs(base, nanoStep, pos.asLong()))
                    session.runtime().spawn(new EffectRuntime.Spawn(new UUID(0, pos.asLong()),
                            new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.PARTICLE, 33),
                            EffectRuntime.Geometry.BILLBOARD, EffectRuntime.Program.TRAIL62, point,
                            new EffectSignal.Point(0, 0, 0), 10000, 1,
                            new EffectSignal.Point(0, 0, ((pos.asLong() ^ nanoStep ^ orb++) & 255) * Math.PI / 128),
                            0, 0x7f7f4040, actualLife, fadeStep, EffectMaterial.ADDITIVE));
            }
        });
        if (advance.discontinuity()) nanoStep = (long) updates;
        PoseStack pose = new PoseStack();
        // AFTER_PARTICLES already has the camera/frustum matrix installed in RenderSystem.
        // Vertices are camera-relative world coordinates; multiplying it here would rotate twice.
        List<EffectRuntime.Draw> draws = new ArrayList<>(session.runtime().snapshot(camera, advance.interpolation()));
        if (nanotech != null) {
            for (var pos : orderedHealth) {
                var base = new EffectSignal.Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                if (base.distanceSquared(camera) > 64 * 64) continue;
                long seed = pos.asLong();
                int orb = 0;
                for (var point : Rac1NanotechVisuals.orbs(base, updates, seed))
                    draws.add(new EffectRuntime.Draw(-1 - orb++, new EffectAssetRef(discSha1, 1,
                            EffectAssetRef.Bank.PARTICLE, 39), EffectRuntime.Geometry.BILLBOARD, point, 60000,
                            new EffectSignal.Point(0, 0, ((seed ^ (orb * 0x9e3779b9L)) & 255)
                                    * Math.PI / 128 + updates * Math.PI / 32),
                            EffectMaterial.rgba(0x7f7f4040), EffectMaterial.ADDITIVE));
            }
        }
        EffectWorldRenderer.render(pose, minecraft.renderBuffers().bufferSource(), catalog,
                draws, camera, event.getCamera().rotation());
        if (nanotech != null) {
            for (var pos : orderedHealth) {
                var base = new EffectSignal.Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                if (base.distanceSquared(camera) > 64 * 64) continue;
                for (var group : Rac1NanotechVisuals.geometry(nanotech, base, camera, updates, pos.asLong())) {
                    var type = catalog.type(new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.FX, group.fx()), group.material());
                    if (type == null) continue;
                    var buffer = minecraft.renderBuffers().bufferSource();
                    var vertex = buffer.getBuffer(type);
                    for (var value : group.vertices()) {
                        var p = value.position();
                        int rgba = value.rgba();
                        vertex.addVertex(pose.last(), (float) (p.x() - camera.x()), (float) (p.y() - camera.y()),
                                (float) (p.z() - camera.z())).setUv(value.u(), value.v())
                                .setColor(rgba >>> 24, rgba >>> 16 & 255, rgba >>> 8 & 255, rgba & 255);
                    }
                    buffer.endBatch(type);
                }
            }
        }
    }
}
