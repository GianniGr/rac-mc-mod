package com.github.giannigr.racmc.gear;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** One transient visual bit on the tracked entity. Never a gameplay authority or a save record. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1BeltWorn {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            RacMcConstants.MOD_ID, "belt_bolt_grabber_worn");
    static final AttachmentType<Boolean> BOLT_GRABBER = AttachmentType.builder(() -> false)
            .sync(ByteBufCodecs.BOOL).build();

    private Rac1BeltWorn() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, entries -> entries.register(ID, BOLT_GRABBER));
    }

    public static boolean showsBoltGrabber(Player player) {
        return Boolean.TRUE.equals(player.getExistingDataOrNull(BOLT_GRABBER));
    }

    static void refresh(ServerPlayer player) {
        if (Rac1BeltServer.isEquipped(player, Rac1BeltGadget.BOLT_GRABBER)) {
            if (!showsBoltGrabber(player)) player.setData(BOLT_GRABBER, true);
        } else {
            clear(player);
        }
    }

    private static void clear(ServerPlayer player) {
        if (player.hasData(BOLT_GRABBER)) player.removeData(BOLT_GRABBER);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        // Covers game-mode changes and exceptional cancels of death. Unchanged bits don't send.
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
