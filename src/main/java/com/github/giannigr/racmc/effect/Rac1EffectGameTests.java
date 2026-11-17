package com.github.giannigr.racmc.effect;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.*;
import com.github.giannigr.racmc.item.*;
import com.github.giannigr.racmc.loot.Rac1CrateRewards;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.*;

/** Headless accepted-trigger tests. No original assets/client provider are loaded by this class. */
@GameTestHolder("racmc_effects")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = "racmc")
public final class Rac1EffectGameTests {
    private Rac1EffectGameTests() {}
    @net.neoforged.bus.api.SubscribeEvent
    public static void fixture(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        ServerLevel level = event.getServer().overworld();
        // GameTest prepares structures BEFORE BeforeBatch callbacks; prepare at server start.
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("racmc_effects", "empty"))
                .fillFromWorld(level, new BlockPos(0, level.getMaxBuildHeight() - 8, 0),
                        new Vec3i(5, 5, 5), false, Blocks.STRUCTURE_VOID);
    }
    private static Rac1CrateBlockEntity crate(GameTestHelper helper, int id) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var definition = Rac1Crates.definitions().stream().filter(value -> value.classNumber() == id).findFirst().orElseThrow();
        level.setBlock(pos, RacMcBlocks.crate(definition).get().defaultBlockState(), 3);
        var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
        crate.initializeReward();
        return crate;
    }
    @GameTest(template = "empty", batch = "effects")
    public static void genuineCrateRemovalSignalsExactlyOnce(GameTestHelper helper) {
        var crate = crate(helper, 501);
        UUID id = crate.rewardId();
        List<EffectSignal> signals = new ArrayList<>();
        Consumer<EffectTriggeredEvent> listener = event -> {
            if (event.signal().lifecycle().equals(id)) signals.add(event.signal());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var pos = crate.getBlockPos(); var state = crate.getBlockState();
            Rac1CrateRewards.destroy(helper.getLevel(), pos, state, null);
            Rac1CrateRewards.destroy(helper.getLevel(), pos, state, null);
            helper.assertTrue(signals.size() == 1, "Duplicate/missing accepted break signal");
            helper.assertTrue(signals.getFirst().semantic() == EffectSignal.Semantic.CRATE_BREAK_HEALTH,
                    "Wrong semantic break kind");
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Visual hook altered removal");
            helper.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
    }
    @GameTest(template = "empty", batch = "effects")
    public static void canceledDetonationHasNoVisualThenAcceptedBlastSignalsOnce(GameTestHelper helper) {
        var crate = crate(helper, 505);
        var level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(crate.getBlockPos());
        List<EffectSignal> signals = new ArrayList<>();
        Consumer<EffectTriggeredEvent> listener = event -> {
            var p = event.signal().anchor();
            if (p.distanceSquared(new EffectSignal.Point(center.x, center.y, center.z)) < .001)
                signals.add(event.signal());
        };
        Consumer<ExplosionEvent.Start> cancel = event -> {
            if (event.getExplosion().center().distanceToSqr(center) < .001) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            Rac1CrateBlast.detonate(level, center, crate, null);
            helper.assertTrue(signals.isEmpty() && !crate.isRemoved(), "Canceled blast emitted effects/consumed crate");
            NeoForge.EVENT_BUS.unregister(cancel);
            Rac1CrateBlast.detonate(level, center, crate, null);
            Rac1CrateBlast.detonate(level, center, crate, null);
            helper.assertTrue(signals.stream().filter(signal ->
                    signal.semantic() == EffectSignal.Semantic.CRATE_TNT_BLAST).count() == 1,
                    "Accepted detonation did not produce exactly one blast");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }
    private static final class ProbeArrow extends Rac1BlasterArrow {
        ProbeArrow(ServerLevel level) { super(RacMcEntities.BLASTER_ARROW.get(), level); }
        void block(BlockHitResult hit) { onHitBlock(hit); }
    }
    @GameTest(template = "empty", batch = "effects")
    public static void acceptedArrowBlockHitAndExpiryShareOneTerminalVisual(GameTestHelper helper) {
        var level = helper.getLevel();
        var arrow = new ProbeArrow(level);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2)));
        arrow.setPos(start); arrow.setDeltaMovement(2, 0, 0);
        float damage = (float) arrow.getBaseDamage();
        List<EffectSignal> signals = new ArrayList<>();
        Consumer<EffectTriggeredEvent> listener = event -> {
            if (event.signal().lifecycle().equals(arrow.getUUID())) signals.add(event.signal());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            arrow.block(new BlockHitResult(start.add(1, 0, 0), Direction.WEST,
                    BlockPos.containing(start.add(1, 0, 0)), false));
            arrow.visualEnd();
            helper.assertTrue(signals.size() == 1
                    && signals.getFirst().semantic() == EffectSignal.Semantic.BLASTER_IMPACT,
                    "Impact+expiry duplicated terminal visual");
            helper.assertTrue(arrow.getBaseDamage() == damage && arrow.isNoGravity(),
                    "Visual hook modified arrow damage/gravity");
            helper.assertTrue(signals.getFirst().impact().normal().x() == -1, "Block normal not preserved");
            helper.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
    }
}
