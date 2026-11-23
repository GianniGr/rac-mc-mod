package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Save alongside the player's inventory; copy on death, never on stack transfer. */
public final class Rac1StarterClaim {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RacMcConstants.MOD_ID);
    private static final Supplier<AttachmentType<Boolean>> CLAIMED =
            TYPES.register("bomb_glove_starter_claimed",
                    () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private Rac1StarterClaim() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }

    static boolean available(ServerPlayer player) {
        var stock = Rac1ShopOffer.BOMB_STARTER.stock();
        return stock.weapon().isPresent() && stock.ammo().isPresent() && !player.getExistingData(CLAIMED).orElse(false);
    }

    static boolean claim(ServerPlayer player) {
        if (!available(player)) return false;
        player.setData(CLAIMED, true);
        return true;
    }
}
