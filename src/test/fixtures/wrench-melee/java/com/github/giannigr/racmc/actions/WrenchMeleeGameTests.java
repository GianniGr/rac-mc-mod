package com.github.giannigr.racmc.actions;

import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.sound.WrenchMeleeSignal;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in real Player.attack and event/packet-path test. No negotiated clients or audio device. */
@GameTestHolder("racmc_melee")
@PrefixGameTestTemplate(false)
public final class WrenchMeleeGameTests {
    @GameTest(template = "wrench_melee_empty", timeoutTicks = 40)
    public static void confirmedHitReplacesOnlyItsOwnStrongFeedback(GameTestHelper h) {
        var level = h.getLevel();
        var origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        var capable = account(level, origin.add(1, 0, 0), true, false);
        var vanilla = account(level, origin.add(2, 0, 0), false, false);
        var far = account(level, origin.add(40, 0, 0), true, false);
        var roster = mutableRoster(level.getServer().getPlayerList());
        roster.addAll(List.of(capable.player, vanilla.player, far.player));
        try {
            for (int mode = 0; mode < 8; mode++) {
                capable.packets.clear(); vanilla.packets.clear(); far.packets.clear();
                var actor = account(level, origin, true, mode != 5);
                var owner = actor.player;
                var target = (mode == 1 ? EntityType.IRON_GOLEM : EntityType.ZOMBIE).create(level);
                h.assertTrue(target instanceof Mob, "fixture could not construct target");
                target.setNoAi(true);
                target.moveTo(origin.x, origin.y, origin.z + 1.5, 0, 0);
                var impact = target.getBoundingBox().getCenter();
                var once = new AtomicBoolean();
                Consumer<PlayLevelSoundEvent.AtPosition> nested = e -> {
                    if (mode(e) && e.getLevel() == level && once.compareAndSet(false, true))
                        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1, 1);
                };
                Consumer<LivingIncomingDamageEvent> deny = e -> {
                    if (e.getEntity() == target) e.setCanceled(true);
                };
                Consumer<PlayLevelSoundEvent.AtPosition> cancelSound = e -> {
                    if (mode(e) && e.getLevel() == level) e.setCanceled(true);
                };
                Consumer<PlayLevelSoundEvent.AtPosition> changeSound = e -> {
                    if (mode(e) && e.getLevel() == level) e.setNewVolume(.5F);
                };
                Consumer<LivingIncomingDamageEvent> reuseSource = e -> {
                    if (e.getEntity() == target && once.compareAndSet(false, true)) target.hurt(e.getSource(), 1);
                };
                if (mode == 1) NeoForge.EVENT_BUS.addListener(nested);
                if (mode == 2) NeoForge.EVENT_BUS.addListener(deny);
                if (mode == 3) owner.setSprinting(true);
                if (mode == 4) NeoForge.EVENT_BUS.addListener(cancelSound);
                if (mode == 6) NeoForge.EVENT_BUS.addListener(reuseSource);
                if (mode == 7) NeoForge.EVENT_BUS.addListener(changeSound);
                level.addFreshEntity(target);
                roster.add(owner);
                float before = target.getHealth();
                try { owner.attack(target); }
                finally {
                    NeoForge.EVENT_BUS.unregister(nested);
                    NeoForge.EVENT_BUS.unregister(deny);
                    NeoForge.EVENT_BUS.unregister(cancelSound);
                    NeoForge.EVENT_BUS.unregister(reuseSource);
                    NeoForge.EVENT_BUS.unregister(changeSound);
                    roster.remove(owner);
                    target.discard();
                }
                h.assertTrue(mode == 2 ? target.getHealth() == before : target.getHealth() < before,
                        "wrong damage outcome for " + mode);
                var offers = capable.packets.stream().filter(p -> p instanceof ClientboundCustomPayloadPacket c
                        && c.payload() instanceof WrenchMeleeSignal).map(p -> (WrenchMeleeSignal)
                                ((ClientboundCustomPayloadPacket)p).payload()).toList();
                var expected = mode == 0 || mode == 1;
                h.assertTrue(offers.size() == (expected ? 1 : 0), "offer count for mode " + mode + ": " + offers);
                h.assertTrue(actor.packets.stream().filter(p -> p instanceof ClientboundCustomPayloadPacket c
                        && c.payload() instanceof WrenchMeleeSignal).count() == (expected ? 1 : 0),
                        "owner route differs from nearby listener");
                if (expected) {
                    h.assertTrue(offers.getFirst().impact().equals(impact), "wrong target position");
                    h.assertTrue(offers.getFirst().variant() == (mode == 0 ? WrenchMeleeSignal.Variant.DEFAULT
                            : WrenchMeleeSignal.Variant.ALTERNATE), "wrong hit variant");
                }
                int nearExpected = mode == 1 || mode == 3 || mode == 5 || mode == 7 ? 1 : 0;
                // A recursively damaged target's outer call can land or reject under
                // vanilla immunity. Preserve whichever native terminal sound it picked.
                if (mode == 6) {
                    nearExpected = (int)soundCount(capable, SoundEvents.PLAYER_ATTACK_STRONG);
                    h.assertTrue(once.get() && nearExpected
                            + soundCount(capable, SoundEvents.PLAYER_ATTACK_NODAMAGE) == 1
                            && soundCount(vanilla, SoundEvents.PLAYER_ATTACK_NODAMAGE) == 1 - nearExpected,
                            "native terminal feedback was lost for reused damage source");
                }
                int vanillaExpected = nearExpected + (expected ? 1 : 0);
                h.assertTrue(soundCount(capable, SoundEvents.PLAYER_ATTACK_STRONG) == nearExpected,
                        "unexpected native strong route for capable listener " + mode);
                h.assertTrue(soundCount(vanilla, SoundEvents.PLAYER_ATTACK_STRONG) == vanillaExpected,
                        "unexpected native strong route for vanilla listener " + mode);
                h.assertTrue(far.packets.stream().noneMatch(p -> p instanceof ClientboundCustomPayloadPacket c
                        && c.payload() instanceof WrenchMeleeSignal), "far observer got original offer");
                if (mode == 3) h.assertTrue(soundCount(capable, SoundEvents.PLAYER_ATTACK_KNOCKBACK) == 1,
                        "lost vanilla sprint feedback");
                if (mode == 7) h.assertTrue(capable.packets.stream().anyMatch(p -> p instanceof ClientboundSoundPacket s
                        && s.getSound().value() == SoundEvents.PLAYER_ATTACK_STRONG && s.getVolume() == .5F),
                        "lost server sound adjustment");
            }
            h.succeed();
        } finally {
            roster.removeAll(List.of(capable.player, vanilla.player, far.player));
        }
    }

    private static long soundCount(Account a, SoundEvent event) {
        return a.packets.stream().filter(p -> p instanceof ClientboundSoundPacket s
                && s.getSound().value() == event).count();
    }

    private static boolean mode(PlayLevelSoundEvent.AtPosition e) {
        return e.getSound() != null && e.getSound().value() == SoundEvents.PLAYER_ATTACK_STRONG;
    }

    private record Account(ServerPlayer player, List<Packet<?>> packets) {}

    private static Account account(ServerLevel level, Vec3 pos, boolean capable, boolean wrench) {
        var profile = new GameProfile(UUID.randomUUID(), "melee-probe");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override public float getAttackStrengthScale(float adjust) { return 1; }
        };
        List<Packet<?>> packets = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
            @Override public boolean hasChannel(ResourceLocation id) {
                return capable && WrenchMeleeSignal.TYPE.id().equals(id);
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(wrench ? RacMcItems.OMNIWRENCH.get() : Items.IRON_SWORD));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        packets.clear();
        return new Account(player, packets);
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> mutableRoster(PlayerList list) {
        try {
            var field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            return (List<ServerPlayer>)field.get(list);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
