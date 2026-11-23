package com.github.giannigr.racmc.worldgen.mixin;

import com.github.giannigr.racmc.worldgen.StructureSupplies;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
abstract class ChunkStructureStartsMixin {
    @Inject(method = "generateStructureStarts", at = @At("RETURN"))
    private static void racmc$markNewStructureSupplies(WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        StructureSupplies.structureStartsGenerated(context.level(), chunk);
    }
}
