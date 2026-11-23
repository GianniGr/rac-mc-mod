package com.github.giannigr.racmc.worldgen;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.block.RacMcBlocks;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Cell;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Slot;
import com.github.giannigr.racmc.worldgen.StructureSupplyPlan.Space;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Bridges generated structure starts to one server-thread decision in their owning chunk. */
public final class StructureSupplies {
    private static final TagKey<Structure> ELIGIBLE = tag("structure_supplies");
    private static final TagKey<Structure> VILLAGES = tag("structure_supplies_villages");
    private static final TagKey<Structure> AQUATIC = tag("structure_supplies_aquatic");
    private static final TagKey<Structure> NEAR_SURFACE = tag("structure_supplies_near_surface");
    private static final int CHUNKS_PER_TICK = 8;

    record Job(ResourceLocation id, BlockPos anchor, boolean village, boolean aquatic, boolean nearSurface) {
        static final Codec<Job> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Job::id),
                BlockPos.CODEC.fieldOf("anchor").forGetter(Job::anchor),
                Codec.BOOL.fieldOf("village").forGetter(Job::village),
                Codec.BOOL.fieldOf("aquatic").forGetter(Job::aquatic),
                Codec.BOOL.fieldOf("near_surface").forGetter(Job::nearSurface)
        ).apply(instance, Job::new));
    }

    static final Codec<List<Job>> PENDING_CODEC = Job.CODEC.listOf();
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RacMcConstants.MOD_ID);
    private static final Supplier<AttachmentType<List<Job>>> PENDING = ATTACHMENTS.register(
            "pending_structure_supplies", () -> AttachmentType.<List<Job>>builder(() -> List.of())
                    .serialize(PENDING_CODEC).build());

    // Only used on the server thread. A queued chunk is the exact instance that fired Load.
    private static final Map<ServerLevel, LinkedHashMap<ChunkPos, LevelChunk>> QUEUED = new HashMap<>();

    private StructureSupplies() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(StructureSupplies::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(StructureSupplies::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(StructureSupplies::levelUnloaded);
        NeoForge.EVENT_BUS.addListener(StructureSupplies::tick);
    }

    /** Generation hook: metadata on the task's own protochunk only; no block or level writes. */
    public static void structureStartsGenerated(ServerLevel level, ChunkAccess chunk) {
        if (level.dimension() != Level.OVERWORLD || !chunk.getPersistedStatus().isBefore(ChunkStatus.STRUCTURE_STARTS)
                || chunk.isUpgrading() || chunk.getAllStarts().isEmpty()
                || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var jobs = new ArrayList<Job>();
        chunk.getAllStarts().forEach((type, start) -> {
            if (!start.isValid() || !start.getChunkPos().equals(chunk.getPos())) return;
            var holder = registry.wrapAsHolder(type);
            if (!holder.is(ELIGIBLE)) return;
            holder.unwrapKey().ifPresent(key -> jobs.add(new Job(key.location(),
                    start.getPieces().getFirst().getBoundingBox().getCenter(),
                    holder.is(VILLAGES), holder.is(AQUATIC), holder.is(NEAR_SURFACE))));
        });
        if (!jobs.isEmpty()) {
            jobs.sort(Comparator.comparing(Job::village).reversed().thenComparing(Job::id));
            chunk.setData(PENDING.get(), List.copyOf(jobs));
        }
    }

    private static void chunkLoaded(ChunkEvent.Load event) {
        // This event runs before FULL promotion completes. Queue, never query the level here.
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && event.getChunk() instanceof LevelChunk chunk && chunk.hasData(PENDING.get())) {
            QUEUED.computeIfAbsent(level, unused -> new LinkedHashMap<>()).put(chunk.getPos(), chunk);
        }
    }

    private static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var queued = QUEUED.get(level);
            if (queued != null) queued.remove(event.getChunk().getPos(), event.getChunk());
        }
    }

    private static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) QUEUED.remove(level);
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var queued = QUEUED.get(level);
        if (queued == null) return;
        // Do not retain an iterator across block mutations or spin on a not-yet-promoted chunk.
        int budget = Math.min(CHUNKS_PER_TICK, queued.size());
        for (int i = 0; i < budget && !queued.isEmpty(); i++) {
            var entry = queued.pollFirstEntry();
            ChunkPos pos = entry.getKey();
            LevelChunk chunk = entry.getValue();
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) {
                queued.putIfAbsent(pos, chunk);
                continue;
            }
            place(level, chunk);
        }
        if (queued.isEmpty()) QUEUED.remove(level, queued);
    }

    private record Placement(Cell pos, BlockState block) {}
    private record CrateJob(StructureSupplyPlan.Draw draw, List<Slot> candidates) {}

    private static void place(ServerLevel level, LevelChunk chunk) {
        var jobs = chunk.getExistingDataOrNull(PENDING.get());
        if (jobs == null) return;
        var terrain = new LocalTerrain(chunk);
        var vendors = new ArrayList<Slot>();
        var blocks = new ArrayList<Placement>();
        var crateJobs = new ArrayList<CrateJob>();
        for (Job job : jobs) {
            var draw = StructureSupplyPlan.draw(level.getSeed(), job.id.toString(), chunk.getPos().x,
                    chunk.getPos().z, job.village);
            if (draw.vendor()) {
                List<Slot> ordered = candidates(terrain, job, false);
                Slot vendor = ordered.stream().filter(slot -> StructureSupplyPlan.fits(slot, vendors))
                        .findFirst().orElse(null);
                if (vendor != null) {
                    vendors.add(vendor);
                    blocks.add(new Placement(vendor.pos(), RacMcBlocks.VENDOR.get().defaultBlockState()));
                } else if (job.village) {
                    RacMcMod.LOGGER.warn("No safe Gadgetron site for {} at start chunk {}: "
                            + "{} valid slots before reservations; searched buildable lanes in the owning chunk",
                            job.id, chunk.getPos(), ordered.size());
                }
            }
            crateJobs.add(new CrateJob(draw, candidates(terrain, job, true)));
        }
        var packing = new StructureCratePacking(terrain, vendors);
        // Before spending space on extras, give every start one attempt and protect all vendors.
        for (CrateJob job : crateJobs) {
            planGroup(packing, job, job.draw.groups().getFirst(), false, true, blocks);
        }
        // Try a rolled bonus before more small groups fragment the remaining floor.
        for (CrateJob job : crateJobs) {
            if (!job.draw.bonus().isEmpty()) {
                planGroup(packing, job, job.draw.bonus(), true, false, blocks);
            }
        }
        for (int i = 1; i < StructureSupplyPlan.MAX_GROUPS; i++) {
            for (CrateJob job : crateJobs) {
                if (i < job.draw.groups().size()) {
                    planGroup(packing, job, job.draw.groups().get(i), false, false, blocks);
                }
            }
        }

        // Without waiting on another chunk or tick, consume and apply on the same chunk. The
        // attachment and placed blocks are in the same chunk save. No block-presence-based retry.
        chunk.removeData(PENDING.get());
        for (Placement placement : blocks) {
            BlockPos pos = terrain.worldPos(placement.pos);
            if (!level.setBlock(pos, placement.block, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                RacMcMod.LOGGER.warn("Could not write natural structure supply at {} in chunk {}", pos, chunk.getPos());
            }
        }
    }

    private static void planGroup(StructureCratePacking packing, CrateJob job,
            List<StructureSupplyPlan.Crate> types, boolean bonus, boolean allowSingle, List<Placement> blocks) {
        List<Slot> group = packing.addGroup(job.candidates, types.size(), bonus, allowSingle);
        for (int i = 0; i < group.size(); i++) {
            blocks.add(new Placement(group.get(i).pos(),
                    RacMcBlocks.crates().get(types.get(i).key).get().defaultBlockState()));
        }
    }

    private static List<Slot> candidates(LocalTerrain terrain, Job job, boolean crates) {
        LevelChunk chunk = terrain.chunk;
        ChunkPos owner = chunk.getPos();
        int ax = job.anchor.getX() - owner.getMinBlockX();
        int az = job.anchor.getZ() - owner.getMinBlockZ();
        int ay = job.anchor.getY();
        var ranked = new ArrayList<Map.Entry<Integer, Slot>>();
        int m = StructureSupplyPlan.SITE_MARGIN;
        for (int x = m; x < 16 - m; x++) {
            for (int z = m; z < 16 - m; z++) {
                int low = chunk.getMinBuildHeight() + 1;
                int high = chunk.getMaxBuildHeight() - 2;
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                int preferred = ay;
                if (job.aquatic || job.nearSurface || job.village) {
                    int seabed = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) + 1;
                    if (crates) {
                        // Every exposed family can use the shore or a submerged floor, not
                        // the top of the water column. Interior families use their piece height.
                        preferred = terrain.space(new Cell(x, seabed, z)) == Space.WATER ? seabed
                                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                    } else {
                        preferred = job.aquatic ? seabed : surface;
                    }
                }
                if (!job.village || crates) {
                    low = Math.max(low, preferred - 12);
                    high = Math.min(high, preferred + 12);
                }
                for (int y = low; y <= high; y++) {
                    Cell cell = new Cell(x, y, z);
                    Slot slot = StructureSupplyPlan.slot(terrain, cell, crates || job.aquatic);
                    if (slot == null) continue;
                    int rank = Math.abs(x - ax) + Math.abs(z - az) + 3 * Math.abs(y - preferred);
                    if (job.village && !crates && y < surface) rank += 1000;
                    ranked.add(Map.entry(rank, slot));
                }
            }
        }
        // Stable tie-break: the fixed x,z,y iteration order, independent of registry/load order.
        ranked.sort(Map.Entry.comparingByKey());
        return ranked.stream().map(Map.Entry::getValue).toList();
    }

    private static final class LocalTerrain implements StructureSupplyPlan.Terrain {
        private final LevelChunk chunk;

        private LocalTerrain(LevelChunk chunk) {
            this.chunk = chunk;
        }

        BlockPos worldPos(Cell pos) {
            return chunk.getPos().getBlockAt(pos.x(), pos.y(), pos.z());
        }

        private BlockState state(Cell pos) {
            if (!pos.inside(0) || pos.y() < chunk.getMinBuildHeight() || pos.y() >= chunk.getMaxBuildHeight()) {
                return Blocks.BEDROCK.defaultBlockState();
            }
            return chunk.getBlockState(worldPos(pos));
        }

        @Override
        public Space space(Cell pos) {
            var state = state(pos);
            if (state.isAir()) return Space.AIR;
            if (state.is(Blocks.WATER) && state.getFluidState().isSource()) return Space.WATER;
            return Space.BLOCKED;
        }

        @Override
        public boolean supports(Cell pos) {
            if (!pos.inside(0) || pos.y() < chunk.getMinBuildHeight() || pos.y() >= chunk.getMaxBuildHeight()) {
                return false;
            }
            var state = state(pos);
            if (state.is(BlockTags.LEAVES) || state.hasBlockEntity() || FallingBlock.isFree(state)
                    || !state.isFaceSturdy(chunk, worldPos(pos), Direction.UP)) return false;
            // A full gravel or sand face is not a stable floor if its column can fall.
            while (state.getBlock() instanceof FallingBlock && pos.y() > chunk.getMinBuildHeight()) {
                pos = pos.offset(0, -1, 0);
                state = state(pos);
                if (FallingBlock.isFree(state)) return false;
            }
            return true;
        }

        @Override
        public boolean protectedSite(Cell pos) {
            if (state(pos.offset(0, -1, 0)).is(Blocks.DIRT_PATH)) return true;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        Block nearby = state(pos.offset(dx, dy, dz)).getBlock();
                        if (nearby instanceof DoorBlock || nearby instanceof FenceGateBlock
                                || nearby instanceof TrapDoorBlock) return true;
                    }
                }
            }
            return false;
        }
    }

    private static TagKey<Structure> tag(String path) {
        return TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, path));
    }
}
