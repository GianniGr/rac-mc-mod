package com.github.giannigr.racmc.client.effect;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.BombGloveArt;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.effect.*;
import com.github.giannigr.racmc.cache.effect.Rac1EffectCacheStore;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.bomb.BombGloveFlightRenderer;
import com.github.giannigr.racmc.client.bomb.BombGloveItemDrawing;
import com.github.giannigr.racmc.client.bomb.BombGloveWornDrawing;
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
 * No vanilla-arrow fallback; the existing imported drawing path owns available projectile graphics. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1EffectClient {
    private record Cpu(String sha1, EffectClock.Profile profile, List<EffectGpuCatalog.CpuTexture> textures,
                       List<EffectMeshAsset> meshes, NanotechTables nanotech, Rac1BlastTables blast,
                       EffectMeshAsset ammoPickup, Rac1AnimatedAsset bombGlove,
                       EffectMeshAsset bomb, EffectMeshAsset bombAmmo, ParticleFrameLists definitions,
                       int shipExhaustTexture) {}
    /** Read-only local presentation capability; generation invalidates every consumer handle. */
    public record LocalPresentation(long generation,String sha1,ParticleFrameLists definitions,EffectClock.Profile profile) {
        public EffectAssetRef particle(int type) {
            if(type<0||type>=definitions.count())return null;
            byte[] bytes=definitions.bytes();int at=16+type*4;
            if((bytes[at]|bytes[at+1]|bytes[at+2]|bytes[at+3])==0)return null;
            byte[] span=definitions.storedSpan(type);
            return span.length==0?null:new EffectAssetRef(sha1,1,EffectAssetRef.Bank.PARTICLE,span[0]&255);
        }
        public EffectAssetRef fx(int id){return new EffectAssetRef(sha1,1,EffectAssetRef.Bank.FX,id);}
    }
    private static LocalPresentation local;
    public static LocalPresentation localPresentation(){return local;}
    public static net.minecraft.client.renderer.RenderType localType(long token,EffectAssetRef ref,EffectMaterial material) {
        return catalog==null||token!=generation?null:catalog.type(ref,material);
    }
    public static void renderLocal(long token,PoseStack pose,java.util.List<EffectRuntime.Draw> draws,
            EffectSignal.Point camera,org.joml.Quaternionf orientation) {
        if(catalog!=null&&token==generation)
            EffectWorldRenderer.render(pose,Minecraft.getInstance().renderBuffers().bufferSource(),catalog,draws,camera,orientation);
    }
    private static CompletableFuture<Cpu> loading;
    private static Path selected;
    private static long generation;
    private static long requestGeneration;
    private static EffectGpuCatalog catalog;
    private static EffectPlaybackSession session;
    private static Object world;
    private static boolean installed;
    private static boolean failed;
    private static boolean projectileUnavailableReported;
    private static NanotechTables nanotech;
    private static String discSha1;
    private static EffectMeshAsset ammoPickup;
    private static Rac1AnimatedAsset bombGlove;
    private static EffectMeshAsset bomb;
    private static EffectMeshAsset bombAmmo;
    private static int shipExhaustTexture = -1;
    private static final Map<net.minecraft.core.BlockPos, Long> health = new HashMap<>();
    private static long nanoStep = -1;
    private record Nano(UUID id, EffectSignal.Point base, long seed, boolean intact,
                        EffectSignal.Point target, double age) {
        List<EffectSignal.Point> orbs(double updates) {
            return target == null ? (intact ? Rac1NanotechVisuals.orbs(base, updates, seed)
                    : Rac1NanotechVisuals.freeOrbs(base, updates, seed))
                    : Rac1NanotechVisuals.attracting(base, target, age, seed);
        }
    }
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
                ammoPickup = cpu.ammoPickup();
                bombGlove = cpu.bombGlove();
                bomb = cpu.bomb();
                bombAmmo = cpu.bombAmmo();
                local=new LocalPresentation(generation,cpu.sha1(),cpu.definitions(),cpu.profile());
                shipExhaustTexture = cpu.shipExhaustTexture();
                session = new EffectPlaybackSession(cpu.sha1(), cpu.profile(), 2048);
                session.bombFlashReady(cpu.meshes().stream()
                        .anyMatch(mesh -> mesh.source().sourceId() == BombGloveDryVisuals.FLASH_CLASS));
                if (cpu.blast() != null) session.blastTables(cpu.blast());
                if (minecraft.level != null)
                    session.beginWorld(minecraft.level.dimension().location().toString(), generation);
                RacMcMod.LOGGER.info("Optional original effects ready: level1 local catalog generation {}.", generation);
            } catch (RuntimeException failure) {
                failed = requestGeneration == generation;
                RacMcMod.LOGGER.warn("Optional original effects unavailable; Blaster projectile geometry is hidden, no vanilla arrow fallback: {}", failure.toString());
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
            var bombFlash = optionalBombArt(BombGloveDryVisuals.FLASH_CLASS, () -> verifiedBombFlash(assets, sha1));
            if (bombFlash != null) meshes.add(bombFlash);
            NanotechTables tables = null;
            try { tables = Rac1NanotechImporter.read(image); }
            catch (Exception unavailable) {
                RacMcMod.LOGGER.warn("Intact nanotech overlay unavailable; no substitute geometry: {}", unavailable.toString());
            }
            Rac1BlastTables blast = null;
            try { blast = Rac1BlastTables.read(image); }
            catch (Exception unavailable) { RacMcMod.LOGGER.warn("Original blast tables unavailable: {}", unavailable.toString()); }
            // Read the verified rigid pickup from the core already in memory. Failure must not
            // disable other effects or force a cache-schema change.
            EffectMeshAsset pickup = null;
            try { pickup = com.github.giannigr.racmc.asset.BlasterAmmoPickupArt.read(assets, sha1); }
            catch (Exception unavailable) {
                RacMcMod.LOGGER.warn("Original Blaster Ammo pickup art unavailable: {}", unavailable.toString());
            }
            var definitions = banks.definitions();
            byte[] exhaustFrames = definitions.count() > 23 ? definitions.storedSpan(23) : new byte[0];
            int exhaust = exhaustFrames.length > 0 ? Byte.toUnsignedInt(exhaustFrames[0]) : -1;
            return new Cpu(sha1, ntsc ? EffectClock.Profile.NTSC : EffectClock.Profile.PAL,
                    EffectGpuCatalog.decode(banks), List.copyOf(meshes), tables, blast, pickup,
                    optionalBombArt(192, () -> BombGloveArt.glove(assets)),
                    optionalBombArt(121, () -> BombGloveArt.bomb(assets, sha1)),
                    optionalBombArt(226, () -> BombGloveArt.ammo(assets, sha1)), definitions, exhaust);
        } catch (Exception failure) { throw new CompletionException(failure); }
    }
    private static EffectMeshAsset verifiedBombFlash(Rac1LevelAssets assets, String sha1)
            throws com.github.giannigr.racmc.asset.AssetFormatException {
        var row = assets.coreIndex().mobyClass(BombGloveDryVisuals.FLASH_CLASS);
        if (assets.level() != 1 || row == null || row.textureCount() != 1
                || (row.textureSlots()[0] & 255) != 211) {
            throw new com.github.giannigr.racmc.asset.AssetFormatException("Unverified Novalis class-1192 texture binding");
        }
        var flash = Rac1EffectMeshImporter.read(assets, sha1, BombGloveDryVisuals.FLASH_CLASS);
        if (flash.mesh().triangleCount() != 320 || flash.textures().size() != 1
                || Arrays.stream(flash.mesh().materials()).anyMatch(slot -> slot != 0)) {
            throw new com.github.giannigr.racmc.asset.AssetFormatException("Unverified Novalis class-1192 material or mesh");
        }
        byte[] argb = flash.textures().getFirst().pixels();
        for (int i = 0; i < argb.length; i += 4) {
            if ((argb[i] & 255) != 255) {
                throw new com.github.giannigr.racmc.asset.AssetFormatException("Unexpected class-1192 texture alpha");
            }
        }
        return flash;
    }
    private static <T> T optionalBombArt(int id, Callable<T> load) {
        try {
            return load.call();
        } catch (Exception unavailable) {
            RacMcMod.LOGGER.warn("Original Bomb Glove class {} unavailable: {}", id, unavailable.toString());
            return null;
        }
    }
    private static void invalidate() {
        generation++;
        local=null;
        if (session != null) session.clear();
        session = null;
        if (catalog != null) catalog.close();
        catalog = null;
        ammoPickup = null;
        bombGlove = null;
        bomb = null;
        bombAmmo = null;
        BombGloveItemDrawing.clearTextures();
        BombGloveFlightRenderer.clearTextures();
        BombGloveWornDrawing.clearTextures();
        shipExhaustTexture = -1;
        nanotech = null;
        health.clear();
        nanoStep = -1;
        projectileUnavailableReported = false;
        // Do not cancel the underlying read mid-ISO; let it finish and discard before any upload.
        // Its requestGeneration prevents stale completion attaching after a disc switch.
    }
    public static void reload() {
        invalidate();
        failed = false;
    }

    /** Client-local CPU mesh from the current verified media generation, or null. */
    public static EffectMeshAsset ammoPickup() {
        return currentMedia() ? ammoPickup : null;
    }

    public static Rac1AnimatedAsset bombGlove() { return currentMedia() ? bombGlove : null; }
    public static EffectMeshAsset bomb() { return currentMedia() ? bomb : null; }
    public static EffectMeshAsset bombAmmo() { return currentMedia() ? bombAmmo : null; }

    private static boolean currentMedia() {
        var media = RacMcClient.media();
        return media.isReady() && Objects.equals(selected, media.configuredPath().orElse(null));
    }

    public enum ShipArt { FLAME, GLASS, EXHAUST }

    /** Borrow ready level-01 materials only. This neither initiates I/O nor owns a texture. */
    public static net.minecraft.client.renderer.RenderType shipMaterial(ShipArt art) {
        var media = RacMcClient.media();
        if (catalog == null || discSha1 == null || !media.isReady() || media.isChecking()
                || selected == null || media.configuredPath().filter(selected::equals).isEmpty()) {
            return null;
        }
        var revision = media.attemptResult().map(IsoValidationResult::revision).orElse(null);
        boolean sameDisc = Rac1DiscCatalog.supported().profiles().stream()
                .anyMatch(profile -> profile.revision() == revision && profile.imageSha1().equals(discSha1));
        if (!sameDisc) {
            return null;
        }
        return switch (art) {
            case FLAME -> catalog.type(new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.FX, 5),
                    EffectMaterial.ADDITIVE);
            case GLASS -> catalog.type(new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.FX, 21),
                    EffectMaterial.NORMAL);
            case EXHAUST -> shipExhaustTexture < 0 ? null : catalog.type(
                    new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.PARTICLE, shipExhaustTexture),
                    EffectMaterial.NORMAL);
        };
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
    public static void prepareProjectile(Rac1BlasterArrow arrow, float partial) {
        if (!arrow.visualFlying()) return;
        boolean ready = catalog != null && session != null && Minecraft.getInstance().level == arrow.level();
        boolean admitted = ready && session.admitProjectile(source(arrow, partial));
        if (!admitted && (!ready || !session.ownsProjectile(arrow.getUUID()))
                && !projectileUnavailableReported) {
            projectileUnavailableReported = true;
            RacMcMod.LOGGER.warn("Blaster imported projectile unavailable for this generation (media loading/missing or bounded effect capacity); no vanilla arrow fallback.");
        }
    }
    private static EffectPlaybackSession.Source source(Rac1BlasterArrow arrow, float partial) {
        return new EffectPlaybackSession.Source(arrow.getUUID(),
                Rac1EffectTriggers.point(arrow.getPosition(partial)),
                Rac1EffectTriggers.point(arrow.getDeltaMovement().normalize()),
                (int) Math.min(600, arrow.tickCount * session.runtime().profile().hz() / 20));
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
            if (entity instanceof Rac1BlasterArrow arrow && arrow.visualFlying()) {
                sources.add(source(arrow, partial));
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
        List<Nano> controllers = new ArrayList<>();
        for (var pos : orderedHealth) {
            long seed = pos.asLong();
            if (minecraft.level.getBlockEntity(pos) instanceof com.github.giannigr.racmc.block.Rac1CrateBlockEntity crate
                    && crate.rewardId() != null) seed = crate.rewardId().getLeastSignificantBits();
            controllers.add(new Nano(new UUID(0, pos.asLong()),
                    new EffectSignal.Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5),
                    seed, true, null, 0));
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (controllers.size() >= 128) break;
            if (entity.distanceToSqr(event.getCamera().getPosition()) > 64 * 64) continue;
            if (entity instanceof net.minecraft.world.entity.item.FallingBlockEntity falling
                    && falling.getBlockState().getBlock() instanceof com.github.giannigr.racmc.block.Rac1CrateBlock block
                    && block.definition().classNumber() == 501) {
                var p = falling.getPosition(partial);
                long seed = falling.getUUID().getLeastSignificantBits();
                if (falling.blockData != null && falling.blockData.hasUUID("BoltRewardId"))
                    seed = falling.blockData.getUUID("BoltRewardId").getLeastSignificantBits();
                controllers.add(new Nano(falling.getUUID(), Rac1EffectTriggers.point(p), seed, true, null, 0));
            } else if (entity instanceof com.github.giannigr.racmc.loot.Rac1NanotechEntity cluster) {
                var player = cluster.receiver().map(minecraft.level::getPlayerByUUID).orElse(null);
                var target = player == null ? null : Rac1EffectTriggers.point(player.getPosition(partial).add(0, .75, 0));
                controllers.add(new Nano(cluster.getUUID(), Rac1EffectTriggers.point(cluster.getPosition(partial)),
                        cluster.seed(), false, target, (cluster.attractionAge() + partial) * 3));
            }
        }
        controllers.sort(Comparator.comparingDouble(c -> c.base().distanceSquared(camera)));
        if (controllers.size() > 16) controllers.subList(16, controllers.size()).clear();
        Collections.reverse(controllers);
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
            for (var controller : controllers) {
                if (session.runtime().remaining() < 128) break; // reserve room for shots/blasts
                var base = controller.base();
                if (base.distanceSquared(camera) > 64 * 64) continue;
                int life = session.runtime().profile().ticks(base.distanceSquared(camera) > 100 ? 5 : 40);
                int fadeStep = Math.max(1, 127 / life), actualLife = (127 + fadeStep - 1) / fadeStep;
                int orb = 0;
                for (var point : controller.orbs(nanoStep)) {
                    if (point == null) continue;
                    session.runtime().spawn(new EffectRuntime.Spawn(controller.id(),
                            new EffectAssetRef(discSha1, 1, EffectAssetRef.Bank.PARTICLE, 33),
                            EffectRuntime.Geometry.BILLBOARD, EffectRuntime.Program.TRAIL62, point,
                            new EffectSignal.Point(0, 0, 0), 10000, 1,
                            new EffectSignal.Point(0, 0, ((controller.seed() ^ nanoStep ^ orb++) & 255) * Math.PI / 128),
                            0, 0x7f7f4040, actualLife, fadeStep, EffectMaterial.ADDITIVE));
                }
            }
        });
        if (advance.discontinuity()) nanoStep = (long) updates;
        PoseStack pose = new PoseStack();
        // AFTER_PARTICLES already has the camera/frustum matrix installed in RenderSystem.
        // Vertices are camera-relative world coordinates; multiplying it here would rotate twice.
        List<EffectRuntime.Draw> draws = new ArrayList<>(session.runtime().snapshot(camera, advance.interpolation()));
        if (nanotech != null) {
            for (var controller : controllers) {
                var base = controller.base();
                if (base.distanceSquared(camera) > 64 * 64) continue;
                long seed = controller.seed();
                int orb = 0;
                for (var point : controller.orbs(updates)) {
                    if (point == null) continue;
                    draws.add(new EffectRuntime.Draw(-1 - orb++, new EffectAssetRef(discSha1, 1,
                            EffectAssetRef.Bank.PARTICLE, 39), EffectRuntime.Geometry.BILLBOARD, point, 60000,
                            new EffectSignal.Point(0, 0, ((seed ^ (orb * 0x9e3779b9L)) & 255)
                                    * Math.PI / 128 + updates * Math.PI / 32),
                            EffectMaterial.rgba(0x7f7f4040), EffectMaterial.ADDITIVE));
                }
            }
        }
        EffectWorldRenderer.render(pose, minecraft.renderBuffers().bufferSource(), catalog,
                draws, camera, event.getCamera().rotation());
        if (nanotech != null) {
            for (var controller : controllers) {
                var base = controller.base();
                if (base.distanceSquared(camera) > 64 * 64) continue;
                var groups = Rac1NanotechVisuals.geometry(nanotech, base, camera, updates, controller.seed());
                if (!controller.intact()) groups = controller.target() == null ? groups.subList(0, 2) : List.of();
                for (var group : groups) {
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
