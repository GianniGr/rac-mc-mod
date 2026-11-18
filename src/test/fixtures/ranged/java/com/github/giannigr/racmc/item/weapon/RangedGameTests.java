package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.asset.Rac1Crates;
import com.github.giannigr.racmc.block.*;
import com.github.giannigr.racmc.item.*;
import com.github.giannigr.racmc.loot.Rac1BoltLedger;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

/** Isolated headless probes, never compiled into ordinary artifacts. No network-client claims. */
@GameTestHolder("racmc_ranged")
@PrefixGameTestTemplate(false)
public final class RangedGameTests {
    private RangedGameTests() {}
    private static ServerPlayer player(ServerLevel level, BlockPos pos) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ranged-probe");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RacMcItems.BLASTER.get()));
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() - 2);
        return player;
    }
    private static Rac1CrateBlockEntity crate(ServerLevel level, BlockPos pos, int classId) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var definition = Rac1Crates.definitions().stream().filter(d -> d.classNumber() == classId).findFirst().orElseThrow();
        level.setBlock(pos, RacMcBlocks.crate(definition).get().defaultBlockState(), 3);
        var crate = (Rac1CrateBlockEntity) level.getBlockEntity(pos);
        crate.initializeReward(); return crate;
    }
    private static boolean paid(ServerLevel level, UUID id) {
        return Rac1BoltLedger.get(level).save(new CompoundTag(), level.registryAccess())
                .getList("rewarded", Tag.TAG_COMPOUND).stream()
                .anyMatch(t -> ((CompoundTag) t).getUUID("id").equals(id));
    }
    private static final class Probe extends Rac1BlasterArrow {
        Probe(ServerLevel level, ServerPlayer owner) { super(RacMcEntities.BLASTER_ARROW.get(), level, owner); }
        void hit(net.minecraft.world.entity.Entity victim) { onHitEntity(new EntityHitResult(victim)); }
        boolean target(net.minecraft.world.entity.Entity victim) { return canHitEntity(victim); }
    }
    private static final class BowProbe extends Arrow {
        BowProbe(ServerLevel level) { super(EntityType.ARROW, level); }
        void hit(net.minecraft.world.entity.Entity victim) { onHitEntity(new EntityHitResult(victim)); }
    }
    @GameTest(template = "empty", batch = "ranged_ground")
    public static void ordinaryGroundedRewardsProtectionReplacementAndVendor(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 2, 4)); var player = player(level, pos);
        for (int kind : new int[]{500, 501, 511}) {
            var crate = crate(level, pos, kind); UUID id = crate.rewardId();
            var projectile = new Probe(level, player);
            h.assertTrue(RangedCrateHits.grounded(projectile, pos), "Ordinary crate not removed: " + kind);
            h.assertTrue(level.getBlockState(pos).isAir(), "Crate remained");
            h.assertTrue(kind == 501 || paid(level, id), "Shared reward path missing");
            h.assertTrue(!RangedCrateHits.grounded(projectile, pos), "Stale collision consumed replacement");
        }
        var denied = crate(level, pos, 500);
        Consumer<BlockEvent.BreakEvent> veto = e -> { if (e.getPos().equals(pos)) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            h.assertTrue(!RangedCrateHits.grounded(new Probe(level, player), pos), "Protection veto bypassed");
            h.assertTrue(!paid(level, denied.rewardId()) && level.getBlockEntity(pos) == denied, "Veto paid/removed");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        Consumer<BlockEvent.BreakEvent> replace = e -> {
            if (e.getPos().equals(pos)) { level.removeBlock(pos, false); crate(level, pos, 500); }
        };
        NeoForge.EVENT_BUS.addListener(replace);
        try {
            h.assertTrue(!RangedCrateHits.grounded(new Probe(level, player), pos), "Reentrant replacement consumed");
            h.assertTrue(!paid(level, denied.rewardId()), "Old incarnation paid on replacement");
        } finally { NeoForge.EVENT_BUS.unregister(replace); }
        level.setBlock(pos, RacMcBlocks.VENDOR.get().defaultBlockState(), 3);
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(!RangedCrateHits.grounded(new Probe(level, player), pos), "Creative vendor destroyed");
        h.assertTrue(level.getBlockState(pos).is(RacMcBlocks.VENDOR.get()), "Vendor identity lost");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "ranged_air")
    public static void airborneOrdinaryKeepsIncarnationAndPaysOnlyAtContact(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 5, 4)); var player = player(level, pos);
        var crate = crate(level, pos, 500); UUID id = crate.rewardId();
        var state = crate.getBlockState(); var data = crate.saveWithoutMetadata(level.registryAccess());
        var carrier = FallingBlockEntity.fall(level, pos, state); carrier.blockData = data;
        h.assertTrue(!paid(level, id), "Representation transfer paid");
        var projectile = new Probe(level, player);
        h.assertTrue(projectile.target(carrier), "Falling crate not a projectile collision candidate");
        h.assertTrue(RangedCrateHits.airborne(projectile, carrier), "Airborne crate rejected");
        h.assertTrue(carrier.isRemoved() && paid(level, id), "Carrier shared transaction missing");
        h.assertTrue(!RangedCrateHits.airborne(projectile, carrier), "Duplicate carrier accepted");
        h.assertTrue(carrier.blockData.getUUID("BoltRewardId").equals(id), "Placement identity changed");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "ranged_fuse")
    public static void groundedAndAirborneExplosiveIdleLongShortTerminalAndTransfer(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 5, 4)); var player = player(level, pos);
        for (int initial : new int[]{-1, 60, 5, 3, 1, -2}) {
            var crate = crate(level, pos, 505); UUID id = crate.rewardId();
            crate.fuse.restore(initial, level.getGameTime());
            h.assertTrue(RangedCrateHits.grounded(new Probe(level, player), pos), "Grounded explosive unhandled");
            int after = crate.fuse.remaining();
            h.assertTrue(initial == -1 || initial > 5 ? after >= 2 && after <= 5 : after == initial,
                    "Grounded short/terminal policy changed");
            h.assertTrue(!paid(level, id) && !crate.isRemoved(), "Contact paid/removed explosive");
            Rac1FallingExplosive.fall(level, pos, crate.getBlockState());
            var carrier = level.getEntitiesOfClass(Rac1FallingExplosive.class,
                    new AABB(pos).inflate(1)).stream().filter(e -> e.blockData != null
                    && id.equals(e.blockData.getUUID("BoltRewardId"))).findFirst().orElseThrow();
            h.assertTrue(carrier.fuse.remaining() == after && !paid(level, id), "Transfer changed fuse/paid");
            h.assertTrue(RangedCrateHits.airborne(new Probe(level, player), carrier), "Air contact rejected");
            h.assertTrue(carrier.fuse.remaining() == after && !paid(level, id), "Air short rehit rerolled/paid");
            carrier.landed(pos.below());
            h.assertTrue(carrier.fuse.remaining() == after, "Landing resurrected/rerolled short fuse");
            carrier.discard();
        }
        h.succeed();
    }
    @GameTest(template = "empty", batch = "ranged_embedding")
    public static void blasterDamageDoesNotEmbedOrEraseRealBowArrows(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 2, 4));
        var shooter = player(level, pos);
        var victim = EntityType.COW.create(level); victim.setPos(Vec3.atCenterOf(pos));
        level.addFreshEntity(victim);
        victim.setArrowCount(3);
        var projectile = new Probe(level, shooter); projectile.setDeltaMovement(2, 0, 0);
        float health = victim.getHealth();
        projectile.hit(victim);
        h.assertTrue(victim.getHealth() == health - 4, "Vanilla speed*base damage changed");
        h.assertTrue(victim.getArrowCount() == 3, "Blaster embedded a shaft or erased existing bow arrows");
        h.assertTrue(projectile.isRemoved(), "Accepted hit failed to terminate");
        victim.invulnerableTime = 0;
        var bow = new BowProbe(level); bow.setDeltaMovement(2, 0, 0); bow.hit(victim);
        h.assertTrue(victim.getArrowCount() == 4, "Real bow arrow no longer embeds normally");
        projectile.setOwner(shooter);
        h.assertTrue(projectile.pickup == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED,
                "Owner changes promoted Blaster pickup");
        var saved = projectile.saveWithoutId(new CompoundTag());
        var restored = new Probe(level, shooter);
        restored.load(saved);
        int[] terminal = {0};
        Consumer<com.github.giannigr.racmc.effect.EffectTriggeredEvent> listener = e -> {
            if (e.signal().lifecycle().equals(restored.getUUID())) terminal[0]++;
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            restored.visualEnd();
            h.assertTrue(terminal[0] == 0 && restored.pickup
                    == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED,
                    "Reload replayed terminal effects or enabled pickup");
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }
    @GameTest(template = "empty", batch = "ranged_projectile_veto")
    public static void genuineProjectileImpactVetoPrecedesCrateMutation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 3, 4)); var player = player(level, pos);
        var crate = crate(level, pos, 500); UUID id = crate.rewardId();
        var arrow = new Probe(level, player);
        arrow.setPos(Vec3.atCenterOf(pos).add(-1.5, 0, 0)); arrow.setDeltaMovement(2, 0, 0);
        level.addFreshEntity(arrow);
        int[] events = {0};
        Consumer<net.neoforged.neoforge.event.entity.ProjectileImpactEvent> veto = e -> {
            if (e.getProjectile() == arrow) { events[0]++; e.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            arrow.tick();
            h.assertTrue(events[0] == 1, "Probe must exercise the genuine collision/veto dispatcher");
            h.assertTrue(level.getBlockEntity(pos) == crate && !paid(level, id), "Projectile veto bypassed");
            h.assertTrue(!arrow.isRemoved(), "Veto invented an accepted terminal hit");
            arrow.discard();
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
    }
    @GameTest(template = "empty", batch = "ranged_payment")
    public static void realAcceptedDuplicateVetoAndObstructedShotsHaveAtomicAmmo(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 3, 4));
        var player = player(level, pos);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
        player.getInventory().items.set(1, new ItemStack(Items.ARROW, 3));
        h.assertTrue(Rac1BlasterServer.fireFirstShot(level, player), "Initial immediate shot rejected");
        h.assertTrue(player.getOffhandItem().getCount() == 1 && player.getInventory().items.get(1).getCount() == 3,
                "Offhand-first payment changed");
        h.assertTrue(!Rac1BlasterServer.fireFirstShot(level, player), "Same-tick duplicate shot admitted");
        h.assertTrue(BlasterAmmo.count(player) == 4, "Duplicate input charged ammunition");

        var denied = player(level, pos);
        denied.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> veto = e -> {
            if (e.getEntity() instanceof Rac1BlasterArrow arrow && arrow.getOwner() == denied) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            h.assertTrue(!Rac1BlasterServer.fireFirstShot(level, denied), "Spawn veto ignored");
            h.assertTrue(denied.getOffhandItem().is(Items.ARROW) && denied.getOffhandItem().getCount() == 1,
                    "Failed admission did not restore original ammo slot");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }

        var blocked = player(level, pos);
        blocked.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
        var muzzle = Rac1BlasterGrip.muzzle(RangedPlayerPose.of(blocked, 1, RangedSkin.server(blocked)));
        level.setBlock(BlockPos.containing(muzzle.x(), muzzle.y(), muzzle.z()), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!Rac1BlasterServer.fireFirstShot(level, blocked), "Obstruction allowed shot relocation");
        h.assertTrue(blocked.getOffhandItem().getCount() == 1, "Obstruction consumed ammo");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "ranged_pose")
    public static void actualPlayerPassengerFlightSwimAttackAndSkinInputsReachAuthority(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 4, 4));
        var player = player(level, pos);
        var horse = EntityType.HORSE.create(level);
        horse.setPos(player.position()); horse.yBodyRot = horse.yBodyRotO = 170;
        level.addFreshEntity(horse);
        player.yHeadRot = player.yHeadRotO = 45;
        player.yBodyRot = player.yBodyRotO = 0;
        h.assertTrue(player.startRiding(horse, true) && horse.shouldRiderSit(), "Genuine seated passenger fixture required");
        var look = RangedPlayerPose.look(player, 1);
        // Literal LivingEntityRenderer clamp:45-170=-125 -> -85; body=45+85-17=113.
        h.assertTrue(Math.abs(look.bodyYaw() - 113) < .001 && Math.abs(look.headYaw() + 68) < .001,
                "Actual vehicle/head clamp omitted");
        var passenger = RangedPlayerPose.of(player, 1, RangedSkin.server(player));
        h.assertTrue(passenger.root().steps().equals(java.util.List.of(HandRoot.Step.y(67))),
                "Passenger resolved body did not reach the actual authoritative root");
        // Genuine accepted authority, compared to a literal independent vanilla render chain.
        // This is still a local headless account, not a negotiated client or visual measurement.
        player.setGameMode(GameType.CREATIVE);
        boolean slim = RangedSkin.defaultSlim(player.getUUID());
        double armX = -(float) (Math.PI / 2) + net.minecraft.util.Mth.sin(.067f) * .05f;
        double armY = -68 * ((float) Math.PI / 180);
        double armZ = net.minecraft.util.Mth.cos(.09f) * .05f + .05f;
        var expectedMuzzle = new org.joml.Matrix4d().translate(player.getX(), player.getY(), player.getZ())
                .scale(player.getScale()).rotateY(Math.toRadians(67))
                .scale(-1, -1, 1).scale(.9375).translate(0, -1.501, 0)
                .translate((slim ? -4.5 : -5) / 16, 2.0 / 16, 0)
                .rotateZYX(armZ, armY, armX).rotateX(-Math.PI / 2).rotateY(Math.PI)
                .translate(1.0 / 16, .125, -.625).rotateXYZ(Math.PI, 0, -Math.PI / 2)
                .translate(-.5, -.5, -.5).translate(.5, .5, .5)
                .transformPosition(new org.joml.Vector3d(.2305, -.03, .6335));
        h.assertTrue(Rac1BlasterServer.fireFirstShot(level, player), "Riding authority fixture must accept one real shot");
        var spawned = level.getEntitiesOfClass(Rac1BlasterArrow.class, player.getBoundingBox().inflate(4)).stream()
                .filter(arrow -> arrow.getOwner() == player).findFirst().orElseThrow();
        h.assertTrue(spawned.position().distanceTo(new Vec3(expectedMuzzle.x, expectedMuzzle.y, expectedMuzzle.z)) < 1e-5,
                "Actual authoritative projectile did not use the independently resolved vanilla passenger hand chain");
        spawned.discard();
        player.stopRiding();
        player.yBodyRot = player.yBodyRotO = 37;
        player.yHeadRot = player.yHeadRotO = 45;
        player.setXRot(-20); player.xRotO = -20;
        player.setDeltaMovement(1, 0, 1);
        player.startFallFlying();
        var flight = RangedPlayerPose.of(player, 1, RangedSkin.server(player));
        h.assertTrue(flight.root().steps().size() == 3, "Actual fall-flight root lacks pitch and velocity yaw");
        h.assertTrue(Math.abs(flight.root().steps().get(1).degrees() + .7) < .001
                && Math.abs(flight.root().steps().get(2).degrees() - 90) < .01,
                "Fall-flight source ramp/order/velocity yaw differs");
        player.stopFallFlying();

        player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        player.doTick(); // actual LivingEntity updates its private swim transition amount
        player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        h.assertTrue(player.getSwimAmount(1) > 0, "Actual vanilla swim transition must be nonzero");
        var swim = RangedPlayerPose.of(player, 1, RangedSkin.server(player));
        h.assertTrue(swim.root().steps().stream().anyMatch(step ->
                step.kind() == HandRoot.Kind.TRANSLATE && step.translation().equals(
                        new WeaponDefinition.Vector(0, -1, .3))), "Actual swim root translation omitted");
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        player.tickCount = 17; player.attackAnim = player.oAttackAnim = .35f;
        for (var hand : net.minecraft.world.entity.HumanoidArm.values()) {
            player.setMainArm(hand);
            player.swingingArm = InteractionHand.MAIN_HAND;
            var pose = RangedPlayerPose.of(player, 1, RangedSkin.server(player));
            boolean left = hand == net.minecraft.world.entity.HumanoidArm.LEFT;
            double body = Math.sin(Math.sqrt(.35) * Math.PI * 2) * .2 * (left ? -1 : 1);
            double expectedX = (left ? 1 : -1) * Math.cos(body) * 5
                    + (RangedSkin.defaultSlim(player.getUUID()) ? (left ? -.5 : .5) : 0);
            h.assertTrue(Math.abs(pose.pivot().x() - expectedX) < .001 && Math.abs(pose.pivot().y() - 5.2) < .001,
                    "Actual attack/crouch/skin state failed to reach final authoritative hand pivot");
        }
        horse.discard();
        h.succeed();
    }
}
