package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1GearServer;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A transferable rescue reward, not an equipment stack or a Creative test pack. */
public final class Rac1ClankItem extends Item {
    public record Stamp(UUID token, UUID seal) {}
    private static final Stamp REDACTED = new Stamp(new UUID(0, 0), new UUID(0, 0));
    // Durable disk codec, deliberately no identity bytes in the item stream in either direction.
    private static final StreamCodec<RegistryFriendlyByteBuf, Stamp> WIRE_ID =
            StreamCodec.of((buffer, token) -> {}, buffer -> REDACTED);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RacMcConstants.MOD_ID);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Stamp>> TOKEN =
            COMPONENTS.register("clank_reward", () -> DataComponentType.<Stamp>builder()
                    .persistent(RecordCodecBuilder.create(instance -> instance.group(
                            UUIDUtil.CODEC.fieldOf("token").forGetter(Stamp::token),
                            UUIDUtil.CODEC.fieldOf("seal").forGetter(Stamp::seal)
                    ).apply(instance, Stamp::new))).networkSynchronized(WIRE_ID).build());

    Rac1ClankItem(Properties properties) {
        super(properties);
    }

    static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    public static ItemStack reward(Stamp id) {
        ItemStack stack = new ItemStack(RacMcItems.CLANK.get());
        stack.set(TOKEN.get(), id);
        return stack;
    }

    public static Stamp rewardId(ItemStack stack) {
        if (stack.getCount() != 1 || !stack.is(RacMcItems.CLANK.get())) return null;
        Stamp id = stack.get(TOKEN.get());
        return REDACTED.equals(id) ? null : id;
    }

    /** Creative's server-side copy operations can also see real, nested item components. */
    public static boolean protectedStack(ItemStack stack) {
        return protectedStack(stack, 0);
    }

    private static boolean protectedStack(ItemStack stack, int depth) {
        if (stack.isEmpty()) return false;
        if (stack.is(RacMcItems.CLANK.get())) return true;
        var container = stack.get(DataComponents.CONTAINER);
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (container == null && bundle == null) return false;
        if (depth >= 16) return true;
        if (container != null)
            for (ItemStack child : container.nonEmptyItems())
                if (protectedStack(child, depth + 1)) return true;
        if (bundle != null)
            for (ItemStack child : bundle.items())
                if (protectedStack(child, depth + 1)) return true;
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);
        return player instanceof ServerPlayer serverPlayer && Rac1GearServer.useClank(serverPlayer, hand)
                ? InteractionResultHolder.consume(player.getItemInHand(hand)) : InteractionResultHolder.fail(held);
    }
}
