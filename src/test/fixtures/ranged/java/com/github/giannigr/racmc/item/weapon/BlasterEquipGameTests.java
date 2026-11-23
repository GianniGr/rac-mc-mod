package com.github.giannigr.racmc.item.weapon;

import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.sound.Rac1AudioEvent;
import com.github.giannigr.racmc.sound.Rac1AudioSignal;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("racmc_ranged")
@PrefixGameTestTemplate(false)
public final class BlasterEquipGameTests {
    private BlasterEquipGameTests() {}

    @GameTest(template = "empty", batch = "ranged_equip_audio")
    public static void blasterDrawFollowsAcceptedMainHandIdentity(GameTestHelper h) {
        var level = h.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "blaster-equip-probe");
        var heard = new ArrayList<Rac1AudioEvent>();
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof Rac1AudioSignal signal) heard.add(signal.event());
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().selected = 0;
        var blaster = RacMcItems.BLASTER.get();
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(blaster));
            observe(player);
            observe(player);
            h.assertTrue(heard.isEmpty(), "Offhand-only Blaster must be silent");
            player.getInventory().setItem(1, new ItemStack(blaster));
            player.getInventory().setItem(2, new ItemStack(blaster));
            observe(player);
            h.assertTrue(heard.isEmpty(), "Acquisition in an unselected slot is not a draw");

            player.getInventory().selected = 1;
            observe(player);
            h.assertTrue(heard.equals(List.of(Rac1AudioEvent.BLASTER_EQUIP_START)), "Missing draw start");
            player.getMainHandItem().set(DataComponents.CUSTOM_NAME, Component.literal("renamed"));
            observe(player);
            h.assertTrue(heard.size() == 1, "Component mutation restarted the draw");
            player.getInventory().selected = 2;
            observe(player);
            h.assertTrue(heard.equals(List.of(Rac1AudioEvent.BLASTER_EQUIP_START,
                    Rac1AudioEvent.BLASTER_EQUIP_FINISH)), "Same main-hand class should finish the original draw");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blaster));
            observe(player);
            h.assertTrue(heard.size() == 2, "Replacing an already held Blaster must not redraw");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            observe(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blaster));
            observe(player);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            observe(player);
            observe(player);
            h.assertTrue(heard.size() == 3 && heard.getLast() == Rac1AudioEvent.BLASTER_EQUIP_START,
                    "Reconnect baseline must not emit or resume an old draw");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    private static void observe(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }
}
