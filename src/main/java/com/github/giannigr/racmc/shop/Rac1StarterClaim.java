package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Retired beta save attachment. Retain its registration so existing saves round-trip unchanged. */
public final class Rac1StarterClaim {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RacMcConstants.MOD_ID);
    static final Supplier<AttachmentType<Boolean>> CLAIMED =
            TYPES.register("bomb_glove_starter_claimed",
                    () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private Rac1StarterClaim() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }

    // No live shop code reads or writes this marker.
}
